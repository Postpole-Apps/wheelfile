package com.postpole.wheelfile.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.postpole.wheelfile.analytics.Analytics
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Wraps a UMP failure so Crashlytics groups consent problems as one non-fatal type. */
class ConsentException(message: String) : Exception(message)

/** Seam over the static `UserMessagingPlatform` calls. Callbacks run on the main thread. */
interface ConsentForms {
    /** `loadAndShowConsentFormIfRequired`. [onDone] gets null on success or the form error message. */
    fun loadAndShowIfRequired(activity: Activity, onDone: (String?) -> Unit)

    /** `showPrivacyOptionsForm`. [onDone] gets null on success or the form error message. */
    fun showPrivacyOptions(activity: Activity, onDone: (String?) -> Unit)
}

class UmpConsentForms : ConsentForms {
    override fun loadAndShowIfRequired(activity: Activity, onDone: (String?) -> Unit) {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
            onDone(formError?.let { "${it.errorCode}: ${it.message}" })
        }
    }

    override fun showPrivacyOptions(activity: Activity, onDone: (String?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            onDone(formError?.let { "${it.errorCode}: ${it.message}" })
        }
    }
}

/** One-shot results of the Privacy options tap. */
enum class ConsentEvent { PRIVACY_OPTIONS_UNAVAILABLE }

/**
 * Consent before ads (spec §5.2). Order on launch:
 * `requestConsentInfoUpdate` → `loadAndShowConsentFormIfRequired` → only then, and only if
 * `canRequestAds()`, set the content rating and initialise the Mobile Ads SDK.
 *
 * Consent errors never block the app: they are logged as non-fatals and the app carries on without ads.
 * [canRequestAds] stays false until the consent info has been updated in this process.
 */
class ConsentManager(
    private val info: ConsentInformation,
    private val forms: ConsentForms,
    private val ads: AdsSdk,
    private val analytics: Analytics,
    private val maxContentRating: () -> String = { AdsConfig.DEFAULTS.maxContentRating },
) {
    private val _canRequestAds = MutableStateFlow(false)

    /** Whether ad requests are allowed right now. Updated after every consent change. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** True when Settings should show the "Privacy options" row (EEA/UK and US-state regimes). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val _events = Channel<ConsentEvent>(Channel.BUFFERED)
    val events: Flow<ConsentEvent> = _events.receiveAsFlow()

    private var infoUpdated = false
    private var gathering = false
    private var adsInitialized = false

    /** Runs the launch consent flow once per process. [params] is built by the caller (debug geography etc.). */
    fun gatherConsent(activity: Activity, params: ConsentRequestParameters) {
        if (gathering || infoUpdated) return
        gathering = true
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                infoUpdated = true
                forms.loadAndShowIfRequired(activity) { error ->
                    if (error != null) analytics.recordNonFatal(ConsentException("Consent form failed: $error"))
                    gathering = false
                    sync()
                }
            },
            { error ->
                analytics.recordNonFatal(
                    ConsentException("Consent info update failed: ${error.errorCode}: ${error.message}"),
                )
                gathering = false
                sync()
            },
        )
    }

    /** Re-reads the consent state (call when Settings opens so the row reflects the latest status). */
    fun refresh() = sync()

    /**
     * Opens the UMP privacy options form on [activity]. Afterwards consent is re-read so a withdrawn
     * consent stops ad requests without a restart. On failure emits [ConsentEvent.PRIVACY_OPTIONS_UNAVAILABLE].
     */
    fun showPrivacyOptions(activity: Activity) {
        forms.showPrivacyOptions(activity) { error ->
            if (error != null) {
                analytics.recordNonFatal(ConsentException("Privacy options form failed: $error"))
                _events.trySend(ConsentEvent.PRIVACY_OPTIONS_UNAVAILABLE)
            }
            sync()
        }
    }

    private fun sync() {
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED
        if (!infoUpdated) return
        val can = info.canRequestAds()
        if (can && !adsInitialized) {
            adsInitialized = true
            ads.setMaxContentRating(maxContentRating())
            ads.initialize()
        }
        _canRequestAds.value = can
    }
}

/** Builds the UMP request parameters; debug builds may force a geography via `-PUMP_DEBUG_GEOGRAPHY`. */
fun buildConsentRequestParameters(
    context: Context,
    debugGeography: String,
    testDeviceHashedId: String,
): ConsentRequestParameters {
    val builder = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false)
    val geography = when (debugGeography) {
        "EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
        "REGULATED_US_STATE" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
        else -> null
    }
    if (geography != null) {
        // The test device hash comes from a Gradle property at build time; nothing is committed.
        val debug = ConsentDebugSettings.Builder(context).setDebugGeography(geography)
        if (testDeviceHashedId.isNotBlank()) debug.addTestDeviceHashedId(testDeviceHashedId)
        builder.setConsentDebugSettings(debug.build())
    }
    return builder.build()
}
