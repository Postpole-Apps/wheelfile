package com.postpole.wheelfile.domain.report

import com.postpole.wheelfile.data.db.EconomyUnit
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

/** Range chips on the Reports tab. Everything except the 12-month window is Pro (docs/spec.md §2.8). */
enum class ReportRange(val isPro: Boolean) {
    LAST_12_MONTHS(false),
    ALL_TIME(true),
    COMPARE(true),
    CUSTOM(true),
}

/** Inclusive bounds in epoch millis. A null bound is open. */
data class ReportWindow(val startMs: Long?, val endMs: Long?) {
    fun contains(time: Long): Boolean = (startMs == null || time >= startMs) && (endMs == null || time <= endMs)

    companion object {
        val ALL = ReportWindow(null, null)
    }
}

/** One vehicle's rows, the input of every aggregation. Money stays in the vehicle's own currency. */
data class ReportSource(
    val vehicle: Vehicle,
    val logs: List<EnergyLog>,
    val services: List<ServiceRecord>,
    val expenses: List<Expense>,
)

/** Money spent in one calendar month, in minor units of one currency. */
data class MonthlyCost(val month: YearMonth, val fuelMinor: Long, val serviceMinor: Long, val expenseMinor: Long) {
    val totalMinor: Long get() = fuelMinor + serviceMinor + expenseMinor
}

/** Monthly totals of ONE currency. Currencies are never converted or added together. */
data class CurrencyMonthly(val currency: String, val months: List<MonthlyCost>) {
    val totalMinor: Long get() = months.sumOf { it.totalMinor }
}

/** Total spent by one vehicle in the window, for the Compare range. Grouped by currency, never converted. */
data class VehicleTotal(val vehicleId: Long, val name: String, val currency: String, val totalMinor: Long)

data class CurrencyComparison(val currency: String, val vehicles: List<VehicleTotal>)

data class EconomyPoint(val dateTime: Long, val value: Double)

/** The economy of one energy stream over time, in the display unit of that stream. */
data class EconomySeries(val stream: EnergyStream, val unit: EconomyUnit, val points: List<EconomyPoint>)

/** Price per unit (litre, gallon, kWh or kg) at each purchase, in major currency units. */
data class PricePoint(val dateTime: Long, val pricePerUnit: Double)

data class PriceSeries(val stream: EnergyStream, val currency: String, val points: List<PricePoint>)

object ReportBuilder {
    /** The free window: the current calendar month and the 11 before it. */
    const val FREE_WINDOW_MONTHS = 12

    /**
     * The 12 calendar months ending with the month of [now]. Starts at 00:00 on the first day of the earliest month
     * and ends at the last millisecond of the current month, so an entry from the first day of that earliest month is
     * in and one from the day before is out.
     */
    fun last12Months(now: Long, zone: ZoneId = ZoneId.systemDefault()): ReportWindow {
        val current = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        val start = current.minusMonths((FREE_WINDOW_MONTHS - 1).toLong()).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return ReportWindow(start, endOfMonth(current, zone))
    }

