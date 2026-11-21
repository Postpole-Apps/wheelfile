package com.postpole.wheelfile.domain.reminder

import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class ReminderStatus { UPCOMING, DUE_SOON, OVERDUE }

/** One odometer reading (in the vehicle's distance unit) with the time it was logged. */
data class OdometerReading(val vehicleId: Long, val dateTime: Long, val odometer: Double)

/** What we know about how a vehicle is driven: the latest reading and the average daily distance. */
data class VehicleUsage(val currentOdometer: Double?, val dailyDistance: Double?) {
    companion object {
        val UNKNOWN = VehicleUsage(null, null)
    }
}

/** What a reminder looks like right now. [estimatedDueDate] is only a projection (spec §2.4). */
data class ReminderState(
    val status: ReminderStatus,
    /** Whole days until the date trigger; negative when passed. Null when the reminder has no date trigger. */
    val daysLeft: Long?,
    /** Distance until the odometer trigger, in the vehicle's unit; negative when passed. Null if unknown. */
    val distanceLeft: Double?,
    /** Projected date the odometer trigger is reached, from recent usage. Null without enough data. */
    val estimatedDueDate: Long?,
)

object ReminderEngine {
    /** Recent usage window for the daily-distance average (spec §2.4: last 90 days). */
    const val USAGE_WINDOW_DAYS = 90L

    /** Used when a reminder has no explicit odometer lead: "about 500 km left". */
    const val DEFAULT_LEAD_DISTANCE = 500.0

    const val REPEAT_NOTIFY_DAYS = 7L

    private const val DAY_MS = 86_400_000L

    /**
     * Latest reading plus the average distance per day over the last [USAGE_WINDOW_DAYS] days, from the first to the
     * last reading inside the window. Null daily distance when fewer than two readings, under a day apart, or flat.
     */
    fun usage(readings: List<OdometerReading>, now: Long): VehicleUsage {
        if (readings.isEmpty()) return VehicleUsage.UNKNOWN
        val current = readings.maxOf { it.odometer }
        val windowStart = now - USAGE_WINDOW_DAYS * DAY_MS
        val recent = readings.filter { it.dateTime in windowStart..now }.sortedBy { it.dateTime }
        if (recent.size < 2) return VehicleUsage(current, null)
        val first = recent.first()
        val last = recent.last()
        val days = (last.dateTime - first.dateTime) / DAY_MS.toDouble()
        val distance = recent.maxOf { it.odometer } - recent.minOf { it.odometer }
        val daily = if (days >= 1.0 && distance > 0.0) distance / days else null
        return VehicleUsage(current, daily)
    }

    /** Projected epoch millis at which [distanceLeft] runs out, or null when it can't be estimated. */
    fun estimateDate(distanceLeft: Double, daily: Double?, now: Long): Long? {
        if (daily == null || daily <= 0.0 || distanceLeft <= 0.0) return null
        return now + ceil(distanceLeft / daily).toLong() * DAY_MS
    }

    fun evaluate(reminder: Reminder, usage: VehicleUsage, now: Long, zone: ZoneId = ZoneId.systemDefault()): ReminderState {
        val usesDate = reminder.kind != ReminderKind.ODOMETER && reminder.dueDate != null
        val usesOdometer = reminder.kind != ReminderKind.DATE && reminder.dueOdometer != null

        val daysLeft = if (usesDate) {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val due = Instant.ofEpochMilli(reminder.dueDate!!).atZone(zone).toLocalDate()
            ChronoUnit.DAYS.between(today, due)
        } else {
            null
        }
        val distanceLeft = if (usesOdometer && usage.currentOdometer != null) reminder.dueOdometer!! - usage.currentOdometer else null

        val dateStatus = daysLeft?.let {
            when {
                it < 0 -> ReminderStatus.OVERDUE
                it <= reminder.leadDays -> ReminderStatus.DUE_SOON
                else -> ReminderStatus.UPCOMING
            }
        } ?: ReminderStatus.UPCOMING
        val odometerStatus = distanceLeft?.let {
            when {
                it <= 0.0 -> ReminderStatus.OVERDUE
                it <= (reminder.leadDistance ?: DEFAULT_LEAD_DISTANCE) -> ReminderStatus.DUE_SOON
                else -> ReminderStatus.UPCOMING
            }
        } ?: ReminderStatus.UPCOMING

        // Whichever-first: the more urgent trigger decides.
        val status = maxOf(dateStatus, odometerStatus)
        return ReminderState(
            status = status,
            daysLeft = daysLeft,
            distanceLeft = distanceLeft,
            estimatedDueDate = distanceLeft?.let { estimateDate(it, usage.dailyDistance, now) },
        )
    }

    /** Notify once when a reminder becomes due, then at most weekly while it stays overdue. */
    fun shouldNotify(reminder: Reminder, state: ReminderState, now: Long): Boolean {
        if (reminder.done || state.status == ReminderStatus.UPCOMING) return false
        val last = reminder.lastNotifiedAt ?: return true
        return state.status == ReminderStatus.OVERDUE && now - last >= REPEAT_NOTIFY_DAYS * DAY_MS
    }

    /**
     * The next occurrence of a repeating reminder completed at [now], or null when it doesn't repeat. Intervals count
     * from completion (a service done late pushes the next one out).
     */
    fun nextOccurrence(reminder: Reminder, usage: VehicleUsage, now: Long, zone: ZoneId = ZoneId.systemDefault()): Reminder? {
        val months = reminder.repeatMonths?.takeIf { it > 0 && reminder.kind != ReminderKind.ODOMETER }
        val distance = reminder.repeatDistance?.takeIf { it > 0.0 && reminder.kind != ReminderKind.DATE }
        if (months == null && distance == null) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return reminder.copy(
            id = 0,
            dueDate = months?.let { today.plusMonths(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() },
            dueOdometer = distance?.let { (usage.currentOdometer ?: reminder.dueOdometer ?: 0.0) + it },
            kind = when {
                months != null && distance != null -> ReminderKind.FIRST_OF
                months != null -> ReminderKind.DATE
                else -> ReminderKind.ODOMETER
            },
            repeatMonths = months,
            repeatDistance = distance,
            done = false,
            lastNotifiedAt = null,
        )
    }

    /** Start of local day for [date], as epoch millis. */
    fun dayStart(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** Quiet hours from Settings: no notifications between [startHour] and [endHour] (local), which may wrap midnight. */
data class QuietHours(val enabled: Boolean = true, val startHour: Int = 21, val endHour: Int = 8) {
    fun contains(now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!enabled || startHour == endHour) return false
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour
        return if (startHour < endHour) hour in startHour until endHour else hour >= startHour || hour < endHour
    }

    /** The next moment quiet hours end, at or after [now]. */
    fun endAfter(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        var end = local.toLocalDate().atTime(endHour, 0).atZone(zone)
        if (!end.isAfter(local)) end = end.plusDays(1)
        return end.toInstant().toEpochMilli()
    }
}
