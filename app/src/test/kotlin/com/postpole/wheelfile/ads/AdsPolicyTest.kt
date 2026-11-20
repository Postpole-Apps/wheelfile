package com.postpole.wheelfile.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AdsPolicyTest {
    private val hour = 60L * 60 * 1_000
    private val day = 24 * hour
    private val install = 1_700_000_000_000L
    private var now = install + 10 * day
    private val config = AdsConfig.DEFAULTS

    private fun policy(
        firstSession: Boolean = false,
        history: InterstitialHistory = InMemoryInterstitialHistory(),
    ) = AdsPolicy(
        clock = { now },
        installTimeMs = install,
        isFirstSession = firstSession,
        history = history,
        zone = ZoneOffset.UTC,
    )

    private fun AdsPolicy.decision(c: AdsConfig = config) =
        evaluate(c, adsHidden = false, canRequestAds = true)

    @Test fun allowedWhenEverythingIsClear() {
        assertTrue(policy().canShowInterstitial(config, adsHidden = false, canRequestAds = true))
    }

    @Test fun firstSession_isBlocked() {
        assertEquals(InterstitialDecision.FIRST_SESSION, policy(firstSession = true).decision())
    }

    @Test fun firstSession_notBlockedWhenRemoteConfigTurnsTheRuleOff() {
        val c = config.copy(interstitialBlockFirstSession = false, interstitialMinInstallDays = 0)
        assertEquals(InterstitialDecision.ALLOWED, policy(firstSession = true).decision(c))
    }

    @Test fun minInstallDays_threeDays() {
        now = install + 3 * day - 1
        assertEquals(InterstitialDecision.TOO_SOON_AFTER_INSTALL, policy().decision())
        now = install + 3 * day
        assertEquals(InterstitialDecision.ALLOWED, policy().decision())
    }

    @Test fun minInterval_480Seconds() {
        val history = InMemoryInterstitialHistory(listOf(now - 479_000))
        assertEquals(InterstitialDecision.MIN_INTERVAL, policy(history = history).decision())
        now += 1_000
        assertEquals(InterstitialDecision.ALLOWED, policy(history = history).decision())
    }

    @Test fun onePerSession() {
        val p = policy()
        p.onInterstitialShown()
        now += 600_000 // past the interval, same session
        assertEquals(InterstitialDecision.SESSION_CAP, p.decision())
    }

    @Test fun threePerDay_acrossSessions() {
        val dayStart = (now / day) * day
        val history = InMemoryInterstitialHistory(listOf(dayStart + hour, dayStart + 3 * hour, dayStart + 5 * hour))
        now = dayStart + 9 * hour
        assertEquals(InterstitialDecision.DAY_CAP, policy(history = history).decision())
        // The next day the cap resets.
        now = dayStart + day + hour
        assertEquals(InterstitialDecision.ALLOWED, policy(history = history).decision())
    }

    @Test fun fillUp_suppressesRestOfSession() {
        val p = policy()
        p.onSaved(SaveKind.FILL_UP)
        assertEquals(InterstitialDecision.PUMP_SESSION, p.decision())
        now += day
        assertEquals(InterstitialDecision.PUMP_SESSION, p.decision())
    }

    @Test fun evCharge_suppressesRestOfSession() {
        val p = policy()
        p.onSaved(SaveKind.EV_CHARGE)
        assertEquals(InterstitialDecision.PUMP_SESSION, p.decision())
    }

    @Test fun serviceAndExpense_doNotSuppress() {
        val p = policy()
        p.onSaved(SaveKind.SERVICE)
        p.onSaved(SaveKind.EXPENSE)
        assertEquals(InterstitialDecision.ALLOWED, p.decision())
    }

    @Test fun newSession_clearsPumpRule() {
        policy().onSaved(SaveKind.FILL_UP)
        assertEquals(InterstitialDecision.ALLOWED, policy().decision())
    }

    @Test fun noInterstitialAfterExportBackupRestoreOrPdfShare() {
        QuietAction.entries.forEach { action ->
            val p = policy()
            p.onQuietAction(action)
            assertEquals("after $action", InterstitialDecision.QUIET_ACTION, p.decision())
        }
    }

    @Test fun adsHiddenConsentAndConfigAlwaysWin() {
        val p = policy()
        assertEquals(InterstitialDecision.ADS_HIDDEN, p.evaluate(config, adsHidden = true, canRequestAds = true))
        assertEquals(InterstitialDecision.CONSENT, p.evaluate(config, adsHidden = false, canRequestAds = false))
        assertEquals(
            InterstitialDecision.DISABLED_BY_CONFIG,
            p.evaluate(config.copy(interstitialEnabled = false), adsHidden = false, canRequestAds = true),
        )
        assertEquals(
            InterstitialDecision.DISABLED_BY_CONFIG,
            p.evaluate(config.copy(adsEnabled = false), adsHidden = false, canRequestAds = true),
        )
    }

    @Test fun shownInterstitialIsRecordedForTheNextSession() {
        val history = InMemoryInterstitialHistory()
        val p = policy(history = history)
        p.onInterstitialShown()
        assertFalse(history.shownAt().isEmpty())
        now += 60_000
        assertEquals(InterstitialDecision.MIN_INTERVAL, policy(history = history).decision())
    }
}
