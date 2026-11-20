package com.postpole.wheelfile.domain.history

import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.economy.InvalidReason
import com.postpole.wheelfile.domain.economy.computeByStream
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class HistoryFilter { ALL, FILL_UPS, CHARGES, SERVICE, EXPENSES }

/** Why an energy entry is marked in History. */
enum class EntryFlag { CHECK_ODOMETER, MISSED_FILL }

sealed interface HistoryEntry {
    val key: String
    val dateTime: Long
    val totalMinor: Long?

    data class Energy(val log: EnergyLog, val flag: EntryFlag?) : HistoryEntry {
        override val key get() = "energy-${log.id}"
        override val dateTime get() = log.dateTime
        override val totalMinor get() = log.totalMinor
    }

    data class Service(val record: ServiceRecord) : HistoryEntry {
        override val key get() = "service-${record.id}"
        override val dateTime get() = record.dateTime
        override val totalMinor get() = record.totalMinor
    }

    data class ExpenseItem(val expense: Expense) : HistoryEntry {
        override val key get() = "expense-${expense.id}"
        override val dateTime get() = expense.dateTime
        override val totalMinor get() = expense.amountMinor
    }
}

data class MonthGroup(val month: YearMonth, val entries: List<HistoryEntry>) {
    val totalMinor: Long get() = entries.sumOf { it.totalMinor ?: 0L }
}

object HistoryBuilder {

    /** Month cards, newest month first, entries newest first. Entries are filtered by [filter]. */
    fun build(
        vehicle: Vehicle,
        logs: List<EnergyLog>,
        services: List<ServiceRecord>,
        expenses: List<Expense>,
        filter: HistoryFilter,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MonthGroup> {
        val flags = flagsFor(logs, vehicle)
        val entries = buildList<HistoryEntry> {
            if (filter == HistoryFilter.ALL || filter == HistoryFilter.FILL_UPS) {
                logs.filter { it.stream != EnergyStream.ELECTRIC }.mapTo(this) { HistoryEntry.Energy(it, flags[it.id]) }
            }
            if (filter == HistoryFilter.ALL || filter == HistoryFilter.CHARGES) {
                logs.filter { it.stream == EnergyStream.ELECTRIC }.mapTo(this) { HistoryEntry.Energy(it, flags[it.id]) }
            }
            if (filter == HistoryFilter.ALL || filter == HistoryFilter.SERVICE) {
                services.mapTo(this) { HistoryEntry.Service(it) }
            }
            if (filter == HistoryFilter.ALL || filter == HistoryFilter.EXPENSES) {
                expenses.mapTo(this) { HistoryEntry.ExpenseItem(it) }
            }
        }
        return entries
            .sortedByDescending { it.dateTime }
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.dateTime).atZone(zone)) }
            .map { (month, list) -> MonthGroup(month, list) }
            .sortedByDescending { it.month }
    }

    /** Spec §4.3 point 7: a non-positive distance is flagged "check odometer" on the closing entry. */
    fun flagsFor(logs: List<EnergyLog>, vehicle: Vehicle): Map<Long, EntryFlag> {
        val flags = mutableMapOf<Long, EntryFlag>()
        computeByStream(logs, vehicle).values.forEach { result ->
            result.segments.forEach { segment ->
                when (segment.invalidReason) {
                    InvalidReason.NON_POSITIVE_DISTANCE -> flags[segment.endEntryId] = EntryFlag.CHECK_ODOMETER
                    InvalidReason.MISSED_FILL -> flags.putIfAbsent(segment.endEntryId, EntryFlag.MISSED_FILL)
                    null -> Unit
                }
            }
        }
        return flags
    }
}
