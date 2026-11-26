package com.postpole.wheelfile.ads

import com.postpole.wheelfile.analytics.Analytics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The coordinator wired to the real [ProPromptPolicy]: the triggers, the rest points and the analytics. */
class ProPromptCoordinatorTest {
    private val day = 24L * 60 * 60 * 1_000
    private val install = 1_700_000_000_000L
    private var now = install
    private var entries = 0
    private var access = ProAccess.FREE
    private var config = ProPromptConfig()
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val analytics = object : Analytics {
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = throw throwable
    }
    private val policy = ProPromptPolicy({ now }, InMemoryProPromptStateStore())
    private val coordinator = ProPromptCoordinator(policy, { config }, { access }, { entries }, analytics)

    private val garage = ProPromptRestPoint.GARAGE
    private val overview = ProPromptRestPoint.VEHICLE_OVERVIEW
    private val milestone = ProPromptTrigger.ENTRIES_MILESTONE
    private val secondVehicle = ProPromptTrigger.SECOND_VEHICLE_ADDED

    private fun launch() = runBlocking { policy.startSession(install) }

    /** Session [n], four days after install, so the first-session and first-3-days rules are clear. */
    private fun inSession(n: Int) {
        now = install + 4 * day
        repeat(n) { launch() }
    }

    private fun saved(kind: SaveKind, total: Int) {
        entries = total
        runBlocking {
            policy.onSaved(kind)
            coordinator.onEntrySaved(kind)
        }
    }

    private fun restPoint(point: ProPromptRestPoint) = runBlocking { coordinator.onRestPoint(point) }

    // --- entries_milestone ---

    @Test fun tenthEntryQualifiesAndOtherCountsDoNot() {
        inSession(2)
        saved(SaveKind.SERVICE, 9)
        saved(SaveKind.SERVICE, 11)
        assertTrue(policy.current.pending.isEmpty())
        saved(SaveKind.SERVICE, 10)
        assertEquals(setOf(milestone.id), policy.current.pending.keys)
    }

    @Test fun deletedEntriesDoNotCount() {
        inSession(2)
        // 10 saved, 1 deleted: the count the database reports is 9 when the next entry is saved... and 10 after it.
        saved(SaveKind.SERVICE, 9)
        assertTrue(policy.current.pending.isEmpty())
        saved(SaveKind.EXPENSE, 10)
        assertNotNull(policy.current.pending[milestone.id])
    }

    @Test fun thresholdComesFromRemoteConfigAndIsClamped() {
        inSession(2)
        config = ProPromptConfig(entriesThreshold = 1) // below the bounds: clamped to 5
        saved(SaveKind.SERVICE, 1)
        assertTrue(policy.current.pending.isEmpty())
        saved(SaveKind.SERVICE, 5)
        assertNotNull(policy.current.pending[milestone.id])
    }

    @Test fun fillUpAtTheThresholdQueuesANextSessionGarageCard() {
        inSession(3)
        saved(SaveKind.FILL_UP, 10)
        val pending = policy.current.pending.getValue(milestone.id)
        assertEquals(ProPromptSurface.CARD, pending.surface)
        // Same session (pump-session): nothing shows, not even on the Garage.
        assertNull(restPoint(garage))
        launch()
        assertEquals(ActiveProPrompt(milestone, ProPromptSurface.CARD), restPoint(garage))
    }

    @Test fun chargeAtTheThresholdQueuesANextSessionGarageCard() {
        inSession(3)
        saved(SaveKind.EV_CHARGE, 10)
        assertEquals(ProPromptSurface.CARD, policy.current.pending.getValue(milestone.id).surface)
        assertNull(restPoint(garage))
    }

    @Test fun serviceAtTheThresholdShowsTheSheetOnOverviewButNotOnTheGarage() {
        inSession(3)
        saved(SaveKind.SERVICE, 10)
        assertEquals(ProPromptSurface.SHEET, policy.current.pending.getValue(milestone.id).surface)
        assertNull("the Garage cannot host a sheet", restPoint(garage))
        assertEquals(ActiveProPrompt(milestone, ProPromptSurface.SHEET), restPoint(overview))
    }

    @Test fun expenseAtTheThresholdShowsTheSheet() {
        inSession(3)
        saved(SaveKind.EXPENSE, 10)
        assertEquals(ActiveProPrompt(milestone, ProPromptSurface.SHEET), restPoint(overview))
    }

    @Test fun sheetBlockedByThePumpSessionBecomesANextSessionCard() {
        inSession(3)
        saved(SaveKind.FILL_UP, 3) // the session saved a fill-up earlier: pump-session
        saved(SaveKind.SERVICE, 10)
        assertNull(restPoint(overview))
        val pending = policy.current.pending.getValue(milestone.id)
        assertEquals(ProPromptSurface.CARD, pending.surface)
        launch()
        assertNull("a card has no slot on Overview", restPoint(overview))
        assertEquals(ActiveProPrompt(milestone, ProPromptSurface.CARD), restPoint(garage))
    }

