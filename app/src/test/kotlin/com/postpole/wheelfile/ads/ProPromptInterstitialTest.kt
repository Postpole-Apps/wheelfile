package com.postpole.wheelfile.ads

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

/** Automatic Pro prompts and interstitials never share a session (spec v0.2.2 §5.2 / §5.4a). */
class ProPromptInterstitialTest {
    private val day = 24L * 60 * 60 * 1_000
    private val install = 1_700_000_000_000L
    private var now = install + 10 * day
    private val prompts = ProPromptPolicy({ now }, InMemoryProPromptStateStore())
    private val ads = AdsPolicy(
        clock = { now },
        installTimeMs = install,
        isFirstSession = false,
        zone = ZoneOffset.UTC,
        proPrompts = prompts,
    )
    private val adsConfig = AdsConfig.DEFAULTS

    private fun startSession() = runBlocking { prompts.startSession(install) }

    private fun interstitial() = ads.evaluate(adsConfig, adsHidden = false, canRequestAds = true)

    private fun promptDecision(trigger: ProPromptTrigger = ProPromptTrigger.ENTRIES_MILESTONE) =
        runBlocking { prompts.evaluate(trigger, ProPromptConfig(), ProAccess.FREE) }.decision

    private fun qualify(trigger: ProPromptTrigger = ProPromptTrigger.ENTRIES_MILESTONE) =
        runBlocking { prompts.qualify(trigger, ProPromptSurface.SHEET) }

    private fun showPrompt(trigger: ProPromptTrigger = ProPromptTrigger.ENTRIES_MILESTONE) =
        runBlocking { prompts.onShown(trigger, ProPromptSurface.SHEET) }

    private fun readySession() {
        repeat(3) { startSession() }
    }

    @Test fun interstitialAllowedWhenNoPromptWasShown() {
        readySession()
        assertEquals(InterstitialDecision.ALLOWED, interstitial())
    }

    @Test fun promptShown_interstitialSkippedForTheRestOfTheSession() {
        readySession()
        qualify()
        showPrompt()
        assertEquals(InterstitialDecision.PRO_PROMPT_SESSION, interstitial())
        now += 60 * 60 * 1_000 // still the same session, however much later
        assertEquals(InterstitialDecision.PRO_PROMPT_SESSION, interstitial())
    }

    @Test fun interstitialShown_noPromptThatSession() {
        readySession()
        qualify()
        ads.onInterstitialShown()
        assertEquals(ProPromptDecision.INTERSTITIAL_SESSION, promptDecision())
    }

    @Test fun bothEligible_promptWins_interstitialSkipped() {
        readySession()
        qualify()
        assertEquals(InterstitialDecision.ALLOWED, interstitial())
        assertEquals(ProPromptDecision.SHOW, promptDecision())
        // The prompt is shown first, as the spec requires, and that settles the session.
        showPrompt()
        assertEquals(InterstitialDecision.PRO_PROMPT_SESSION, interstitial())
        assertEquals(false, ads.canShowInterstitial(adsConfig, adsHidden = false, canRequestAds = true))
    }

    @Test fun nextSession_bothMayShowAgain() {
        readySession()
        qualify()
        showPrompt()
        startSession()
        assertEquals(InterstitialDecision.ALLOWED, interstitial())
    }

    @Test fun interstitialInEarlierSession_doesNotBlockLaterPrompt() {
        readySession()
        ads.onInterstitialShown()
        startSession()
        qualify()
        assertEquals(ProPromptDecision.SHOW, promptDecision())
    }

    @Test fun deferredPromptKeepsItsCap_whenInterstitialTookTheSession() {
        readySession()
        qualify()
        ads.onInterstitialShown()
        assertEquals(ProPromptDecision.INTERSTITIAL_SESSION, promptDecision())
        assertEquals(null, prompts.current.lastSoftShownAt)
        startSession()
        assertEquals(ProPromptDecision.SHOW, promptDecision())
    }

    @Test fun savesAreForwardedForThePumpSessionRule() {
        readySession()
        qualify()
        ads.onSaved(SaveKind.FILL_UP)
        assertEquals(ProPromptDecision.PUMP_SESSION, promptDecision())
    }
}
