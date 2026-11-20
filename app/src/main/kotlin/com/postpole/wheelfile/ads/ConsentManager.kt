package com.postpole.wheelfile.ads

import android.app.Activity
import com.postpole.wheelfile.analytics.Analytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wraps a UMP failure so Crashlytics groups them as one non-fatal type. */
class ConsentException(message: String) : Exception(message)

/**
 * Owns consent state for ads. Order on launch: consent info update, then the consent form if required,
 * then (only when `canRequestAds()` is true) the Mobile Ads SDK is initialized. Consent errors are logged
 * as non-fatals and never block the app; they just mean no ads.
 */
class ConsentManager(
    private val sdk: ConsentSdk,
    private val ads: AdsSdk,
    private val analytics: Analytics,
    private val maxContentRating: () -> String = { AdsConfig.DEFAULT.maxContentRating },
) {
    private val _canRequestAds = MutableStateFlow(false)

    /** Whether consent currently allows ad requests. Becomes false right after consent is withdrawn. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(sdk.privacyOptionsRequired)

    /** Whether Settings should show the "Privacy options" row. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val _adsReady = MutableStateFlow(false)

    /** True once the SDK is initialized AND consent allows requests. Ad slots only request when this is true. */
    val adsReady: StateFlow<Boolean> = _adsReady.asStateFlow()

    @Volatile private var gatherStarted = false
    @Volatile private var adsInitialized = false

    /** Run once per process from the launch Activity. Safe to call again (later calls are ignored). */
    fun gatherConsent(activity: Activity) {
        if (gatherStarted) return
        gatherStarted = true
        sdk.requestConsentInfoUpdate(
            activity,
            onSuccess = {
                sdk.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) logError("Consent form failed: $error")
                    onConsentSettled()
                }
            },
            onError = { error ->
                logError("Consent info update failed: $error")
                // A previous session's consent may still allow requests; otherwise no ads this session.
                onConsentSettled()
            },
        )
    }

    /**
     * Opens the UMP privacy options form with the current [activity]. [onError] runs when the form can't be
     * shown. Consent is re-read afterwards either way, so a withdrawal stops ad requests immediately.
     */
    fun showPrivacyOptions(activity: Activity, onError: () -> Unit) {
        sdk.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                logError("Privacy options form failed: $error")
                onError()
            }
            onConsentSettled()
        }
    }

    private fun onConsentSettled() {
        _privacyOptionsRequired.value = sdk.privacyOptionsRequired
        val allowed = sdk.canRequestAds
        _canRequestAds.value = allowed
        if (allowed && !adsInitialized) {
            adsInitialized = true
            ads.initialize(maxContentRating())
        }
        _adsReady.value = allowed && adsInitialized
    }

    private fun logError(message: String) = analytics.recordNonFatal(ConsentException(message))
}
