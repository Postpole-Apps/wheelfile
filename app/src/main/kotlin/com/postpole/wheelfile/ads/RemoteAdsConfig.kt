package com.postpole.wheelfile.ads

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.postpole.wheelfile.R
import com.postpole.wheelfile.analytics.Analytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Source of the current [AdsConfig]. Starts with bundled defaults; a background fetch may update it. */
interface RemoteAdsConfig {
    val config: StateFlow<AdsConfig>

    /** Fetch and activate in the background. Safe to call more than once; never blocks the caller. */
    fun refresh()
}

/** Used when Firebase isn't configured (no google-services.json): the bundled defaults apply. */
object DefaultRemoteAdsConfig : RemoteAdsConfig {
    override val config: StateFlow<AdsConfig> = MutableStateFlow(AdsConfig.DEFAULT).asStateFlow()
    override fun refresh() = Unit
}

class FirebaseRemoteAdsConfig(
    private val remoteConfig: FirebaseRemoteConfig,
    private val analytics: Analytics,
) : RemoteAdsConfig {
    private val _config = MutableStateFlow(AdsConfig.DEFAULT)
    override val config: StateFlow<AdsConfig> = _config.asStateFlow()

    private val values = object : ConfigValues {
        override fun bool(key: String) = remoteConfig.getBoolean(key)
        override fun long(key: String) = remoteConfig.getLong(key)
        override fun string(key: String) = remoteConfig.getString(key)
    }

    init {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(MIN_FETCH_INTERVAL_SEC).build(),
        )
        // Defaults first, so values read before the first fetch match the bundled XML.
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults).addOnCompleteListener { read() }
    }

    override fun refresh() {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                read()
            } else {
                task.exception?.let(analytics::recordNonFatal)
            }
        }
    }

    private fun read() {
        _config.value = AdsConfig.from(values)
    }

    companion object {
        private const val MIN_FETCH_INTERVAL_SEC = 3_600L

        /** Firebase when this app has its own google-services.json, otherwise the bundled defaults. */
        fun create(context: Context, analytics: Analytics): RemoteAdsConfig =
            if (FirebaseApp.getApps(context).isEmpty()) {
                DefaultRemoteAdsConfig
            } else {
                FirebaseRemoteAdsConfig(FirebaseRemoteConfig.getInstance(), analytics)
            }
    }
}
