package com.postpole.wheelfile.ads

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.postpole.wheelfile.R
import com.postpole.wheelfile.analytics.Analytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the current [AdsConfig] comes from. Always has a value (bundled defaults until a fetch lands). */
interface AdsConfigSource {
    val config: StateFlow<AdsConfig>

    /** Starts the background fetch-and-activate. Safe to call more than once. */
    fun start()
}

/** Firebase Remote Config backed source. Without a google-services.json it just serves the defaults. */
class FirebaseAdsConfigSource(
    private val context: Context,
    private val analytics: Analytics,
) : AdsConfigSource {
    private val _config = MutableStateFlow(AdsConfig.DEFAULTS)
    override val config: StateFlow<AdsConfig> = _config.asStateFlow()

    private var started = false

    override fun start() {
        if (started) return
        started = true
        val configured = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
        if (!configured) return
        try {
            val rc = FirebaseRemoteConfig.getInstance()
            rc.setDefaultsAsync(R.xml.remote_config_defaults).addOnCompleteListener {
                _config.value = rc.toAdsConfig()
                rc.fetchAndActivate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        _config.value = rc.toAdsConfig()
                    } else {
                        task.exception?.let(analytics::recordNonFatal)
                    }
                }
            }
        } catch (e: Exception) {
            analytics.recordNonFatal(e)
        }
    }

    private fun FirebaseRemoteConfig.toAdsConfig(): AdsConfig {
        val d = AdsConfig.DEFAULTS
        return AdsConfig(
            adsEnabled = getBoolean(AdsConfigKeys.ADS_ENABLED),
            bannerEnabled = getBoolean(AdsConfigKeys.BANNER_ENABLED),
            interstitialEnabled = getBoolean(AdsConfigKeys.INTERSTITIAL_ENABLED),
            rewardedEnabled = getBoolean(AdsConfigKeys.REWARDED_ENABLED),
            nativeEnabled = getBoolean(AdsConfigKeys.NATIVE_ENABLED),
            appOpenEnabled = getBoolean(AdsConfigKeys.APP_OPEN_ENABLED),
            interstitialBlockFirstSession = getBoolean(AdsConfigKeys.INTERSTITIAL_BLOCK_FIRST_SESSION),
            interstitialMinInstallDays = getLong(AdsConfigKeys.INTERSTITIAL_MIN_INSTALL_DAYS).toInt(),
            interstitialMinIntervalSec = getLong(AdsConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC).toInt(),
            interstitialMaxPerSession = getLong(AdsConfigKeys.INTERSTITIAL_MAX_PER_SESSION).toInt(),
            interstitialMaxPerDay = getLong(AdsConfigKeys.INTERSTITIAL_MAX_PER_DAY).toInt(),
            maxContentRating = getString(AdsConfigKeys.MAX_CONTENT_RATING).ifBlank { d.maxContentRating },
            rewardedDailyLimit = getLong(AdsConfigKeys.REWARDED_DAILY_LIMIT).toInt(),
        )
    }
}
