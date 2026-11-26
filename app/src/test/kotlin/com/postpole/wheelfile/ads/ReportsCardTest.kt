package com.postpole.wheelfile.ads

import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.ui.pro.PaywallTrigger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

/** `reports_3_months` (spec v0.2.2 §5.4a): the 3-month threshold, the Reports rest point and the shared caps. */
class ReportsCardTest {
    private val day = 24L * 60 * 60 * 1_000
    private val install = 1_700_000_000_000L
    private var now = install
    private var access = ProAccess.FREE
    private val events = CopyOnWriteArrayList<Pair<String, Map<String, Any>>>()
    private val analytics = object : Analytics {
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = throw throwable
    }
    private val policy = ProPromptPolicy({ now }, InMemoryProPromptStateStore())
    private val coordinator = ProPromptCoordinator(policy, { ProPromptConfig() }, { access }, { 0 }, analytics)
    private val reports = ProPromptTrigger.REPORTS_3_MONTHS

    private fun inSession(n: Int) {
        now = install + 4 * day
        runBlocking { repeat(n) { policy.startSession(install) } }
    }

    private fun opened(firstEntryAt: Long?) = runBlocking {
        coordinator.onReportsOpened(firstEntryAt, now)
        coordinator.onRestPoint(ProPromptRestPoint.VEHICLE_REPORTS)
    }

