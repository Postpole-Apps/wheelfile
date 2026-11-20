package com.postpole.wheelfile.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.postpole.wheelfile.BuildConfig

/**
 * Thin seam over the static UMP calls so [ConsentManager] can be unit tested with a fake.
 * Error callbacks carry a short message, or null on success. Callbacks arrive on the main thread.
 */
interface ConsentSdk {
    /** Last known `canRequestAds()`; may be true from a previous session before the next update. */
    val canRequestAds: Boolean

    /** `privacyOptionsRequirementStatus == REQUIRED` (EEA/UK, and the US-state message). */
    val privacyOptionsRequired: Boolean

    fun requestConsentInfoUpdate(activity: Activity, onSuccess: () -> Unit, onError: (String) -> Unit)

    fun loadAndShowConsentFormIfRequired(activity: Activity, onDone: (error: String?) -> Unit)

    fun showPrivacyOptionsForm(activity: Activity, onDone: (error: String?) -> Unit)
}

class UmpConsentSdk(context: Context) : ConsentSdk {
    private val appContext = context.applicationContext
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)

    override val canRequestAds: Boolean get() = info.canRequestAds()

    override val privacyOptionsRequired: Boolean
        get() = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun requestConsentInfoUpdate(activity: Activity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        info.requestConsentInfoUpdate(
            activity,
            buildParams(activity),
            { onSuccess() },
            { error -> onError("${error.errorCode}: ${error.message}") },
        )
    }

    override fun loadAndShowConsentFormIfRequired(activity: Activity, onDone: (error: String?) -> Unit) {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
            onDone(error?.let { "${it.errorCode}: ${it.message}" })
        }
    }

    override fun showPrivacyOptionsForm(activity: Activity, onDone: (error: String?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            onDone(error?.let { "${it.errorCode}: ${it.message}" })
        }
    }

    private fun buildParams(activity: Activity): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        // Debug-only geography/device overrides come from local Gradle properties, never from the repo.
        if (BuildConfig.DEBUG) {
            val geography = when (BuildConfig.UMP_DEBUG_GEOGRAPHY.uppercase()) {
                "EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                "REGULATED_US_STATE" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
                "OTHER" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
                else -> null
            }
            if (geography != null) {
                val debug = ConsentDebugSettings.Builder(activity).setDebugGeography(geography)
                BuildConfig.UMP_TEST_DEVICE_ID.takeIf { it.isNotBlank() }?.let(debug::addTestDeviceHashedId)
                builder.setConsentDebugSettings(debug.build())
            }
        }
        return builder.build()
    }
}

/** Seam over the Mobile Ads SDK; only ever used after consent allows ad requests. */
interface AdsSdk {
    /** Applies [maxContentRating] ("G", "PG", "T" or "MA"), then initializes the SDK. */
    fun initialize(maxContentRating: String)
}

class GmsAdsSdk(context: Context) : AdsSdk {
    private val appContext = context.applicationContext

    override fun initialize(maxContentRating: String) {
        // Off the main thread, as the SDK recommends. No test-device IDs are configured here.
        Thread {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setMaxAdContentRating(contentRatingConstant(maxContentRating)).build(),
            )
            MobileAds.initialize(appContext)
        }.start()
    }
}

/** Maps the Remote Config rating to the SDK constant; anything unknown falls back to PG. */
fun contentRatingConstant(rating: String): String = when (rating.trim().uppercase()) {
    "G" -> RequestConfiguration.MAX_AD_CONTENT_RATING_G
    "T" -> RequestConfiguration.MAX_AD_CONTENT_RATING_T
    "MA" -> RequestConfiguration.MAX_AD_CONTENT_RATING_MA
    else -> RequestConfiguration.MAX_AD_CONTENT_RATING_PG
}