    @Test fun removeAdsOwnerNeverGetsTheSheetOnlyTheGarageCard() {
        access = ProAccess.REMOVE_ADS
        inSession(3)
        saved(SaveKind.SERVICE, 10)
        // Overview has no card slot: the converted card is deferred, no sheet.
        assertNull(restPoint(overview))
        assertEquals(ProPromptSurface.CARD, policy.current.pending.getValue(milestone.id).surface)
        assertEquals(ActiveProPrompt(milestone, ProPromptSurface.CARD), restPoint(garage))
        assertTrue(events.none { it.second["surface"] == "sheet" })
    }

    @Test fun proOwnerSeesNothing() {
        access = ProAccess.PRO
        inSession(3)
        saved(SaveKind.SERVICE, 10)
        assertNull(restPoint(overview))
        assertNull(restPoint(garage))
        assertTrue(events.isEmpty())
    }

    // --- second_vehicle_added ---

    @Test fun secondActiveVehicleQueuesANextSessionGarageCard() {
        inSession(3)
        runBlocking { coordinator.onVehicleAdded(1) }
        assertTrue(policy.current.pending.isEmpty())
        runBlocking { coordinator.onVehicleAdded(2) }
        assertNull("next session, not this one", restPoint(garage))
        launch()
        assertEquals(ActiveProPrompt(secondVehicle, ProPromptSurface.CARD), restPoint(garage))
    }

    @Test fun thirdVehicleDoesNotQualifyAgain() {
        inSession(3)
        runBlocking { coordinator.onVehicleAdded(3) }
        assertTrue(policy.current.pending.isEmpty())
    }

    // --- Garage rest point: pending converted card, and screens without a slot ---

    @Test fun garageShowsAPendingConvertedCardAndALaterNoSlotScreenDefersIt() {
        access = ProAccess.REMOVE_ADS
        inSession(3)
        saved(SaveKind.EXPENSE, 10)
        // A screen with no card slot passes hasCardSlot = false: the converted card waits.
        assertNull(restPoint(overview))
        assertNotNull(policy.current.pending[milestone.id])
        // The Garage has the slot and shows it.
        assertEquals(ActiveProPrompt(milestone, ProPromptSurface.CARD), restPoint(garage))
        assertTrue(policy.current.pending.isEmpty())
    }

    @Test fun oneAutomaticPromptPerSession() {
        inSession(3)
        runBlocking { coordinator.onVehicleAdded(2) }
        saved(SaveKind.SERVICE, 10)
        launch()
        assertEquals(secondVehicle, restPoint(garage)?.trigger)
        assertNull(restPoint(garage))
        assertNull(restPoint(overview))
    }

    // --- Analytics ---

    @Test fun cardIsLoggedWithSurfaceCardAndEnumsOnly() {
        access = ProAccess.REMOVE_ADS
        inSession(3)
        saved(SaveKind.SERVICE, 10)
        val prompt = restPoint(garage)!!
        runBlocking { coordinator.onDismissed(prompt, ProPromptDismissMethod.CLOSE) }
        coordinator.onCtaTapped(prompt)
        assertEquals(
            listOf(
                "pro_prompt_shown" to mapOf("trigger" to "entries_milestone", "surface" to "card"),
                "pro_prompt_dismissed" to mapOf("trigger" to "entries_milestone", "surface" to "card", "method" to "close"),
                "pro_prompt_cta_tap" to mapOf("trigger" to "entries_milestone", "surface" to "card"),
            ),
            events,
        )
    }

    @Test fun sheetDismissalsCountTowardTheSheetLimitButCardDismissalsDoNot() {
        inSession(3)
        saved(SaveKind.SERVICE, 10)
        val sheet = restPoint(overview)!!
        runBlocking { coordinator.onDismissed(sheet, ProPromptDismissMethod.NOT_NOW) }
        assertEquals(1, policy.current.sheetDismissals)
        runBlocking { coordinator.onDismissed(ActiveProPrompt(secondVehicle, ProPromptSurface.CARD), ProPromptDismissMethod.CLOSE) }
        assertEquals(1, policy.current.sheetDismissals)
    }

    @Test fun paywallTriggersAreTheAutoEnums() {
        assertEquals("auto_entries_milestone", ProPromptCoordinator.paywallTrigger(milestone).analyticsValue)
        assertEquals("auto_second_vehicle_added", ProPromptCoordinator.paywallTrigger(secondVehicle).analyticsValue)
    }
}
