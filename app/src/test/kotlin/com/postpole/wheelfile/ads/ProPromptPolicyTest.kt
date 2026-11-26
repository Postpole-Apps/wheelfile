package com.postpole.wheelfile.ads

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProPromptPolicyTest {
    private val minute = 60L * 1_000
    private val hour = 60 * minute
    private val day = 24 * hour
    private val install = 1_700_000_000_000L
    private var now = install
    private val store = InMemoryProPromptStateStore()
    private val policy = ProPromptPolicy({ now }, store)
    private val config = ProPromptConfig()

    private val sheet = ProPromptSurface.SHEET
    private val card = ProPromptSurface.CARD
    private val milestone = ProPromptTrigger.ENTRIES_MILESTONE
    private val service = ProPromptTrigger.SERVICE_LOGGED
    private val secondVehicle = ProPromptTrigger.SECOND_VEHICLE_ADDED
    private val reports = ProPromptTrigger.REPORTS_3_MONTHS

    /** Cold start. */
    private fun launch() = runBlocking { policy.startSession(install) }

    /** Puts the policy in session [n] (≥ 4 days after install) with nothing shown yet. */
    private fun inSession(n: Int) {
        now = install + 4 * day
        repeat(n) { launch() }
        assertEquals(n, policy.current.sessionCount)
    }

    private fun qualify(t: ProPromptTrigger, s: ProPromptSurface, by: SaveKind? = null) =
        runBlocking { policy.qualify(t, s, by) }

    private fun eval(
        t: ProPromptTrigger,
        c: ProPromptConfig = config,
        access: ProAccess = ProAccess.FREE,
    ) = runBlocking { policy.evaluate(t, c, access) }

    private fun decision(t: ProPromptTrigger, c: ProPromptConfig = config, access: ProAccess = ProAccess.FREE) =
        eval(t, c, access).decision

    private fun shown(t: ProPromptTrigger, s: ProPromptSurface) = runBlocking { policy.onShown(t, s) }
    private fun dismissed(t: ProPromptTrigger, s: ProPromptSurface) = runBlocking { policy.onDismissed(t, s) }

    /** Show and dismiss [t] as [s] in a fresh session, 7+ (sheets) or 14+ (cards) days after the last one. */
    private fun showAndDismissInNewSession(t: ProPromptTrigger, s: ProPromptSurface) {
        now += 15 * day
        launch()
        qualify(t, s)
        assertEquals(ProPromptDecision.SHOW, decision(t))
        shown(t, s)
        dismissed(t, s)
    }

    @Test fun showsWhenEverythingIsClear() {
        inSession(3)
        qualify(milestone, sheet)
        assertEquals(ProPromptOutcome(ProPromptDecision.SHOW, sheet), eval(milestone))
    }

    // --- Threading ---

    @Test fun mainThreadFlagsSetDuringInFlightUpdates_areNotLost() = runBlocking {
        // The clock and the store are called while a coroutine update is in flight; use them to inject the
        // main-thread calls (AdsPolicy -> onInterstitialShown / onSaved) at that moment.
        var inject: (() -> Unit)? = null
        val racingStore = object : ProPromptStateStore {
            override suspend fun load() = ProPromptState()
            override suspend fun save(state: ProPromptState) {
                inject?.also { inject = null }?.invoke()
            }
        }
        val racing = ProPromptPolicy({ inject?.also { inject = null }?.invoke(); now }, racingStore)
        now = install + 4 * day
        racing.startSession(install)
        racing.startSession(install)

        racing.qualify(milestone, sheet)
        inject = { racing.onInterstitialShown() }
        racing.onShown(milestone, sheet)
        assertTrue(racing.current.interstitialThisSession)
        assertTrue(racing.current.proPromptThisSession)

        inject = { racing.onSaved(SaveKind.FILL_UP) }
        racing.onDismissed(milestone, sheet)
        assertTrue(racing.current.pumpSession)
        assertTrue(racing.current.interstitialThisSession)
    }

    @Test fun nothingPending_noPrompt() {
        inSession(3)
        assertEquals(ProPromptDecision.NOT_PENDING, decision(milestone))
    }

    // --- Sessions ---

    @Test fun firstSession_neverPrompts() {
        now = install + 10 * day
        launch()
        qualify(milestone, card)
        assertEquals(ProPromptDecision.FIRST_SESSION, decision(milestone))
    }

    @Test fun returnAfter29Minutes_isTheSameSession() = runBlocking {
        inSession(2)
        policy.onBackgrounded()
        now += 29 * minute
        assertFalse(policy.onForegrounded())
        assertEquals(2, policy.current.sessionCount)
    }

    @Test fun returnAfter30Minutes_isANewSession() = runBlocking {
        inSession(2)
        policy.onBackgrounded()
        now += 30 * minute
        assertTrue(policy.onForegrounded())
        assertEquals(3, policy.current.sessionCount)
    }

    @Test fun newSession_resetsSessionFlags() = runBlocking {
        inSession(3)
        qualify(milestone, sheet)
        shown(milestone, sheet)
        policy.onBackgrounded()
        now += 30 * minute
        policy.onForegrounded()
        assertFalse(policy.proPromptShownThisSession)
    }

    @Test fun sessionCounterAndInstallTimeSurviveRestart() {
        inSession(3)
        val restarted = ProPromptPolicy({ now }, store)
        runBlocking { restarted.startSession(installTimeMs = 5L) }
        assertEquals(4, restarted.current.sessionCount)
        assertEquals(install, restarted.current.installTimeMs)
    }

    // --- Install age ---

    @Test fun firstThreeDays_noPrompt_dayFourAllowed() {
        now = install
        launch()
        launch()
        launch()
        qualify(milestone, card)
        now = install + 3 * day - 1
        assertEquals(ProPromptDecision.TOO_SOON_AFTER_INSTALL, decision(milestone))
        now = install + 3 * day
        assertEquals(ProPromptDecision.SHOW, decision(milestone))
    }

    // --- Sheet session floor ---

    @Test fun sheet_needsMinSession_cardDoesNot() {
        inSession(2)
        qualify(milestone, sheet)
        qualify(reports, card)
        assertEquals(ProPromptDecision.MIN_SESSION, decision(milestone))
        assertEquals(ProPromptDecision.SHOW, decision(reports))
    }

    @Test fun minSession_remoteConfigCanRaiseButNotGoBelowTwo() {
        inSession(3)
        qualify(milestone, sheet)
        assertEquals(ProPromptDecision.MIN_SESSION, decision(milestone, config.copy(minSession = 4)))
        assertEquals(ProPromptDecision.SHOW, decision(milestone, config.copy(minSession = 0)))
        assertEquals(2, config.copy(minSession = 0).coerced().minSession)
        assertEquals(2, config.copy(minSession = -5).coerced().minSession)
    }

    @Test fun minSessionTwo_allowsSheetInSessionTwo() {
        inSession(2)
        qualify(milestone, sheet)
        assertEquals(ProPromptDecision.SHOW, decision(milestone, config.copy(minSession = 2)))
    }

    // --- Per-session and 7-day caps ---

    @Test fun oneAutomaticPromptPerSession() {
        inSession(3)
        qualify(milestone, sheet)
        qualify(reports, card)
        shown(milestone, sheet)
        assertEquals(ProPromptDecision.SESSION_CAP, decision(reports))
    }

    @Test fun sheetThenSheet_within7Days_blocked() {
        inSession(3)
        qualify(milestone, sheet)
        shown(milestone, sheet)
        now += 7 * day - 1
        launch()
        qualify(service, sheet)
        assertEquals(ProPromptDecision.SOFT_WINDOW, decision(service))
        now += 1
        assertEquals(ProPromptDecision.SHOW, decision(service))
    }

    @Test fun softWindow_coversSheetsAndCardsBothWays() {
        inSession(3)
        qualify(milestone, sheet)
        shown(milestone, sheet)
        now += 3 * day
        launch()
        qualify(reports, card)
        assertEquals(ProPromptDecision.SOFT_WINDOW, decision(reports))

        // And a card blocks a later sheet.
        now += 20 * day
        launch()
        qualify(secondVehicle, card)
        shown(secondVehicle, card)
        now += 6 * day
        launch()
        qualify(service, sheet)
        assertEquals(ProPromptDecision.SOFT_WINDOW, decision(service))
    }

    @Test fun promptNotShown_doesNotUseTheCap() {
        inSession(3)
        qualify(milestone, sheet)
        assertEquals(ProPromptDecision.SHOW, decision(milestone))
        assertEquals(ProPromptDecision.SHOW, decision(milestone)) // evaluating again changes nothing
        assertFalse(policy.proPromptShownThisSession)
        assertEquals(null, policy.current.lastSoftShownAt)
    }

    // --- Dismissals ---

    @Test fun cardReshow_every14DaysAfterDismissal() {
        inSession(3)
        qualify(reports, card)
        shown(reports, card)
        dismissed(reports, card)
        now += 14 * day - 1
        launch()
        qualify(reports, card)
        assertEquals(ProPromptDecision.CARD_RESHOW_WINDOW, decision(reports))
        now += 1
        assertEquals(ProPromptDecision.SHOW, decision(reports))
    }

    @Test fun threeSheetDismissalsInTotal_stopAllSheets() {
        inSession(3)
        qualify(milestone, sheet)
        shown(milestone, sheet)
        dismissed(milestone, sheet)
        showAndDismissInNewSession(service, sheet)
        showAndDismissInNewSession(secondVehicle, sheet) // three different triggers, three dismissals
        now += 15 * day
        launch()
        qualify(reports, sheet)
        assertEquals(ProPromptDecision.SHEET_DISMISS_LIMIT, decision(reports))
        // Cards are unaffected by the sheet total.
        qualify(milestone, card)
        assertEquals(ProPromptDecision.SHOW, decision(milestone))
    }

    @Test fun twoSheetDismissals_stillAllowed() {
        inSession(3)
        qualify(milestone, sheet)
        shown(milestone, sheet)
        dismissed(milestone, sheet)
        showAndDismissInNewSession(service, sheet)
        now += 15 * day
        launch()
        qualify(reports, sheet)
        assertEquals(ProPromptDecision.SHOW, decision(reports))
    }

    @Test fun cardTrigger_stopsAfterThreeOfItsOwnDismissals() {
        inSession(3)
        qualify(reports, card)
        shown(reports, card)
        dismissed(reports, card)
        showAndDismissInNewSession(reports, card)
        showAndDismissInNewSession(reports, card)
        now += 15 * day
        launch()
        qualify(reports, card)
        assertEquals(ProPromptDecision.CARD_DISMISS_LIMIT, decision(reports))
        // A different card trigger is independent.
        qualify(secondVehicle, card)
        assertEquals(ProPromptDecision.SHOW, decision(secondVehicle))
    }

    @Test fun cardDismissals_doNotCountTowardSheetTotal() {
        inSession(3)
        qualify(reports, card)
        shown(reports, card)
        dismissed(reports, card)
        showAndDismissInNewSession(reports, card)
        showAndDismissInNewSession(reports, card)
        assertEquals(0, policy.current.sheetDismissals)
        now += 15 * day
        launch()
        qualify(milestone, sheet)
        assertEquals(ProPromptDecision.SHOW, decision(milestone))
    }

    // --- Deferral ---

    @Test fun deferred_showsAtALaterRestPoint_andIsDroppedAfter7Days() {
        inSession(3)
        qualify(milestone, sheet)
        shown(service, sheet) // something else used the session
        assertEquals(ProPromptDecision.SESSION_CAP, decision(milestone))
        now += 7 * day
        launch() // exactly 7 days is still inside the window
        assertEquals(ProPromptDecision.SHOW, decision(milestone))
    }

    @Test fun deferred_droppedAfterMoreThan7Days() {
        inSession(3)
        qualify(milestone, sheet)
        now += 7 * day + 1
        launch()
        assertEquals(ProPromptDecision.EXPIRED, decision(milestone))
        assertEquals(ProPromptDecision.NOT_PENDING, decision(milestone))
    }

    @Test fun qualifyingAgain_doesNotPostponeTheDrop() {
        inSession(3)
        qualify(milestone, sheet)
        now += 6 * day
        qualify(milestone, sheet)
        now += day + 1
        assertEquals(ProPromptDecision.EXPIRED, decision(milestone))
    }

    @Test fun pendingSurvivesRestart() {
        inSession(3)
        qualify(milestone, sheet)
        val restarted = ProPromptPolicy({ now }, store)
        runBlocking { restarted.startSession(install) }
        val outcome = runBlocking { restarted.evaluate(milestone, config, ProAccess.FREE) }
        assertEquals(ProPromptDecision.SHOW, outcome.decision)
    }

    // --- Pump-session ---

    @Test fun pumpSession_blocksEverything() {
        inSession(3)
        qualify(reports, card)
        policy.onSaved(SaveKind.FILL_UP)
        assertEquals(ProPromptDecision.PUMP_SESSION, decision(reports))
        policy.onSaved(SaveKind.SERVICE) // saves that are not pump saves don't matter
    }

    @Test fun evCharge_isAlsoAPumpSession() {
        inSession(3)
        qualify(reports, card)
        policy.onSaved(SaveKind.EV_CHARGE)
        assertEquals(ProPromptDecision.PUMP_SESSION, decision(reports))
    }

    @Test fun serviceSave_isNotAPumpSession() {
        inSession(3)
        qualify(service, sheet)
        policy.onSaved(SaveKind.SERVICE)
        assertEquals(ProPromptDecision.SHOW, decision(service))
    }

    @Test fun promptEarnedByFillUp_isACardInTheNextSession() {
        inSession(3)
        qualify(milestone, sheet, by = SaveKind.FILL_UP)
        policy.onSaved(SaveKind.FILL_UP)
        assertEquals(ProPromptDecision.PUMP_SESSION, decision(milestone))
        launch()
        assertEquals(ProPromptOutcome(ProPromptDecision.SHOW, card), eval(milestone))
    }

    @Test fun promptEarnedByCharge_isACardInTheNextSession_evenWithoutPumpFlag() {
        inSession(3)
        qualify(milestone, sheet, by = SaveKind.EV_CHARGE)
        assertEquals(ProPromptDecision.NEXT_SESSION, decision(milestone))
        launch()
        assertEquals(ProPromptOutcome(ProPromptDecision.SHOW, card), eval(milestone))
    }

    @Test fun pumpBlockedServiceSheet_becomesNextSessionCard() {
        inSession(3)
        policy.onSaved(SaveKind.FILL_UP)
        qualify(service, sheet) // service logged later in a pump session
        assertEquals(ProPromptOutcome(ProPromptDecision.PUMP_SESSION, card), eval(service))
        launch()
        assertEquals(ProPromptOutcome(ProPromptDecision.SHOW, card), eval(service))
    }

    @Test fun pumpBlockedMilestoneSheet_becomesNextSessionCard() {
        inSession(3)
        policy.onSaved(SaveKind.EV_CHARGE)
        qualify(milestone, sheet, by = SaveKind.EXPENSE) // 10th entry was an expense
        assertEquals(ProPromptDecision.PUMP_SESSION, decision(milestone))
        launch()
        assertEquals(card, eval(milestone).surface)
        assertTrue(eval(milestone).shouldShow)
    }

    @Test fun pumpBlockedSheet_staysBlockedForTheRestOfTheSession() {
        inSession(3)
        policy.onSaved(SaveKind.FILL_UP)
        qualify(milestone, sheet)
        eval(milestone)
        policy.onSaved(SaveKind.FILL_UP)
        assertEquals(ProPromptDecision.PUMP_SESSION, decision(milestone))
    }

    // --- Hard gate ---

    @Test fun hardGate_48HoursSkip() {
        inSession(3)
        qualify(milestone, sheet)
        runBlocking { policy.onHardGateShown() }
        now += 48 * hour - 1
        assertEquals(ProPromptDecision.HARD_GATE_RECENT, decision(milestone))
        now += 1
        assertEquals(ProPromptDecision.SHOW, decision(milestone))
    }

    // --- Ownership ---

    @Test fun proOwner_neverPrompted() {
        inSession(3)
        qualify(milestone, sheet)
        qualify(reports, card)
        assertEquals(ProPromptDecision.PRO_OWNER, decision(milestone, access = ProAccess.PRO))
        assertEquals(ProPromptDecision.PRO_OWNER, decision(reports, access = ProAccess.PRO))
    }

    private val removeAds = ProAccess.REMOVE_ADS

    @Test fun proOwner_getsNothing_evenWithoutSlot() {
        inSession(3)
        qualify(milestone, sheet)
        val outcome = runBlocking { policy.evaluate(milestone, config, ProAccess.PRO, hasCardSlot = false) }
        assertEquals(ProPromptDecision.PRO_OWNER, outcome.decision)
    }

    @Test fun removeAdsOwner_sheetTrigger_showsCardWithSameTrigger() {
        inSession(3)
        qualify(milestone, sheet)
        assertEquals(ProPromptOutcome(ProPromptDecision.SHOW, card), eval(milestone, access = removeAds))
    }

    @Test fun removeAdsOwner_noSlot_deferredToGarage_thenDroppedAfter7Days() {
        inSession(3)
        qualify(milestone, sheet)
        val noSlot = runBlocking { policy.evaluate(milestone, config, removeAds, hasCardSlot = false) }
        assertEquals(ProPromptOutcome(ProPromptDecision.NO_CARD_SLOT, card), noSlot)
        now += 7 * day
        assertEquals(ProPromptOutcome(ProPromptDecision.SHOW, card), eval(milestone, access = removeAds))
        now += 1
        assertEquals(ProPromptDecision.EXPIRED, decision(milestone, access = removeAds))
        assertEquals(ProPromptDecision.NOT_PENDING, decision(milestone, access = removeAds))
    }

    @Test fun removeAdsOwner_convertedCard_followsCardCaps_andLeavesSheetCountersAlone() {
        inSession(3)
        qualify(milestone, sheet)
        assertEquals(card, eval(milestone, access = removeAds).surface)
        shown(milestone, card)
        dismissed(milestone, card)
        // 1 auto prompt per 7 days.
        now += 6 * day
        launch()
        qualify(service, sheet)
        assertEquals(ProPromptDecision.SOFT_WINDOW, decision(service, access = removeAds))
        now += day
        assertEquals(ProPromptDecision.SHOW, decision(service, access = removeAds))
        // Three dismissals stop that trigger.
        showAndDismissInNewSession(milestone, card)
        showAndDismissInNewSession(milestone, card)
        now += 15 * day
        launch()
        qualify(milestone, sheet)
        assertEquals(ProPromptDecision.CARD_DISMISS_LIMIT, decision(milestone, access = removeAds))
        assertEquals(0, policy.current.sheetDismissals)
        assertTrue(policy.current.shownCounts.keys.none { it.endsWith(":sheet") })
        assertTrue(policy.current.lastShownAt.keys.none { it.endsWith(":sheet") })
    }

    @Test fun removeAdsOwner_stillSubjectToSessionOneAndHardGate() {
        now = install + 10 * day
        launch()
        qualify(milestone, sheet)
        assertEquals(ProPromptDecision.FIRST_SESSION, decision(milestone, access = removeAds))
        launch()
        runBlocking { policy.onHardGateShown() }
        assertEquals(ProPromptDecision.HARD_GATE_RECENT, decision(milestone, access = removeAds))
    }

    @Test fun removeAdsOwner_oneAutoPromptPerSession() {
        inSession(3)
        qualify(milestone, sheet)
        qualify(reports, card)
        shown(reports, card)
        assertEquals(ProPromptDecision.SESSION_CAP, decision(milestone, access = removeAds))
    }

    @Test fun nonOwner_stillGetsTheSheet() {
        inSession(3)
        qualify(milestone, sheet)
        assertEquals(ProPromptOutcome(ProPromptDecision.SHOW, sheet), eval(milestone))
    }

    // --- Remote Config ---

    @Test fun killSwitch_turnsAutoPromptsOff_andKeepsPendingForWhenItIsBackOn() {
        inSession(3)
        qualify(milestone, sheet)
        assertEquals(ProPromptDecision.DISABLED_BY_CONFIG, decision(milestone, config.copy(enabled = false)))
        assertEquals(ProPromptDecision.SHOW, decision(milestone))
    }

    @Test fun entriesThreshold_boundsAre5to25() {
        assertEquals(10, ProPromptConfig().entriesThreshold)
        assertEquals(5, ProPromptConfig(entriesThreshold = 1).coerced().entriesThreshold)
        assertEquals(5, ProPromptConfig(entriesThreshold = 5).coerced().entriesThreshold)
        assertEquals(25, ProPromptConfig(entriesThreshold = 25).coerced().entriesThreshold)
        assertEquals(25, ProPromptConfig(entriesThreshold = 99).coerced().entriesThreshold)
    }

    @Test fun configDefaults() {
        val c = ProPromptConfig()
        assertTrue(c.enabled)
        assertEquals(3, c.minSession)
        assertEquals(10, c.entriesThreshold)
    }

    // --- Storage ---

    @Test fun dataStoreCodec_roundTrips() {
        val pending = mapOf("entries_milestone" to PendingProPrompt(card, 42L, 4))
        val encoded = DataStoreProPromptStateStore.encodePending(pending)
        assertEquals(pending, DataStoreProPromptStateStore.decodePending(encoded))
        val counts = mapOf("service_logged:sheet" to 2, "reports_3_months:card" to 1)
        assertEquals(
            counts,
            DataStoreProPromptStateStore.decodeMap(DataStoreProPromptStateStore.encodeMap(counts)) { it.toIntOrNull() },
        )
        assertEquals(emptyMap<String, PendingProPrompt>(), DataStoreProPromptStateStore.decodePending(null))
        assertEquals(emptyMap<String, Int>(), DataStoreProPromptStateStore.decodeMap(null) { it.toIntOrNull() })
    }
}
