package com.postpole.template.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

class FirebaseAnalyticsImpl(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
) : Analytics {

    override fun logEvent(name: String, params: Map<String, Any>) {
        analytics.logEvent(name, params.toBundle())
    }

    override fun setUserProperty(name: String, value: String?) {
        analytics.setUserProperty(name, value)
    }

    override fun logScreenView(screenName: String) {
        analytics.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply { putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName) },
        )
    }

    override fun recordNonFatal(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    private fun Map<String, Any>.toBundle() = Bundle().also { bundle ->
        forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Float -> bundle.putDouble(key, value.toDouble())
                is Boolean -> bundle.putLong(key, if (value) 1L else 0L)
                else -> bundle.putString(key, value.toString())
            }
        }
    }
}

/**
 * Returns the Firebase implementation when this app was built with a google-services.json
 * (the plugin then initialises [FirebaseApp] automatically), otherwise [NoOpAnalytics].
 */
fun createAnalytics(context: Context): Analytics {
    val configured = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    if (!configured) return NoOpAnalytics
    return FirebaseAnalyticsImpl(FirebaseAnalytics.getInstance(context), FirebaseCrashlytics.getInstance())
}
