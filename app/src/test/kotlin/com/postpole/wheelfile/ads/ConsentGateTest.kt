package com.postpole.wheelfile.ads

import android.app.Activity
import com.postpole.wheelfile.analytics.Analytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** A fake UMP: nothing completes until the test says so, so ordering can be asserted. */
private class FakeConsentSdk : ConsentSdk {
    override var canRequestAds = false
    override var privacyOptionsRequired = false

    val calls = mutableListOf<String>()
    var pendingUpdate: Pair<() -> Unit, (String) -> Unit>? = null
    var pendingForm: ((String?) -> Unit)? = null
    var privacyFormActivity: Activity? = null
    var privacyFormResult: String? = null

    /** What the user did inside the privacy options form (for example withdrew consent). */
    var inPrivacyForm: () -> Unit = {}

    override fun requestConsentInfoUpdate(activity: Activity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        calls += "requestConsentInfoUpdate"
        pendingUpdate = onSuccess to onError
    }

    override fun loadAndShowConsentFormIfRequired(activity: Activity, onDone: (String?) -> Unit) {
        calls += "loadAndShowConsentFormIfRequired"
        pendingForm = onDone
    }

    override fun showPrivacyOptionsForm(activity: Activity, onDone: (String?) -> Unit) {
        calls += "showPrivacyOptionsForm"
        privacyFormActivity = activity
        inPrivacyForm()
        onDone(privacyFormResult)
    }
}

private class FakeAdsSdk(private val calls: MutableList<String>) : AdsSdk {
    var initializedWith = mutableListOf<String>()
    override fun initialize(maxContentRating: String) {
        calls += "MobileAds.initialize"
        initializedWith += maxContentRating
    }
}

private class RecordingAnalytics : Analytics {
    val nonFatals = mutableListOf<Throwable>()
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun recordNonFatal(throwable: Throwable) { nonFatals += throwable }
}

@RunWith(RobolectricTestRunner::class)
class ConsentGateTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val sdk = FakeConsentSdk()
    private val ads = FakeAdsSdk(sdk.calls)
    private val analytics = RecordingAnalytics()
    private fun manager() = ConsentManager(sdk, ads, analytics) { "PG" }

    @Test fun noAdsSdkCall_beforeTheConsentInfoUpdateCompletes() {
        sdk.canRequestAds = true // even if a previous session allowed ads
        val manager = manager()
        manager.gatherConsent(activity)

        assertEquals(listOf("requestConsentInfoUpdate"), sdk.calls)
        assertEquals(0, ads.initializedWith.size)
        assertFalse(manager.adsReady.value)
    }

    @Test fun order_isUpdate_thenForm_thenInitialize() {
        val manager = manager()
        manager.gatherConsent(activity)
        sdk.pendingUpdate!!.first()
        assertEquals(0, ads.initializedWith.size)

        sdk.canRequestAds = true // the user accepted in the form
        sdk.pendingForm!!(null)

        assertEquals(
            listOf("requestConsentInfoUpdate", "loadAndShowConsentFormIfRequired", "MobileAds.initialize"),
            sdk.calls,
        )
        assertEquals(listOf("PG"), ads.initializedWith)
        assertTrue(manager.canRequestAds.value)
        assertTrue(manager.adsReady.value)
    }

    @Test fun consentDenied_neverInitializesAds() {
        val manager = manager()
        manager.gatherConsent(activity)
        sdk.pendingUpdate!!.first()
        sdk.canRequestAds = false
        sdk.pendingForm!!(null)

        assertEquals(0, ads.initializedWith.size)
        assertFalse(manager.canRequestAds.value)
        assertFalse(manager.adsReady.value)
    }

    @Test fun updateError_isLoggedAndDoesNotBlock() {
        val manager = manager()
        manager.gatherConsent(activity)
        sdk.pendingUpdate!!.second("3: network")

        assertEquals(1, analytics.nonFatals.size)
        assertTrue(analytics.nonFatals.single() is ConsentException)
        assertEquals(0, ads.initializedWith.size) // no consent, no ads; the app keeps working
        assertFalse(manager.adsReady.value)
    }

    @Test fun formError_isLoggedAndDoesNotBlock() {
        val manager = manager()
        manager.gatherConsent(activity)
        sdk.pendingUpdate!!.first()
        sdk.pendingForm!!("1: form failed")

        assertEquals(1, analytics.nonFatals.size)
        assertFalse(manager.adsReady.value)
    }

    @Test fun gather_runsOncePerProcess() {
        val manager = manager()
        manager.gatherConsent(activity)
        manager.gatherConsent(activity)
        assertEquals(1, sdk.calls.count { it == "requestConsentInfoUpdate" })
    }

    @Test fun privacyOptionsRow_followsTheRequirementStatus() {
        sdk.privacyOptionsRequired = false
        val manager = manager()
        assertFalse(manager.privacyOptionsRequired.value)

        manager.gatherConsent(activity)
        sdk.privacyOptionsRequired = true // UMP now reports REQUIRED (EEA/UK or a US state)
        sdk.pendingUpdate!!.first()
        sdk.pendingForm!!(null)
        assertTrue(manager.privacyOptionsRequired.value)

        sdk.privacyOptionsRequired = false
        manager.showPrivacyOptions(activity) {}
        assertFalse(manager.privacyOptionsRequired.value)
    }

    @Test fun privacyOptionsRow_isVisibleFromTheSavedStatusBeforeAnyUpdate() {
        sdk.privacyOptionsRequired = true
        assertTrue(manager().privacyOptionsRequired.value)
    }

    @Test fun tappingPrivacyOptions_callsShowPrivacyOptionsFormWithTheActivity() {
        sdk.privacyOptionsRequired = true
        val manager = manager()
        var failed = false
        manager.showPrivacyOptions(activity) { failed = true }

        assertEquals(listOf("showPrivacyOptionsForm"), sdk.calls)
        assertSame(activity, sdk.privacyFormActivity)
        assertFalse(failed)
        assertEquals(0, analytics.nonFatals.size)
    }

    @Test fun privacyOptionsError_reportsFailureAndLogsNonFatal() {
        val manager = manager()
        var failed = false
        sdk.privacyFormResult = "2: form unavailable"
        manager.showPrivacyOptions(activity) { failed = true }

        assertTrue(failed)
        assertTrue(analytics.nonFatals.single() is ConsentException)
    }

    @Test fun withdrawingConsent_stopsAdRequestsWithoutARestart() {
        val manager = manager()
        manager.gatherConsent(activity)
        sdk.canRequestAds = true
        sdk.pendingUpdate!!.first()
        sdk.pendingForm!!(null)
        assertTrue(manager.adsReady.value)

        sdk.inPrivacyForm = { sdk.canRequestAds = false } // user withdraws consent in the form
        manager.showPrivacyOptions(activity) {}

        assertFalse(manager.canRequestAds.value)
        assertFalse(manager.adsReady.value)
    }

    @Test fun grantingConsentInThePrivacyForm_initializesAdsOnce() {
        val manager = manager()
        manager.gatherConsent(activity)
        sdk.pendingUpdate!!.first()
        sdk.pendingForm!!(null) // denied at launch
        assertEquals(0, ads.initializedWith.size)

        sdk.inPrivacyForm = { sdk.canRequestAds = true }
        manager.showPrivacyOptions(activity) {}
        manager.showPrivacyOptions(activity) {}

        assertEquals(1, ads.initializedWith.size)
        assertTrue(manager.adsReady.value)
    }
}
