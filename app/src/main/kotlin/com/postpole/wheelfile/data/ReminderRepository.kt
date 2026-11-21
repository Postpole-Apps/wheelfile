package com.postpole.wheelfile.data

import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderDao
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.domain.reminder.ReminderEngine
import com.postpole.wheelfile.domain.reminder.ReminderState
import com.postpole.wheelfile.domain.reminder.ReminderStatus
import com.postpole.wheelfile.domain.reminder.VehicleUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ReminderItem(val reminder: Reminder, val vehicle: Vehicle, val state: ReminderState?)

/** Reminders joined with their vehicle and the usage projection, for the Reminders list and the bell badge. */
@Singleton
class ReminderRepository @Inject constructor(
    private val reminders: ReminderDao,
    private val vehicles: VehicleDao,
) {
    /** Reminders of one vehicle, or of every vehicle when [vehicleId] is null. Open ones first, soonest first. */
    fun observeItems(vehicleId: Long?, clock: () -> Long = System::currentTimeMillis): Flow<List<ReminderItem>> =
        combine(reminders.observeAll(), vehicles.observeAll(), reminders.observeReadings()) { all, vs, readings ->
            val now = clock()
            val byVehicle = vs.associateBy { it.id }
            val usage = readings.groupBy { it.vehicleId }
            all.filter { vehicleId == null || it.vehicleId == vehicleId }.mapNotNull { r ->
                val v = byVehicle[r.vehicleId] ?: return@mapNotNull null
                val state = if (r.done) null else ReminderEngine.evaluate(r, ReminderEngine.usage(usage[v.id].orEmpty(), now), now)
                ReminderItem(r, v, state)
            }.sortedWith(
                compareBy<ReminderItem> { it.reminder.done }
                    .thenBy { it.state?.status?.let { s -> -s.ordinal } ?: 0 }
                    .thenBy { it.reminder.dueDate ?: it.state?.estimatedDueDate ?: Long.MAX_VALUE },
            )
        }

    /** Number of open reminders that are due soon or overdue, for the bell badge. */
    fun observeDueCount(vehicleId: Long, clock: () -> Long = System::currentTimeMillis): Flow<Int> =
        observeItems(vehicleId, clock).map { list -> list.count { it.state != null && it.state.status != ReminderStatus.UPCOMING } }

    suspend fun usage(vehicleId: Long, now: Long): VehicleUsage =
        ReminderEngine.usage(reminders.getReadings().filter { it.vehicleId == vehicleId }, now)

    suspend fun save(reminder: Reminder): Long = reminders.insert(reminder)

    /** Marks a reminder done. A repeating one gets its next occurrence. Returns that next reminder when created. */
    suspend fun markDone(id: Long, now: Long): Reminder? {
        val reminder = reminders.get(id) ?: return null
        reminders.update(reminder.copy(done = true))
        val next = ReminderEngine.nextOccurrence(reminder, usage(reminder.vehicleId, now), now) ?: return null
        return next.copy(id = reminders.insert(next))
    }

    suspend fun delete(reminder: Reminder) = reminders.delete(reminder)
}