    /** A custom range from the start of [from] to the end of [to] (both inclusive days). Swapped if reversed. */
    fun custom(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ReportWindow {
        val a = minOf(from, to)
        val b = maxOf(from, to)
        return ReportWindow(
            a.atStartOfDay(zone).toInstant().toEpochMilli(),
            b.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
        )
    }

    fun window(range: ReportRange, now: Long, custom: ReportWindow?, zone: ZoneId = ZoneId.systemDefault()): ReportWindow = when (range) {
        ReportRange.LAST_12_MONTHS -> last12Months(now, zone)
        ReportRange.CUSTOM -> custom ?: ReportWindow.ALL
        ReportRange.ALL_TIME, ReportRange.COMPARE -> ReportWindow.ALL
    }

    private fun endOfMonth(month: YearMonth, zone: ZoneId): Long =
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    private fun monthOf(time: Long, zone: ZoneId) = YearMonth.from(Instant.ofEpochMilli(time).atZone(zone))

    /**
     * Cost per calendar month split into fuel/energy, service and expenses, one series per currency. Every month in
     * a bounded window is present (zero when nothing was spent); an open window runs from the first to the last month
     * that has data. Currencies are kept apart: two vehicles in EUR add up, a vehicle in USD gets its own series.
     */
    fun monthlyCosts(
        sources: List<ReportSource>,
        window: ReportWindow,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CurrencyMonthly> =
        sources.groupBy { it.vehicle.currency }.map { (currency, group) ->
            val fuel = HashMap<YearMonth, Long>()
            val service = HashMap<YearMonth, Long>()
            val expense = HashMap<YearMonth, Long>()
            for (s in group) {
                s.logs.filter { window.contains(it.dateTime) }.forEach {
                    fuel.merge(monthOf(it.dateTime, zone), it.totalMinor ?: 0L, Long::plus)
                }
                s.services.filter { window.contains(it.dateTime) }.forEach {
                    service.merge(monthOf(it.dateTime, zone), it.totalMinor ?: 0L, Long::plus)
                }
                s.expenses.filter { window.contains(it.dateTime) }.forEach {
                    expense.merge(monthOf(it.dateTime, zone), it.amountMinor, Long::plus)
                }
            }
            val present = fuel.keys + service.keys + expense.keys
            val first = window.startMs?.let { monthOf(it, zone) } ?: present.minOrNull()
            val last = window.endMs?.let { monthOf(it, zone) } ?: present.maxOrNull()
            val months = if (first == null || last == null) {
                emptyList()
            } else {
                generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.map {
                    MonthlyCost(it, fuel[it] ?: 0L, service[it] ?: 0L, expense[it] ?: 0L)
                }.toList()
            }
            CurrencyMonthly(currency, months)
        }.sortedBy { it.currency }

    /** Total spent per vehicle in [window], grouped by currency (the Compare range). Largest spender first. */
    fun compare(sources: List<ReportSource>, window: ReportWindow): List<CurrencyComparison> =
        sources.map { s ->
            val total = s.logs.filter { window.contains(it.dateTime) }.sumOf { it.totalMinor ?: 0L } +
                s.services.filter { window.contains(it.dateTime) }.sumOf { it.totalMinor ?: 0L } +
                s.expenses.filter { window.contains(it.dateTime) }.sumOf { it.amountMinor }
            VehicleTotal(s.vehicle.id, s.vehicle.name, s.vehicle.currency, total)
        }.groupBy { it.currency }.map { (currency, list) ->
            CurrencyComparison(currency, list.sortedByDescending { it.totalMinor })
        }.sortedBy { it.currency }

    /**
     * Economy at the end of each valid full-to-full segment, per energy stream, in the vehicle's display unit.
     * The window filters on the date of the closing fill-up.
     */
    fun economySeries(source: ReportSource, window: ReportWindow): List<EconomySeries> {
        val vehicle = source.vehicle
        return computeByStream(source.logs, vehicle).entries.sortedBy { it.key.ordinal }.mapNotNull { (stream, result) ->
            val dates = source.logs.filter { it.stream == stream }.associate { it.id to it.dateTime }
            val unit = VehicleStatsCalculator.displayUnit(vehicle, stream)
            val points = result.validSegments.mapNotNull { seg ->
                val time = dates[seg.endEntryId] ?: return@mapNotNull null
                val value = seg.per100Km?.let { EconomyDisplay.value(it, unit, 2) } ?: return@mapNotNull null
                if (window.contains(time)) EconomyPoint(time, value) else null
            }.sortedBy { it.dateTime }
            if (points.isEmpty()) null else EconomySeries(stream, unit, points)
        }
    }

    /** Price per unit at each purchase in [window], per energy stream, oldest first. */
    fun priceSeries(source: ReportSource, window: ReportWindow): List<PriceSeries> {
        val vehicle = source.vehicle
        val digits = try {
            Currency.getInstance(vehicle.currency).defaultFractionDigits.coerceAtLeast(0)
        } catch (_: IllegalArgumentException) {
            2
        }
        return source.logs.filter { window.contains(it.dateTime) }.groupBy { it.stream }.entries.sortedBy { it.key.ordinal }
            .mapNotNull { (stream, list) ->
                val points = list.mapNotNull { log ->
                    val price = when {
                        log.pricePerUnitMinor != null -> PriceMath.unitPriceFromStored(log.pricePerUnitMinor, digits).toDouble()
                        log.totalMinor != null && log.quantity > 0.0 -> PriceMath.fromMinor(log.totalMinor, digits).toDouble() / log.quantity
                        else -> null
                    }
                    price?.takeIf { it > 0.0 }?.let { PricePoint(log.dateTime, it) }
                }.sortedBy { it.dateTime }
                if (points.isEmpty()) null else PriceSeries(stream, vehicle.currency, points)
            }
    }
}