    private fun millis(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun threeMonthsQualifiesAndLessDoesNot() {
        val today = LocalDate.of(2026, 11, 26)
        val now = millis(today)
        fun q(first: LocalDate?) =
            ProPromptCoordinator.hasThreeMonthsOfEntries(first?.let(::millis), now, ZoneOffset.UTC)
        assertTrue(q(today.minusMonths(3)))
        assertTrue(q(today.minusMonths(6)))
        assertFalse(q(today.minusMonths(3).plusDays(1)))
        assertFalse(q(today.minusMonths(2)))
        assertFalse(q(null))
    }

    private fun utcMillis(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun monthLengthClampsTheThreeMonthBoundary() {
        // 31 Aug + 3 months clamps to 30 Nov (November has 30 days).
        val first = millis(LocalDate.of(2026, 8, 31))
        fun q(today: LocalDate) =
            ProPromptCoordinator.hasThreeMonthsOfEntries(first, millis(today), ZoneOffset.UTC)
        assertFalse(q(LocalDate.of(2026, 11, 29)))
        assertTrue(q(LocalDate.of(2026, 11, 30)))
        assertTrue(q(LocalDate.of(2026, 12, 1)))
    }

    @Test fun localZoneDecidesTheDateNotUtc() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        // 2026-08-26T20:00Z is 27 Aug 01:30 in Kolkata. Now is 26 Nov in both zones.
        val first = utcMillis("2026-08-26T20:00:00Z")
        val now = utcMillis("2026-11-26T10:00:00Z")
        assertTrue(ProPromptCoordinator.hasThreeMonthsOfEntries(first, now, ZoneOffset.UTC)) // 26 Aug + 3 = 26 Nov
        assertFalse(ProPromptCoordinator.hasThreeMonthsOfEntries(first, now, kolkata)) // 27 Aug + 3 = 27 Nov
        // The next local day qualifies in Kolkata.
        assertTrue(ProPromptCoordinator.hasThreeMonthsOfEntries(first, utcMillis("2026-11-26T20:00:00Z"), kolkata))
    }

    @Test fun localZoneAcrossADstChange() {
        val la = ZoneId.of("America/Los_Angeles")
        // US DST ended on 2026-11-01. 06:30Z that day is 31 Oct 23:30 PDT (the UTC date is already 1 Nov).
        val first = utcMillis("2026-11-01T06:30:00Z")
        // 2027-01-31T20:00Z is 31 Jan 12:00 PST. Local: 31 Oct + 3 months = 31 Jan, so it qualifies.
        val now = utcMillis("2027-01-31T20:00:00Z")
        assertTrue(ProPromptCoordinator.hasThreeMonthsOfEntries(first, now, la))
        // In UTC the first entry is on 1 Nov, so the same instant is still a day short.
        assertFalse(ProPromptCoordinator.hasThreeMonthsOfEntries(first, now, ZoneOffset.UTC))
        // One local day earlier does not qualify in LA either.
        assertFalse(ProPromptCoordinator.hasThreeMonthsOfEntries(first, utcMillis("2027-01-30T20:00:00Z"), la))
    }

    @Test fun threeMonthVehicleShowsTheCardAndLogsShown() {
        inSession(2)
        val shown = opened(now - 100 * day)
        assertEquals(ActiveProPrompt(reports, ProPromptSurface.CARD), shown)
        val shownEvent = events.single { it.first == "pro_prompt_shown" }
        assertEquals("reports_3_months", shownEvent.second["trigger"])
        assertEquals("card", shownEvent.second["surface"])
    }

    @Test fun lessThanThreeMonthsNeverQualifies() {
        inSession(2)
        assertNull(opened(now - 60 * day))
        assertTrue(policy.current.pending.isEmpty())
    }

    @Test fun firstSessionIsQuiet() {
        inSession(1)
        assertNull(opened(now - 100 * day))
    }

    @Test fun oneAutomaticPromptPerSession() {
        inSession(2)
        assertTrue(opened(now - 100 * day) != null)
        runBlocking { coordinator.onDismissed(ActiveProPrompt(reports, ProPromptSurface.CARD), ProPromptDismissMethod.CLOSE) }
        now += 15 * day
        runBlocking { policy.onForegrounded() }
        // Same session: the cap holds even after the re-show window.
        assertNull(opened(now - 100 * day))
    }

    @Test fun reshowAfterDismissalWaitsFourteenDaysAndStopsAfterThree() {
        inSession(2)
        val prompt = ActiveProPrompt(reports, ProPromptSurface.CARD)
        repeat(3) {
            assertTrue("show $it", opened(now - 400 * day) != null)
            runBlocking { coordinator.onDismissed(prompt, ProPromptDismissMethod.CLOSE) }
            now += 13 * day
            runBlocking {
                policy.onBackgrounded()
                now += 31 * 60 * 1_000
                policy.onForegrounded()
            }
            assertNull("14-day window $it", opened(now - 400 * day))
            now += 2 * day
            runBlocking {
                policy.onBackgrounded()
                now += 31 * 60 * 1_000
                policy.onForegrounded()
            }
        }
        assertNull(opened(now - 400 * day))
        assertEquals(3, policy.current.dismissedCounts["reports_3_months:card"])
    }

    @Test fun proOwnersNeverRemoveAdsOwnersMay() {
        inSession(2)
        access = ProAccess.PRO
        assertNull(opened(now - 100 * day))
        access = ProAccess.REMOVE_ADS
        assertEquals(ActiveProPrompt(reports, ProPromptSurface.CARD), opened(now - 100 * day))
    }

    @Test fun pumpSessionBlocksAndHardGateSkips() {
        inSession(2)
        policy.onSaved(SaveKind.FILL_UP)
        assertNull(opened(now - 100 * day))
        inSession(2)
        runBlocking { policy.onHardGateShown() }
        assertNull(opened(now - 100 * day))
    }

    @Test fun otherRestPointsNeverEvaluateTheReportsCard() {
        inSession(2)
        runBlocking { coordinator.onReportsOpened(now - 100 * day, now) }
        assertNull(runBlocking { coordinator.onRestPoint(ProPromptRestPoint.GARAGE) })
        assertNull(runBlocking { coordinator.onRestPoint(ProPromptRestPoint.VEHICLE_OVERVIEW) })
        assertTrue(runBlocking { coordinator.onRestPoint(ProPromptRestPoint.VEHICLE_REPORTS) } != null)
    }

    @Test fun ctaOpensTheAutoReportsPaywall() {
        assertEquals(PaywallTrigger.AUTO_REPORTS_3_MONTHS, ProPromptCoordinator.paywallTrigger(reports))
    }
}
