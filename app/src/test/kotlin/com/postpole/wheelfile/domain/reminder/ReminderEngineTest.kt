package com.postpole.wheelfile.domain.reminder

import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderEngineTest {
    private val zone = ZoneId.of("UTC")
    private val day = 86_400_000L
    private val now = LocalDate.of(2026, 6, 15).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun date(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun at(d: LocalDate, hour: Int) = d.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun reminder(
        kind: ReminderKind,
        dueDate: Long? = null,
        dueOdometer: Double? = null,
        leadDays: Int = 7,
        leadDistance: Double? = 500.0,
    ) = Reminder(
        id = 1, vehicleId = 1, title = "Service", kind = kind, dueDate = dueDate, dueOdometer = dueOdometer,
        leadDays = leadDays, leadDistance = leadDistance,
    )

    private fun eval(r: Reminder, usage: VehicleUsage = VehicleUsage(10_000.0, 40.0)) = ReminderEngine.evaluate(r, usage, now, zone)

    // ---- date trigger ----

    @Test
    fun dateReminderMovesFromUpcomingToDueSoonToOverdue() {
        fun status(due: LocalDate) = eval(reminder(ReminderKind.DATE, dueDate = date(due))).status
        assertEquals(ReminderStatus.UPCOMING, status(LocalDate.of(2026, 7, 15)))
        assertEquals(ReminderStatus.DUE_SOON, status(LocalDate.of(2026, 6, 22))) // exactly the 7-day lead
        assertEquals(ReminderStatus.DUE_SOON, status(LocalDate.of(2026, 6, 15))) // today
        assertEquals(ReminderStatus.OVERDUE, status(LocalDate.of(2026, 6, 14)))
    }

    @Test
    fun daysLeftCountsCalendarDays() {
        assertEquals(10L, eval(reminder(ReminderKind.DATE, dueDate = date(LocalDate.of(2026, 6, 25)))).daysLeft)
    }

    // ---- odometer trigger ----

    @Test
    fun odometerReminderUsesLeadDistance() {
        fun status(current: Double) =
            ReminderEngine.evaluate(reminder(ReminderKind.ODOMETER, dueOdometer = 10_000.0), VehicleUsage(current, null), now, zone).status
        assertEquals(ReminderStatus.UPCOMING, status(9_000.0))
        assertEquals(ReminderStatus.DUE_SOON, status(9_500.0))
        assertEquals(ReminderStatus.OVERDUE, status(10_000.0))
        assertEquals(ReminderStatus.OVERDUE, status(10_250.0))
    }

    @Test
    fun odometerReminderWithoutAReadingStaysUpcoming() {
        val state = ReminderEngine.evaluate(reminder(ReminderKind.ODOMETER, dueOdometer = 10_000.0), VehicleUsage.UNKNOWN, now, zone)
        assertEquals(ReminderStatus.UPCOMING, state.status)
        assertNull(state.distanceLeft)
    }

    // ---- whichever first ----

    @Test
    fun whicheverFirstFiresOnTheDateEvenIfTheOdometerIsFar() {
        val r = reminder(ReminderKind.FIRST_OF, dueDate = date(LocalDate.of(2026, 6, 16)), dueOdometer = 20_000.0)
        assertEquals(ReminderStatus.DUE_SOON, eval(r).status)
    }

    @Test
    fun whicheverFirstFiresOnTheOdometerEvenIfTheDateIsFar() {
        val r = reminder(ReminderKind.FIRST_OF, dueDate = date(LocalDate.of(2027, 6, 16)), dueOdometer = 10_100.0)
        assertEquals(ReminderStatus.DUE_SOON, eval(r).status)
    }

    @Test
    fun whicheverFirstTakesTheMoreUrgentStatus() {
        val r = reminder(ReminderKind.FIRST_OF, dueDate = date(LocalDate.of(2026, 6, 10)), dueOdometer = 10_300.0)
        assertEquals(ReminderStatus.OVERDUE, eval(r).status)
    }

    @Test
    fun whicheverFirstStaysUpcomingWhenNeitherIsNear() {
        val r = reminder(ReminderKind.FIRST_OF, dueDate = date(LocalDate.of(2027, 6, 16)), dueOdometer = 20_000.0)
        assertEquals(ReminderStatus.UPCOMING, eval(r).status)
    }

    @Test
    fun aDateReminderIgnoresAStrayOdometer() {
        val r = reminder(ReminderKind.DATE, dueDate = date(LocalDate.of(2027, 6, 16)), dueOdometer = 10_001.0)
        assertEquals(ReminderStatus.UPCOMING, eval(r).status)
    }

    // ---- projection ----

    @Test
    fun usageIsTheAverageDailyDistanceOverTheRecentWindow() {
        val readings = listOf(
            OdometerReading(1, now - 30 * day, 9_000.0),
            OdometerReading(1, now - 10 * day, 9_800.0),
            OdometerReading(1, now, 10_000.0),
        )
        val usage = ReminderEngine.usage(readings, now)
        assertEquals(10_000.0, usage.currentOdometer!!, 0.0)
        assertEquals(1_000.0 / 30.0, usage.dailyDistance!!, 1e-9)
    }

    @Test
    fun readingsOlderThanNinetyDaysAreIgnoredForTheAverage() {
        val readings = listOf(
            OdometerReading(1, now - 200 * day, 1_000.0),
            OdometerReading(1, now - 100 * day, 5_000.0),
            OdometerReading(1, now - 20 * day, 9_000.0),
            OdometerReading(1, now, 9_400.0),
        )
        // Only the last two are inside the window: 400 over 20 days.
        assertEquals(20.0, ReminderEngine.usage(readings, now).dailyDistance!!, 1e-9)
    }

    @Test
    fun usageNeedsTwoReadingsAtLeastADayApart() {
        assertNull(ReminderEngine.usage(listOf(OdometerReading(1, now, 5_000.0)), now).dailyDistance)
        assertNull(ReminderEngine.usage(listOf(OdometerReading(1, now - 1_000, 5_000.0), OdometerReading(1, now, 5_010.0)), now).dailyDistance)
        assertEquals(VehicleUsage.UNKNOWN, ReminderEngine.usage(emptyList(), now))
        assertEquals(5_000.0, ReminderEngine.usage(listOf(OdometerReading(1, now, 5_000.0)), now).currentOdometer!!, 0.0)
    }

    @Test
    fun usageIsNullWhenTheOdometerDidNotMove() {
        val readings = listOf(OdometerReading(1, now - 10 * day, 5_000.0), OdometerReading(1, now, 5_000.0))
        assertNull(ReminderEngine.usage(readings, now).dailyDistance)
    }

    @Test
    fun estimateRoundsUpToWholeDays() {
        assertEquals(now + 13 * day, ReminderEngine.estimateDate(500.0, 40.0, now))
        assertEquals(now + 1 * day, ReminderEngine.estimateDate(1.0, 40.0, now))
        assertNull(ReminderEngine.estimateDate(500.0, null, now))
        assertNull(ReminderEngine.estimateDate(0.0, 40.0, now))
        assertNull(ReminderEngine.estimateDate(-5.0, 40.0, now))
    }

    @Test
    fun evaluateExposesTheEstimateForOdometerReminders() {
        val state = eval(reminder(ReminderKind.ODOMETER, dueOdometer = 10_400.0), VehicleUsage(10_000.0, 40.0))
        assertEquals(400.0, state.distanceLeft!!, 0.0)
        assertEquals(now + 10 * day, state.estimatedDueDate)
    }

    // ---- notifications ----

    @Test
    fun notifiesOnceThenWeeklyWhileOverdue() {
        val dueSoon = reminder(ReminderKind.DATE, dueDate = date(LocalDate.of(2026, 6, 16)))
        val soonState = eval(dueSoon)
        assertTrue(ReminderEngine.shouldNotify(dueSoon, soonState, now))
        assertFalse(ReminderEngine.shouldNotify(dueSoon.copy(lastNotifiedAt = now - day), soonState, now))

        val overdue = reminder(ReminderKind.DATE, dueDate = date(LocalDate.of(2026, 6, 1)))
        val overdueState = eval(overdue)
        assertFalse(ReminderEngine.shouldNotify(overdue.copy(lastNotifiedAt = now - 3 * day), overdueState, now))
        assertTrue(ReminderEngine.shouldNotify(overdue.copy(lastNotifiedAt = now - 7 * day), overdueState, now))
    }

    @Test
    fun neverNotifiesForUpcomingOrDone() {
        val upcoming = reminder(ReminderKind.DATE, dueDate = date(LocalDate.of(2027, 1, 1)))
        assertFalse(ReminderEngine.shouldNotify(upcoming, eval(upcoming), now))
        val done = reminder(ReminderKind.DATE, dueDate = date(LocalDate.of(2026, 6, 1))).copy(done = true)
        assertFalse(ReminderEngine.shouldNotify(done, eval(done), now))
    }

    // ---- repeat ----

    @Test
    fun repeatingDateReminderGetsTheNextOccurrenceFromCompletion() {
        val r = reminder(ReminderKind.DATE, dueDate = date(LocalDate.of(2026, 6, 1))).copy(repeatMonths = 12, lastNotifiedAt = 5)
        val next = ReminderEngine.nextOccurrence(r, VehicleUsage.UNKNOWN, now, zone)!!
        assertEquals(date(LocalDate.of(2027, 6, 15)), next.dueDate)
        assertEquals(0L, next.id)
        assertFalse(next.done)
        assertNull(next.lastNotifiedAt)
    }

    @Test
    fun repeatingOdometerReminderCountsFromTheCurrentReading() {
        val r = reminder(ReminderKind.ODOMETER, dueOdometer = 10_000.0).copy(repeatDistance = 15_000.0)
        val next = ReminderEngine.nextOccurrence(r, VehicleUsage(10_250.0, null), now, zone)!!
        assertEquals(25_250.0, next.dueOdometer!!, 0.0)
        assertEquals(ReminderKind.ODOMETER, next.kind)
    }

    @Test
    fun repeatingWhicheverFirstKeepsBothTriggers() {
        val r = reminder(ReminderKind.FIRST_OF, dueDate = date(LocalDate.of(2026, 6, 1)), dueOdometer = 10_000.0)
            .copy(repeatMonths = 6, repeatDistance = 10_000.0)
        val next = ReminderEngine.nextOccurrence(r, VehicleUsage(10_100.0, null), now, zone)!!
        assertEquals(ReminderKind.FIRST_OF, next.kind)
        assertEquals(date(LocalDate.of(2026, 12, 15)), next.dueDate)
        assertEquals(20_100.0, next.dueOdometer!!, 0.0)
    }

    @Test
    fun nonRepeatingReminderHasNoNextOccurrence() {
        assertNull(ReminderEngine.nextOccurrence(reminder(ReminderKind.DATE, dueDate = date(LocalDate.of(2026, 6, 1))), VehicleUsage.UNKNOWN, now, zone))
        // A distance repeat does not apply to a date-only reminder.
        val dateOnly = reminder(ReminderKind.DATE, dueDate = date(LocalDate.of(2026, 6, 1))).copy(repeatDistance = 5_000.0)
        assertNull(ReminderEngine.nextOccurrence(dateOnly, VehicleUsage.UNKNOWN, now, zone))
        assertNotNull(ReminderEngine.nextOccurrence(dateOnly.copy(repeatMonths = 3), VehicleUsage.UNKNOWN, now, zone))
    }

    // ---- quiet hours ----

    @Test
    fun quietHoursWrapMidnight() {
        val quiet = QuietHours(enabled = true, startHour = 21, endHour = 8)
        val d = LocalDate.of(2026, 6, 15)
        assertTrue(quiet.contains(at(d, 22), zone))
        assertTrue(quiet.contains(at(d, 3), zone))
        assertTrue(quiet.contains(at(d, 7), zone))
        assertFalse(quiet.contains(at(d, 8), zone))
        assertFalse(quiet.contains(at(d, 12), zone))
        assertFalse(quiet.contains(at(d, 20), zone))
    }

    @Test
    fun quietHoursSameDayAndDisabled() {
        val quiet = QuietHours(enabled = true, startHour = 13, endHour = 15)
        val d = LocalDate.of(2026, 6, 15)
        assertTrue(quiet.contains(at(d, 14), zone))
        assertFalse(quiet.contains(at(d, 15), zone))
        assertFalse(QuietHours(enabled = false).contains(at(d, 23), zone))
        assertFalse(QuietHours(enabled = true, startHour = 8, endHour = 8).contains(at(d, 8), zone))
    }

    @Test
    fun quietHoursEndAtTheNextEndHour() {
        val quiet = QuietHours(enabled = true, startHour = 21, endHour = 8)
        val d = LocalDate.of(2026, 6, 15)
        assertEquals(at(d.plusDays(1), 8), quiet.endAfter(at(d, 23), zone))
        assertEquals(at(d, 8), quiet.endAfter(at(d, 3), zone))
    }
}
