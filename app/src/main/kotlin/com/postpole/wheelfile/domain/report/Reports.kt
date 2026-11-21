package com.postpole.wheelfile.domain.report

import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.economy.EconomyDisplay
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import com.postpole.wheelfile.domain.economy.computeByStream
import com.postpole.wheelfile.domain.entry.PriceMath
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Currency

/** The range chips on the Reports tab. Only [LAST_12_MONTHS] is free (spec §2.8). */
enum class ReportRange(val isPro: Boolean) {
    LAST_12_MONTHS(false),
    ALL_TIME(true),
    COMPARE(true),
    CUSTOM(true),
}

/** A half-open time window: [startMillis] inclusive, [endMillis] exclusive. A null side is unbounded. */
data class ReportWindow(val startMillis: Long?, val endMillis: Long?) {
    fun contains(t: Long): Boolean = (startMillis == null || t >= startMillis) && (endMillis == null || t < endMillis)

    companion object {
        val ALL = ReportWindow(null, null)

        /** The free window: the current calendar month and the 11 before it. */
        fun last12Months(now: Long, zone: ZoneId = ZoneId.systemDefault()): ReportWindow {
            val current = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
            return ReportWindow(startOf(current.minusMonths(11), zone), startOf(current.plusMonths(1), zone))
        }

        /** [from] to [to], both inclusive days. */
        fun custom(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ReportWindow {
            val (a, b) = if (to.isBefore(from)) to to from else from to to
            return ReportWindow(
                a.atStartOfDay(zone).toInstant().toEpochMilli(),
                b.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }

        private fun startOf(month: YearMonth, zone: ZoneId) = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

/** One vehicle's rows, as the aggregation needs them. */
data class CostSource(
    val currency: String,
    val logs: List<EnergyLog>,
    val services: List<ServiceRecord>,
    val expenses: List<Expense>,
)

data class MonthlyCost(val month: YearMonth, val energyMinor: Long, val serviceMinor: Long, val expenseMinor: Long) {
    val totalMinor: Long get() = energyMinor + serviceMinor + expenseMinor
}

/** Months of one currency. Amounts in different currencies are never added together or converted. */
data class CurrencyCosts(val currency: String, val months: List<MonthlyCost>) {
    val totalMinor: Long get() = months.sumOf { it.totalMinor }
}

data class EconomyPoint(val dateTime: Long, val value: Double)

data class EconomySeries(val stream: EnergyStream, val unit: com.postpole.wheelfile.data.db.EconomyUnit, val points: List<EconomyPoint>)

data class PricePoint(val dateTime: Long, val price: Double)

/** Price per unit (litre, gallon, kg or kWh) of one stream, in major currency units. */
data class PriceSeries(val stream: EnergyStream, val points: List<PricePoint>)

object ReportBuilder {

    /**
     * Monthly cost by category, one list per currency (sorted by currency code). Months run without gaps from the first
     * to the last month with data, or across the whole [window] when it is bounded on both sides.
     */
    fun monthlyCosts(
        sources: List<CostSource>,
        window: ReportWindow,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CurrencyCosts> {
        fun month(t: Long) = YearMonth.from(Instant.ofEpochMilli(t).atZone(zone))
        return sources.groupBy { it.currency }.toSortedMap().map { (currency, group) ->
            val energy = HashMap<YearMonth, Long>()
            val service = HashMap<YearMonth, Long>()
            val expense = HashMap<YearMonth, Long>()
            for (s in group) {
                s.logs.filter { window.contains(it.dateTime) }.forEach { energy.merge(month(it.dateTime), it.totalMinor ?: 0L, Long::plus) }
                s.services.filter { window.contains(it.dateTime) }.forEach { service.merge(month(it.dateTime), it.totalMinor ?: 0L, Long::plus) }
                s.expenses.filter { window.contains(it.dateTime) }.forEach { expense.merge(month(it.dateTime), it.amountMinor, Long::plus) }
            }
            val present = energy.keys + service.keys + expense.keys
            val bounded = window.startMillis != null && window.endMillis != null
            val first = if (bounded) month(window.startMillis!!) else present.minOrNull()
            val last = if (bounded) month(window.endMillis!! - 1) else present.maxOrNull()
            val months = if (first == null || last == null) {
                emptyList()
            } else {
                generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.map { m ->
                    MonthlyCost(m, energy[m] ?: 0L, service[m] ?: 0L, expense[m] ?: 0L)
                }.toList()
            }
            CurrencyCosts(currency, months)
        }
    }

    /**
     * Economy of every valid full-to-full segment that closes inside [window], per stream, in the vehicle's display unit.
     * Segments are computed over the whole history so a window edge never cuts one in two.
     */
    fun economySeries(vehicle: Vehicle, logs: List<EnergyLog>, window: ReportWindow): List<EconomySeries> {
        val byId = logs.associateBy { it.id }
        return computeByStream(logs, vehicle).entries.sortedBy { it.key.ordinal }.mapNotNull { (stream, result) ->
            val unit = VehicleStatsCalculator.displayUnit(vehicle, stream)
            val points = result.validSegments.mapNotNull { seg ->
                val at = byId[seg.endEntryId]?.dateTime ?: return@mapNotNull null
                if (!window.contains(at)) return@mapNotNull null
                val value = seg.per100Km?.let { EconomyDisplay.value(it, unit, 2) } ?: return@mapNotNull null
                EconomyPoint(at, value)
            }.sortedBy { it.dateTime }
            if (points.isEmpty()) null else EconomySeries(stream, unit, points)
        }
    }

    /**
     * Price per unit of each fill-up or charge in [window]. The stored unit price is used when there is one, otherwise
     * total over quantity (the quantity is in the vehicle's volume unit, or kWh).
     */
    fun priceSeries(vehicle: Vehicle, logs: List<EnergyLog>, window: ReportWindow): List<PriceSeries> {
        val digits = fractionDigits(vehicle.currency)
        return logs.filter { window.contains(it.dateTime) }.groupBy { it.stream }.entries.sortedBy { it.key.ordinal }.mapNotNull { (stream, list) ->
            val points = list.sortedBy { it.dateTime }.mapNotNull { log ->
                val price = when {
                    log.pricePerUnitMinor != null && log.pricePerUnitMinor > 0 ->
                        PriceMath.unitPriceFromStored(log.pricePerUnitMinor, digits).toDouble()
                    log.totalMinor != null && log.totalMinor > 0 && log.quantity > 0 ->
                        PriceMath.fromMinor(log.totalMinor, digits).toDouble() / log.quantity
                    else -> null
                }
                price?.let { PricePoint(log.dateTime, it) }
            }
            if (points.isEmpty()) null else PriceSeries(stream, points)
        }
    }

    /** One row of the Pro comparison. Amounts are in the vehicle's own currency; nothing is converted. */
    data class Comparison(
        val vehicle: Vehicle,
        val totalMinor: Long,
        val costPerDistanceMinor: Double?,
        val averageEconomy: Double?,
        val economyUnit: com.postpole.wheelfile.data.db.EconomyUnit?,
    )

    fun compare(vehicle: Vehicle, logs: List<EnergyLog>, services: List<ServiceRecord>, expenses: List<Expense>, window: ReportWindow): Comparison {
        val stats = VehicleStatsCalculator.compute(
            vehicle,
            logs.filter { window.contains(it.dateTime) },
            services.filter { window.contains(it.dateTime) },
            expenses.filter { window.contains(it.dateTime) },
        )
        val stream = stats.streams.firstOrNull { it.average != null }
        return Comparison(vehicle, stats.costs.totalMinor, stats.costs.costPerDistanceMinor, stream?.average, stream?.unit)
    }

    fun fractionDigits(currency: String): Int =
        try {
            Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
        } catch (_: IllegalArgumentException) {
            2
        }
}
