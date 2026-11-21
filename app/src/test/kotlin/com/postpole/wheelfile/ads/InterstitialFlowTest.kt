package com.postpole.wheelfile.ads

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset

/**
 * `wf_interstitial_after_review`: only when going back from Vehicle detail to the Garage after viewing History or
 * Reports, with every Remote Config cap, the first-session block and the pump-session rule (spec §5.2).
 */
@RunWith(RobolectricTestRunner::class)
class InterstitialFlowTest {
    private val hour = 60L * 60 * 1_000
    private val day = 24 * hour
    private val install = 1_700_000_000_000L
    private var now = install + 10 * day
    private var config = AdsConfig.DEFAULTS
    private var adsHidden = false
    private var canRequestAds = true
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    private class FakeGateway(var ready: Boolean = true) : InterstitialGateway {
        var shows = 0
        var preloads = 0
        override val isReady: Boolean get() = ready
        override fun preload() {
            preloads++
        }
        override fun show(activity: Activity): Boolean {
            if (!ready) return false
            shows++
            ready = false
            return true
        }
    }

    private val gateway = FakeGateway()

    private fun newPolicy(
        firstSession: Boolean = false,
        history: InterstitialHistory = InMemoryInterstitialHistory(),
    ) = AdsPolicy({ now }, install, firstSession, history, ZoneOffset.UTC)

    private var policy = newPolicy()

    private fun coordinator() = InterstitialCoordinator(policy, gateway, { config }, { adsHidden }, { canRequestAds })

    /** The user opened History (or Reports) and went back to the Garage. */
    private fun InterstitialCoordinator.backFromReview(review: Boolean = true): Boolean {
        policy.onLeftVehicleDetail(viewedReview = review)
        return onBackToGarage(activity)
    }

    @Test fun backFromHistoryOrReports_showsOnce() {
        val c = coordinator()
        assertTrue(c.backFromReview())
        assertEquals(1, gateway.shows)
    }

    @Test fun backFromOverviewOnly_neverShows() {
        val c = coordinator()
        assertFalse(c.backFromReview(review = false))
        assertEquals(0, gateway.shows)
    }

    @Test fun backWithoutLeavingDetail_neverShows() {
        assertFalse(coordinator().onBackToGarage(activity))
        assertEquals(0, gateway.shows)
    }

    @Test fun fastNavigation_neverBeatsTheCaps() {
        val c = coordinator()
        assertTrue(c.backFromReview())
        repeat(5) {
            gateway.ready = true
            now += 1_000
            assertFalse(c.backFromReview())
        }
        assertEquals(1, gateway.shows)
    }

    @Test fun onePerSession_evenAfterTheIntervalPasses() {
        val c = coordinator()
        assertTrue(c.backFromReview())
        gateway.ready = true
        now += 600_000
        assertFalse(c.backFromReview())
    }

    @Test fun threePerDay_acrossSessions() {
        val history = InMemoryInterstitialHistory(listOf(now - 3 * hour, now - 2 * hour, now - hour))
        policy = newPolicy(history = history)
        assertFalse(coordinator().backFromReview())
        assertEquals(0, gateway.shows)
    }

    @Test fun minInterval_480Seconds_acrossSessions() {
        policy = newPolicy(history = InMemoryInterstitialHistory(listOf(now - 479_000)))
        assertFalse(coordinator().backFromReview())
        now += 1_000
        assertTrue(coordinator().backFromReview())
    }

    @Test fun pumpSession_fillUpBlocksTheRestOfTheSession() {
        val c = coordinator()
        policy.onSaved(SaveKind.FILL_UP)
        assertFalse(c.backFromReview())
        assertEquals(0, gateway.shows)
    }

    @Test fun pumpSession_evChargeBlocksToo() {
        val c = coordinator()
        policy.onSaved(SaveKind.EV_CHARGE)
        assertFalse(c.backFromReview())
    }

    @Test fun serviceAndExpenseSaves_doNotStartAPumpSession() {
        val c = coordinator()
        policy.onSaved(SaveKind.SERVICE)
        policy.onSaved(SaveKind.EXPENSE)
        assertTrue(c.backFromReview())
    }

    @Test fun afterExportBackupRestoreOrPdf_noInterstitialThisSession() {
        QuietAction.entries.forEach { action ->
            policy = newPolicy()
            gateway.shows = 0
            gateway.ready = true
            policy.onQuietAction(action)
            assertFalse(action.name, coordinator().backFromReview())
            assertEquals(0, gateway.shows)
        }
    }

    @Test fun firstSession_isBlocked() {
        policy = newPolicy(firstSession = true)
        assertFalse(coordinator().backFromReview())
    }

    @Test fun beforeThreeInstallDays_isBlocked() {
        now = install + 3 * day - 1
        assertFalse(coordinator().backFromReview())
        now = install + 3 * day
        assertTrue(coordinator().backFromReview())
    }

    @Test fun adsHiddenConsentAndRemoteConfig_eachBlock() {
        adsHidden = true
        assertFalse(coordinator().backFromReview())
        adsHidden = false
        canRequestAds = false
        assertFalse(coordinator().backFromReview())
        canRequestAds = true
        config = config.copy(interstitialEnabled = false)
        assertFalse(coordinator().backFromReview())
        config = AdsConfig.DEFAULTS.copy(adsEnabled = false)
        assertFalse(coordinator().backFromReview())
        assertEquals(0, gateway.shows)
    }

    @Test fun notLoadedYet_showsNothingAndRecordsNoImpression() {
        gateway.ready = false
        val c = coordinator()
        assertFalse(c.backFromReview())
        assertEquals(1, gateway.preloads)
        // The failed attempt did not use up the session cap.
        gateway.ready = true
        assertTrue(c.backFromReview())
    }

    @Test fun preload_onlyWhenAnInterstitialCouldShow() {
        val c = coordinator()
        c.preloadIfEligible()
        assertEquals(1, gateway.preloads)
        policy.onSaved(SaveKind.FILL_UP)
        c.preloadIfEligible()
        adsHidden = true
        c.preloadIfEligible()
        assertEquals(1, gateway.preloads)
    }

    @Test fun reviewFlag_isConsumedByTheFirstGarageVisit() {
        val c = coordinator()
        policy.onLeftVehicleDetail(viewedReview = true)
        policy.onSaved(SaveKind.FILL_UP) // blocked now, but the flag is still consumed
        assertFalse(c.onBackToGarage(activity))
        assertFalse(policy.reviewedBeforeGarage)
    }
}
