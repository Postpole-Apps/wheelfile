package com.postpole.wheelfile.reminders

import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderDao
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.domain.reminder.QuietHours
import com.postpole.wheelfile.domain.reminder.ReminderEngine
import com.postpole.wheelfile.domain.reminder.ReminderState
import java.time.ZoneId

/** Posts the system notification for a due reminder. Returns false when nothing was shown (for example permission denied). */
fun interface ReminderNotifier {
    fun post(reminder: Reminder, vehicle: Vehicle, state: ReminderState): Boolean
}

data class CheckResult(
    val posted: Int,
    /** Set when quiet hours blocked the run: when to try again. */
    val deferUntil: Long? = null,
)

/**
 * The daily check: finds due reminders and notifies once (then weekly while overdue). Everything time-related comes
 * from [clock] so tests can drive a test clock. Quiet hours defer the run instead of dropping notifications.
 */
class ReminderChecker(
    private val reminders: ReminderDao,
    private val vehicles: VehicleDao,
    private val notifier: ReminderNotifier,
    private val quietHours: suspend () -> QuietHours,
    private val clock: () -> Long,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    suspend fun run(): CheckResult {
        val now = clock()
        val quiet = quietHours()
        if (quiet.contains(now, zone())) return CheckResult(posted = 0, deferUntil = quiet.endAfter(now, zone()))

        val readings = reminders.getReadings().groupBy { it.vehicleId }
        val byVehicle = vehicles.getAll().associateBy { it.id }
        var posted = 0
        for (reminder in reminders.getPending()) {
            val vehicle = byVehicle[reminder.vehicleId]?.takeIf { !it.archived } ?: continue
            val usage = ReminderEngine.usage(readings[vehicle.id].orEmpty(), now)
            val state = ReminderEngine.evaluate(reminder, usage, now, zone())
            if (!ReminderEngine.shouldNotify(reminder, state, now)) continue
            if (notifier.post(reminder, vehicle, state)) {
                reminders.update(reminder.copy(lastNotifiedAt = now))
                posted++
            }
        }
        return CheckResult(posted)
    }
}
