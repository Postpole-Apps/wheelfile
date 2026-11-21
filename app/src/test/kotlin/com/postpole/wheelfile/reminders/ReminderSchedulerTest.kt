package com.postpole.wheelfile.reminders

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.reminder.QuietHours
import com.postpole.wheelfile.domain.reminder.ReminderState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/** The daily reminder job on WorkManager's test helpers, and the check it runs on an injectable test clock. */
@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {
    private val zone = ZoneId.of("UTC")
    private val day = 86_400_000L
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private var now = at(LocalDate.of(2026, 6, 15), 10)
    private val posted = mutableListOf<Long>()
    private var canPost = true
    private var quiet = QuietHours(enabled = false)

    private fun at(d: LocalDate, hour: Int) = d.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun checker() = ReminderChecker(
        reminders = db.reminderDao(),
        vehicles = db.vehicleDao(),
        notifier = ReminderNotifier { reminder: Reminder, _: Vehicle, _: ReminderState ->
            if (canPost) posted += reminder.id
            canPost
        },
        quietHours = { quiet },
        clock = { now },
        zone = { zone },
    )

    private suspend fun vehicle(archived: Boolean = false) = db.vehicleDao().insert(
        Vehicle(
            name = "Car", type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
            volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", archived = archived,
            createdAt = 1, updatedAt = 1,
        ),
    )

    private suspend fun reading(vehicleId: Long, daysAgo: Long, odometer: Double) = db.energyLogDao().insert(
        EnergyLog(
            vehicleId = vehicleId, stream = EnergyStream.LIQUID, dateTime = now - daysAgo * day, odometer = odometer,
            quantity = 40.0, isFull = true, createdAt = 1, updatedAt = 1,
        ),
    )

    private suspend fun remind(vehicleId: Long, kind: ReminderKind, dueDate: Long? = null, dueOdometer: Double? = null) =
        db.reminderDao().insert(
            Reminder(vehicleId = vehicleId, title = "Service", kind = kind, dueDate = dueDate, dueOdometer = dueOdometer, leadDays = 7, leadDistance = 500.0),
        )

    // ---- WorkManager scheduling ----

    @Test
    fun dailyWorkIsEnqueuedOnceAsAUniquePeriodicJob() {
        val scheduler = ReminderScheduler(context)
        scheduler.scheduleDaily(now, zone)
        scheduler.scheduleDaily(now, zone)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ReminderScheduler.DAILY_WORK).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
    }

    @Test
    fun deferredRunIsAOneTimeJobAndReplacesTheEarlierOne() {
        val scheduler = ReminderScheduler(context)
        scheduler.scheduleDeferred(60_000)
        scheduler.scheduleDeferred(120_000)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ReminderScheduler.DEFERRED_WORK).get()
        assertEquals(WorkInfo.State.ENQUEUED, infos.last().state)
        assertTrue(infos.dropLast(1).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun firstDailyRunLandsAtNineLocalTime() {
        assertEquals(23 * 60 * 60 * 1000L, ReminderScheduler.initialDelayMillis(at(LocalDate.of(2026, 6, 15), 10), zone))
        assertEquals(60 * 60 * 1000L, ReminderScheduler.initialDelayMillis(at(LocalDate.of(2026, 6, 15), 8), zone))
        assertEquals(24 * 60 * 60 * 1000L, ReminderScheduler.initialDelayMillis(at(LocalDate.of(2026, 6, 15), 9), zone))
    }

    // ---- the check, on a test clock ----

    @Test
    fun dateReminderFiresWhenItsLeadTimeStarts() = runBlocking {
        val v = vehicle()
        val id = remind(v, ReminderKind.DATE, dueDate = at(LocalDate.of(2026, 6, 22), 0))
        assertEquals(1, checker().run().posted)
        assertEquals(listOf(id), posted)
    }

    @Test
    fun dateReminderStaysQuietUntilItsLeadTimeStarts() = runBlocking {
        val v = vehicle()
        remind(v, ReminderKind.DATE, dueDate = at(LocalDate.of(2026, 6, 23), 0))
        assertEquals(0, checker().run().posted)
        // Advance the test clock one day: now it is due soon.
        now += day
        assertEquals(1, checker().run().posted)
    }

    @Test
    fun odometerReminderFiresOnTheReading() = runBlocking {
        val v = vehicle()
        reading(v, 20, 9_000.0)
        remind(v, ReminderKind.ODOMETER, dueOdometer = 10_000.0)
        assertEquals(0, checker().run().posted)
        reading(v, 0, 9_600.0) // within the 500 km lead
        assertEquals(1, checker().run().posted)
    }

    @Test
    fun whicheverFirstFiresOnEitherTrigger() = runBlocking {
        val v = vehicle()
        reading(v, 0, 1_000.0)
        val far = at(LocalDate.of(2027, 1, 1), 0)
        val byDate = remind(v, ReminderKind.FIRST_OF, dueDate = at(LocalDate.of(2026, 6, 16), 0), dueOdometer = 50_000.0)
        val byOdometer = remind(v, ReminderKind.FIRST_OF, dueDate = far, dueOdometer = 1_200.0)
        val neither = remind(v, ReminderKind.FIRST_OF, dueDate = far, dueOdometer = 50_000.0)
        assertEquals(2, checker().run().posted)
        assertEquals(setOf(byDate, byOdometer), posted.toSet())
        assertTrue(neither !in posted)
    }

    @Test
    fun aReminderIsNotRepeatedTheSameDay() = runBlocking {
        val v = vehicle()
        val id = remind(v, ReminderKind.DATE, dueDate = at(LocalDate.of(2026, 6, 16), 0))
        assertEquals(1, checker().run().posted)
        assertEquals(now, db.reminderDao().get(id)!!.lastNotifiedAt)
        assertEquals(0, checker().run().posted)
        now += day
        assertEquals(0, checker().run().posted)
    }

    @Test
    fun overdueReminderIsRepeatedWeekly() = runBlocking {
        val v = vehicle()
        remind(v, ReminderKind.DATE, dueDate = at(LocalDate.of(2026, 6, 1), 0))
        assertEquals(1, checker().run().posted)
        now += 6 * day
        assertEquals(0, checker().run().posted)
        now += day
        assertEquals(1, checker().run().posted)
    }

    @Test
    fun quietHoursDeferTheRunUntilTheyEnd() = runBlocking {
        val v = vehicle()
        remind(v, ReminderKind.DATE, dueDate = at(LocalDate.of(2026, 6, 16), 0))
        quiet = QuietHours(enabled = true, startHour = 21, endHour = 8)
        now = at(LocalDate.of(2026, 6, 15), 23)

        val deferred = checker().run()
        assertEquals(0, deferred.posted)
        assertEquals(at(LocalDate.of(2026, 6, 16), 8), deferred.deferUntil)
        assertTrue(posted.isEmpty())

        now = deferred.deferUntil!!
        val later = checker().run()
        assertEquals(1, later.posted)
        assertNull(later.deferUntil)
    }

    @Test
    fun deniedNotificationsKeepTheReminderPendingForLater() = runBlocking {
        val v = vehicle()
        val id = remind(v, ReminderKind.DATE, dueDate = at(LocalDate.of(2026, 6, 16), 0))
        canPost = false
        assertEquals(0, checker().run().posted)
        assertNull(db.reminderDao().get(id)!!.lastNotifiedAt)
        canPost = true
        assertEquals(1, checker().run().posted)
    }

    @Test
    fun archivedVehiclesAndDoneRemindersAreSkipped() = runBlocking {
        val archived = vehicle(archived = true)
        remind(archived, ReminderKind.DATE, dueDate = at(LocalDate.of(2026, 6, 16), 0))
        val active = vehicle()
        val doneId = remind(active, ReminderKind.DATE, dueDate = at(LocalDate.of(2026, 6, 16), 0))
        db.reminderDao().update(db.reminderDao().get(doneId)!!.copy(done = true))
        assertEquals(0, checker().run().posted)
        assertNotNull(db.reminderDao().get(doneId))
    }
}
