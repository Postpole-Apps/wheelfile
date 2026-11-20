package com.postpole.wheelfile.ads

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.postpole.wheelfile.analytics.Analytics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

private class FakeConsentInformation : ConsentInformation {
    var canRequest = false
    var privacyStatus = PrivacyOptionsRequirementStatus.UNKNOWN
    var updateRequests = 0
    private var onSuccess: ConsentInformation.OnConsentInfoUpdateSuccessListener? = null

    override fun getConsentStatus() = ConsentInformation.ConsentStatus.UNKNOWN
    override fun canRequestAds() = canRequest
    override fun getPrivacyOptionsRequirementStatus() = privacyStatus
    override fun isConsentFormAvailable() = true
    override fun reset() = Unit

    override fun requestConsentInfoUpdate(
        activity: Activity,
        params: ConsentRequestParameters,
        success: ConsentInformation.OnConsentInfoUpdateSuccessListener,
        failure: ConsentInformation.OnConsentInfoUpdateFailureListener,
    ) {
        updateRequests++
        onSuccess = success
    }

    /** Completes the pending consent info update, as the UMP SDK would. */
    fun completeUpdate() = onSuccess!!.onConsentInfoUpdateSuccess()
}

private class FakeForms(private val info: FakeConsentInformation) : ConsentForms {
    var launchForms = 0
    var privacyOptionsCalls = 0
    var launchError: String? = null
    var privacyError: String? = null
    var consentAfterLaunchForm = true
    var consentAfterPrivacyForm: Boolean? = null
    var lastPrivacyActivity: Activity? = null

    override fun loadAndShowIfRequired(activity: Activity, onDone: (String?) -> Unit) {
        launchForms++
        info.canRequest = consentAfterLaunchForm
        onDone(launchError)
    }

    override fun showPrivacyOptions(activity: Activity, onDone: (String?) -> Unit) {
        privacyOptionsCalls++
        lastPrivacyActivity = activity
        consentAfterPrivacyForm?.let { info.canRequest = it }
        onDone(privacyError)
    }
}

private class FakeAdsSdk(private val log: MutableList<String>) : AdsSdk {
    var initialized = 0
    var rating: String? = null
    override fun initialize() {
        initialized++
        log += "init"
    }

    override fun setMaxContentRating(rating: String) {
        this.rating = rating
        log += "rating"
    }
}

private class RecordingAnalytics : Analytics {
    val nonFatals = mutableListOf<Throwable>()
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun recordNonFatal(throwable: Throwable) {
        nonFatals += throwable
    }
}

@RunWith(RobolectricTestRunner::class)
class ConsentGateTest {
    private val info = FakeConsentInformation()
    private val forms = FakeForms(info)
    private val order = mutableListOf<String>()
    private val sdk = FakeAdsSdk(order)
    private val analytics = RecordingAnalytics()
    private lateinit var activity: Activity
    private lateinit var manager: ConsentManager
    private val params = ConsentRequestParameters.Builder().build()

    @Before fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        manager = ConsentManager(info, forms, sdk, analytics, maxContentRating = { "PG" })
    }

    @Test fun noAdRequestBeforeConsentInfoUpdate() {
        // Consent was already stored from an earlier run: canRequestAds() is true from the start.
        info.canRequest = true
        manager.refresh()
        assertFalse(manager.canRequestAds.value)
        assertEquals(0, sdk.initialized)

        manager.gatherConsent(activity, params)
        // Update requested but not finished: still nothing.
        assertEquals(1, info.updateRequests)
        assertEquals(0, forms.launchForms)
        assertFalse(manager.canRequestAds.value)
        assertEquals(0, sdk.initialized)

        info.completeUpdate()
        assertEquals(1, forms.launchForms)
        assertTrue(manager.canRequestAds.value)
        assertEquals(1, sdk.initialized)
    }

    @Test fun formShownAfterUpdateAndBeforeInit_ratingSetBeforeInit() {
        manager.gatherConsent(activity, params)
        info.completeUpdate()
        assertEquals(listOf("rating", "init"), order)
        assertEquals("PG", sdk.rating)
    }

    @Test fun consentDeclined_noInitAndNoAds() {
        forms.consentAfterLaunchForm = false
        manager.gatherConsent(activity, params)
        info.completeUpdate()
        assertFalse(manager.canRequestAds.value)
        assertEquals(0, sdk.initialized)
    }

    @Test fun formError_isNonFatalAndAppContinues() {
        forms.launchError = "3: form failed"
        forms.consentAfterLaunchForm = false
        manager.gatherConsent(activity, params)
        info.completeUpdate()
        assertEquals(1, analytics.nonFatals.size)
        assertTrue(analytics.nonFatals.single() is ConsentException)
        assertFalse(manager.canRequestAds.value)
    }

    @Test fun launchFlowRunsOncePerProcess() {
        manager.gatherConsent(activity, params)
        info.completeUpdate()
        manager.gatherConsent(activity, params)
        assertEquals(1, info.updateRequests)
    }

    @Test fun privacyOptionsRow_followsRequirementStatus() {
        info.privacyStatus = PrivacyOptionsRequirementStatus.REQUIRED
        manager.refresh()
        assertTrue(manager.privacyOptionsRequired.value)

        info.privacyStatus = PrivacyOptionsRequirementStatus.NOT_REQUIRED
        manager.refresh()
        assertFalse(manager.privacyOptionsRequired.value)

        info.privacyStatus = PrivacyOptionsRequirementStatus.UNKNOWN
        manager.refresh()
        assertFalse(manager.privacyOptionsRequired.value)
    }

    @Test fun privacyOptionsRow_updatesWhenConsentInfoArrives() {
        manager.gatherConsent(activity, params)
        assertFalse(manager.privacyOptionsRequired.value)
        info.privacyStatus = PrivacyOptionsRequirementStatus.REQUIRED
        info.completeUpdate()
        assertTrue(manager.privacyOptionsRequired.value)
    }

    @Test fun tappingPrivacyOptions_callsShowPrivacyOptionsFormWithTheActivity() {
        info.privacyStatus = PrivacyOptionsRequirementStatus.REQUIRED
        manager.showPrivacyOptions(activity)
        assertEquals(1, forms.privacyOptionsCalls)
        assertEquals(activity, forms.lastPrivacyActivity)
    }

    @Test fun withdrawnConsent_stopsAdRequestsWithoutRestart() {
        manager.gatherConsent(activity, params)
        info.completeUpdate()
        assertTrue(manager.canRequestAds.value)

        forms.consentAfterPrivacyForm = false
        manager.showPrivacyOptions(activity)
        assertFalse(manager.canRequestAds.value)
    }

    @Test fun privacyOptionsError_emitsEventAndLogsNonFatal() = runBlocking {
        forms.privacyError = "1: no form"
        manager.showPrivacyOptions(activity)
        assertEquals(ConsentEvent.PRIVACY_OPTIONS_UNAVAILABLE, manager.events.first())
        assertEquals(1, analytics.nonFatals.size)
    }

    @Test fun privacyOptionsSuccess_emitsNoEvent() {
        manager.showPrivacyOptions(activity)
        assertTrue(analytics.nonFatals.isEmpty())
    }
}
