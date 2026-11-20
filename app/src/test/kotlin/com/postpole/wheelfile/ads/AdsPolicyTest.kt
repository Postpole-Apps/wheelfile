package com.postpole.wheelfile.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class AdsPolicyTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val day = TimeUnit.DAYS.toMillis(1)
    private val config = AdsConfig.DEFAULT

    /** Noon UTC, so adding a few hours never crosses midnight. */
    private var now = LocalDateTime.of(2026, 11, 20, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun policy(
        installedDaysAgo: Long = 10,
        firstSession: Boolean = false,
        history: AdsHistory = AdsHistory(installTimeMs = now - installedDaysAgo * day),
    ) = AdsPolicy(history, firstSession, clock = { now }, zone = zone)

    @Test fun allowed_whenEveryRuleIsMet() {
        assertTrue(policy().canShowInterstitial(config))
    }

    @Test fun firstSession_isBlocked() {
        assertFalse(policy(installedDaysAgo = 10, firstSession = true).canShowInterstitial(config))
    }

    @Test fun firstSession_notBlockedWhenRemoteConfigAllows() {
        val open = config.copy(interstitialBlockFirstSession = false, interstitialMinInstallDays = 0)
        assertTrue(policy(installedDaysAgo = 0, firstSession = true).canShowInterstitial(open))
    }

    @Test fun minInstallDays_isThree() {
        assertFalse(policy(installedDaysAgo = 0).canShowInterstitial(config))
        assertFalse(policy(installedDaysAgo = 2).canShowInterstitial(config))
        assertTrue(policy(installedDaysAgo = 3).canShowInterstitial(config))
    }

    @Test fun minInterval_is480Seconds() {
        val loose = config.copy(interstitialMaxPerSession = 10)
        val p = policy()
        p.recordInterstitialShown()
        now += TimeUnit.SECONDS.toMillis(479)
        assertFalse(p.canShowInterstitial(loose))
        now += TimeUnit.SECONDS.toMillis(1)
        assertTrue(p.canShowInterstitial(loose))
    }

    @Test fun maxOnePerSession() {
        val p = policy()
        p.recordInterstitialShown()
        now += TimeUnit.HOURS.toMillis(2)
        assertFalse(p.canShowInterstitial(config))
        // A new session (new policy, restored history) may show again.
        assertTrue(policy(history = p.history).canShowInterstitial(config))
    }

    @Test fun maxThreePerDay() {
        var history = AdsHistory(installTimeMs = now - 10 * day)
        repeat(3) {
            val session = policy(history = history)
            assertTrue("show #${it + 1}", session.canShowInterstitial(config))
            session.recordInterstitialShown()
            history = session.history
            now += TimeUnit.MINUTES.toMillis(30)
        }
        assertEquals(3, history.shownOnDay)
        assertFalse(policy(history = history).canShowInterstitial(config))
    }

    @Test fun dailyCount_resetsTheNextDay() {
        var history = AdsHistory(installTimeMs = now - 10 * day)
        repeat(3) {
            val session = policy(history = history)
            session.recordInterstitialShown()
            history = session.history
            now += TimeUnit.MINUTES.toMillis(30)
        }
        now += day
        assertTrue(policy(history = history).canShowInterstitial(config))
    }

    @Test fun fillUpSaved_suppressesTheRestOfTheSession() {
        val p = policy()
        p.onEvent(SessionEvent.FILL_UP_SAVED)
        assertFalse(p.canShowInterstitial(config))
        now += TimeUnit.DAYS.toMillis(1)
        assertFalse(p.canShowInterstitial(config))
    }

    @Test fun chargeSaved_suppressesTheRestOfTheSession() {
        val p = policy()
        p.onEvent(SessionEvent.CHARGE_SAVED)
        assertFalse(p.canShowInterstitial(config))
    }

    @Test fun serviceAndExpenseSaves_doNotSuppress() {
        val p = policy()
        p.onEvent(SessionEvent.SERVICE_SAVED)
        p.onEvent(SessionEvent.EXPENSE_SAVED)
        assertTrue(p.canShowInterstitial(config))
    }

    @Test fun noInterstitialAfterExportBackupRestoreOrPdfShare() {
        listOf(
            SessionEvent.EXPORTED,
            SessionEvent.BACKED_UP,
            SessionEvent.RESTORED,
            SessionEvent.PDF_SHARED,
        ).forEach { event ->
            val p = policy()
            p.onEvent(event)
            assertFalse("after $event", p.canShowInterstitial(config))
        }
    }

    @Test fun suppression_doesNotCarryToTheNextSession() {
        val first = policy()
        first.onEvent(SessionEvent.FILL_UP_SAVED)
        assertTrue(policy(history = first.history).canShowInterstitial(config))
    }

    @Test fun remoteConfigSwitches_turnInterstitialsOff() {
        assertFalse(policy().canShowInterstitial(config.copy(adsEnabled = false)))
        assertFalse(policy().canShowInterstitial(config.copy(interstitialEnabled = false)))
    }
}
