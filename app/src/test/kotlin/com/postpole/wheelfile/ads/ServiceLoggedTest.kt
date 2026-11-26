package com.postpole.wheelfile.ads

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.ui.pro.PaywallTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/** `service_logged` (spec v0.2.2 §5.4a): the once-ever gate, the coordinator and the shared policy rules. */
class ServiceLoggedTest {
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
    private var fired: Long? = null
    private var done = false
    private val gate = object : ServiceLoggedGate {
        override suspend fun tryConsume(savedAt: Long): Boolean {
            val f = fired
            if (f == null || f > savedAt || done) return false
            done = true
            return true
        }
    }
    private val coordinator =
        ProPromptCoordinator(policy, { ProPromptConfig() }, { access }, { 0 }, analytics, gate)
    private val service = ProPromptTrigger.SERVICE_LOGGED

    private fun inSession(n: Int) {
        now = install + 4 * day
        runBlocking { repeat(n) { policy.startSession(install) } }
    }

    private fun serviceSaved() = runBlocking {
        policy.onSaved(SaveKind.SERVICE)
        coordinator.onServiceSaved(now)
    }

    private fun restPoint(point: ProPromptRestPoint) = runBlocking { coordinator.onRestPoint(point) }

    @Test fun firstServiceAfterAFireQualifiesAndASecondDoesNot() {
        inSession(3)
        fired = now - day
        serviceSaved()
        assertEquals(ProPromptSurface.SHEET, policy.current.pending[service.id]?.surface)
        val shown = restPoint(ProPromptRestPoint.VEHICLE_OVERVIEW)
        assertEquals(ActiveProPrompt(service, ProPromptSurface.SHEET), shown)
        serviceSaved()
        assertNull(policy.current.pending[service.id])
    }

    @Test fun serviceWithNoPriorFireDoesNotQualifyAndKeepsTheGateOpen() {
        inSession(3)
        serviceSaved()
        assertTrue(policy.current.pending.isEmpty())
        fired = now + 1 // a fire after the save does not count for it
        serviceSaved()
        assertTrue(policy.current.pending.isEmpty())
        fired = now - 1
        serviceSaved()
        assertNotNull(policy.current.pending[service.id])
    }

    @Test fun shownSheetLogsAnalyticsWithEnumParams() {
        inSession(3)
        fired = now - day
        serviceSaved()
        val prompt = restPoint(ProPromptRestPoint.VEHICLE_OVERVIEW)!!
        coordinator.onCtaTapped(prompt)
        runBlocking { coordinator.onDismissed(prompt, ProPromptDismissMethod.NOT_NOW) }
        assertEquals(
            listOf(
                "pro_prompt_shown" to mapOf("trigger" to "service_logged", "surface" to "sheet"),
                "pro_prompt_cta_tap" to mapOf("trigger" to "service_logged", "surface" to "sheet"),
                "pro_prompt_dismissed" to mapOf("trigger" to "service_logged", "surface" to "sheet", "method" to "not_now"),
            ),
            events,
        )
        assertEquals(PaywallTrigger.AUTO_SERVICE_LOGGED, ProPromptCoordinator.paywallTrigger(service))
    }

    @Test fun blockedSheetBecomesANextSessionCardAndIsDroppedAfterSevenDays() {
        inSession(3)
        fired = now - day
        serviceSaved() // pump-session flag is not set by a service; set it as a fill-up in the same session would
        runBlocking { policy.onSaved(SaveKind.FILL_UP) }
        assertNull(restPoint(ProPromptRestPoint.VEHICLE_OVERVIEW))
        assertEquals(ProPromptSurface.CARD, policy.current.pending[service.id]?.surface)
        // Same session: still nothing. Next session: the card shows on the Garage, which has the card slot.
        assertNull(restPoint(ProPromptRestPoint.GARAGE))
        runBlocking { policy.startSession(install) }
        assertEquals(ActiveProPrompt(service, ProPromptSurface.CARD), restPoint(ProPromptRestPoint.GARAGE))
    }

    @Test fun blockedCardNotShownWithinSevenDaysIsDropped() {
        inSession(3)
        fired = now - day
        serviceSaved()
        runBlocking { policy.onSaved(SaveKind.EV_CHARGE) }
        assertNull(restPoint(ProPromptRestPoint.VEHICLE_OVERVIEW))
        now += 7 * day + 1
        runBlocking { policy.startSession(install) }
        assertNull(restPoint(ProPromptRestPoint.GARAGE))
        assertNull(policy.current.pending[service.id])
    }

    @Test fun removeAdsOwnerNeverGetsTheSheetOnlyTheCard() {
        inSession(3)
        access = ProAccess.REMOVE_ADS
        fired = now - day
        serviceSaved()
        assertNull(restPoint(ProPromptRestPoint.VEHICLE_OVERVIEW)) // Overview has no card slot, no sheet either
        assertEquals(ProPromptSurface.CARD, policy.current.pending[service.id]?.surface)
        assertEquals(0, policy.current.sheetDismissals)
        assertEquals(ActiveProPrompt(service, ProPromptSurface.CARD), restPoint(ProPromptRestPoint.GARAGE))
        assertTrue(events.none { it.second["surface"] == "sheet" })
    }

    @Test fun proOwnerSeesNothing() {
        inSession(3)
        access = ProAccess.PRO
        fired = now - day
        serviceSaved()
        assertNull(restPoint(ProPromptRestPoint.VEHICLE_OVERVIEW))
        assertNull(restPoint(ProPromptRestPoint.GARAGE))
        assertTrue(events.isEmpty())
    }

    @Test fun resetAfterWipeDropsInMemoryStateSoItIsNotWrittenBack() {
        inSession(3)
        fired = now - day
        serviceSaved()
        runBlocking { policy.onHardGateShown() }
        runBlocking { policy.resetAfterWipe() }
        assertTrue(policy.current.pending.isEmpty())
        assertNull(policy.current.lastHardGateAt)
        assertTrue(policy.current.shownCounts.isEmpty())
        assertEquals(1, policy.current.sessionCount)
    }

    @Test fun hardGatePaywallsAreTheLimitTriggersOnly() {
        assertTrue(PaywallTrigger.VEHICLE_LIMIT.isHardGate)
        assertTrue(PaywallTrigger.ATTACHMENT_LIMIT.isHardGate)
        assertFalse(PaywallTrigger.AUTO_SERVICE_LOGGED.isHardGate)
        assertFalse(PaywallTrigger.SETTINGS.isHardGate)
    }

    @Test fun hardGateWithin48HoursBlocksTheSheet() {
        inSession(3)
        fired = now - day
        serviceSaved()
        runBlocking { policy.onHardGateShown() }
        assertNull(restPoint(ProPromptRestPoint.VEHICLE_OVERVIEW))
        now += 49L * 60 * 60 * 1_000
        assertNotNull(restPoint(ProPromptRestPoint.VEHICLE_OVERVIEW))
    }

    @Test fun dataStoreGateConsumesOnceOnlyAfterAFire() = runBlocking {
        val file = File(Files.createTempDirectory("gate").toFile(), "gate.preferences_pb")
        val ds = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) { file }
        val gate = DataStoreServiceLoggedGate(ds)
        assertFalse(gate.tryConsume(1_000L)) // no fire yet
        DataStoreReminderFiredStore(ds).recordFired(500L)
        assertFalse(gate.tryConsume(400L)) // saved before the fire
        assertTrue(gate.tryConsume(1_000L))
        assertFalse(gate.tryConsume(2_000L)) // once ever
    }
}
