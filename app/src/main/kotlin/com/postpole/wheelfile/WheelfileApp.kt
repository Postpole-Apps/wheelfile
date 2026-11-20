package com.postpole.wheelfile

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.postpole.wheelfile.ads.RemoteAdsConfig
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.billing.BillingRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WheelfileApp : Application() {
    @Inject lateinit var billing: BillingRepository
    @Inject lateinit var analytics: Analytics
    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var remoteAdsConfig: RemoteAdsConfig

    override fun onCreate() {
        super.onCreate()
        billing.start()
        // Fetch-and-activate runs in the background; the bundled defaults apply until it completes.
        remoteAdsConfig.refresh()
        logLaunch()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = billing.onForeground()
            },
        )
    }

    private fun logLaunch() {
        analytics.logEvent(AnalyticsEvents.APP_OPEN)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            var first = false
            dataStore.edit {
                if (it[LAUNCHED_BEFORE] != true) {
                    first = true
                    it[LAUNCHED_BEFORE] = true
                }
            }
            if (first) analytics.logEvent(AnalyticsEvents.FIRST_LAUNCH)
        }
    }

    private companion object {
        val LAUNCHED_BEFORE = booleanPreferencesKey("launched_before")
    }
}
