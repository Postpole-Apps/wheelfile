package com.postpole.wheelfile.domain.history

import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.domain.economy.EconomyDisplay
import com.postpole.wheelfile.domain.economy.InvalidReason
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import com.postpole.wheelfile.domain.economy.computeByStream
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class HistoryFilter { ALL, FUEL, CHARGING, SERVICE, EXPENSES }

enum class HistoryKind { FILL_UP, CHARGE, SERVICE, EXPENSE }

data class HistoryRow(
    val kind: HistoryKind,
    /** Row id within its own table (kinds can share ids). */
    val id: Long,
    val dateTime: Long,
    /** In the vehicle's distance unit. */
    val odometer: Double?,
    val quantity: Double?,
    val totalMinor: Long?,
    val isFull: Boolean = true,
    val missedPrevious: Boolean = false,
    /** Economy of the full-to-full segment that ends at this entry, in the vehicle's display unit. */
    val economy: Double? = null,
    /** Change vs the previous valid segment of the same stream; negative is better. */
    val deltaPercent: Double? = null,
    /** The segment closing here has a non-positive distance ("check odometer"). */
    val checkOdometer: Boolean = false,
    val expenseCategory: ExpenseCategory? = null,
    val serviceItems: List<String> = emptyList(),
    val station: String? = null,
    /** Energy stream of fill-ups and charges; null for service and expenses. */
    val stream: EnergyStream? = null,
)

data class MonthGroup(val month: YearMonth, val totalMinor: Long, val rows: List<HistoryRow>)

object HistoryBuilder {

    fun matches(filter: HistoryFilter, kind: HistoryKind): Boolean = when (filter) {
        HistoryFilter.ALL -> true
        HistoryFilter.FUEL -> kind == HistoryKind.FILL_UP
        HistoryFilter.CHARGING -> kind == HistoryKind.CHARGE
        HistoryFilter.SERVICE -> kind == HistoryKind.SERVICE
        HistoryFilter.EXPENSES -> kind == HistoryKind.EXPENSE
    }

    /** Newest first, grouped by calendar month in [zone]. */
    fun build(data: VehicleData, filter: HistoryFilter, zone: ZoneId = ZoneId.systemDefault()): List<MonthGroup> {
        val vehicle = data.vehicle
        val byStream = computeByStream(data.logs, vehicle)

        // Economy by closing entry id, plus the delta against the previous valid segment of that stream.
        val economyByEntry = HashMap<Long, Triple<Double?, Double?, Boolean>>()
        byStream.forEach { (stream, result) ->
            val unit = VehicleStatsCalculator.displayUnit(vehicle, stream)
            var previous: Double? = null
            for (segment in result.segments) {
                val per100 = segment.per100Km
                val delta = if (per100 != null && previous != null && previous > 0.0) {
                    EconomyDisplay.round((per100 - previous) / previous * 100.0, 1)
                } else {
                    null
                }
                economyByEntry[segment.endEntryId] = Triple(
                    per100?.let { EconomyDisplay.value(it, unit) },
                    delta,
                    segment.invalidReason == InvalidReason.NON_POSITIVE_DISTANCE,
                )
                if (per100 != null) previous = per100
            }
        }

        val rows = buildList {
            data.logs.forEach { log ->
                val kind = if (log.stream == EnergyStream.ELECTRIC) HistoryKind.CHARGE else HistoryKind.FILL_UP
                val (economy, delta, check) = economyByEntry[log.id] ?: Triple(null, null, false)
                add(
                    HistoryRow(
                        kind = kind, id = log.id, dateTime = log.dateTime, odometer = log.odometer,
                        quantity = log.quantity, totalMinor = log.totalMinor, isFull = log.isFull,
                        missedPrevious = log.missedPrevious, economy = economy, deltaPercent = delta,
                        checkOdometer = check, station = log.station, stream = log.stream,
                    ),
                )
            }
            data.services.forEach {
                add(
                    HistoryRow(
                        kind = HistoryKind.SERVICE, id = it.id, dateTime = it.dateTime, odometer = it.odometer,
                        quantity = null, totalMinor = it.totalMinor, serviceItems = it.items,
                    ),
                )
            }
            data.expenses.forEach {
                add(
                    HistoryRow(
                        kind = HistoryKind.EXPENSE, id = it.id, dateTime = it.dateTime, odometer = it.odometer,
                        quantity = null, totalMinor = it.amountMinor, expenseCategory = it.category,
                    ),
                )
            }
        }
            .filter { matches(filter, it.kind) }
            .sortedWith(compareByDescending<HistoryRow> { it.dateTime }.thenByDescending { it.odometer ?: 0.0 }.thenByDescending { it.id })

        return rows
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.dateTime).atZone(zone)) }
            .map { (month, list) -> MonthGroup(month, list.sumOf { it.totalMinor ?: 0L }, list) }
            .sortedByDescending { it.month }
    }
}
