package com.postpole.wheelfile

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.postpole.wheelfile.ads.AdsConfigSource
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.ProPromptPolicy
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.reminders.AndroidReminderNotifier
import com.postpole.wheelfile.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WheelfileApp : Application(), Configuration.Provider {
    /** Default WorkManager setup, declared so it also initialises on demand where the startup initializer doesn't run. */
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()

    @Inject lateinit var billing: BillingRepository
    @Inject lateinit var analytics: Analytics
    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var adsConfig: AdsConfigSource
    @Inject lateinit var notifier: AndroidReminderNotifier

    // Created at launch so "first session" is decided once per process.
    @Inject lateinit var adsPolicy: AdsPolicy
    @Inject lateinit var proPrompts: ProPromptPolicy

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startProPromptSession()
        billing.start()
        adsConfig.start()
        logLaunch()
        notifier.ensureChannel()
        ReminderScheduler(this).scheduleDaily()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    billing.onForeground()
                    appScope.launch { proPrompts.onForegrounded() }
                }

                override fun onStop(owner: LifecycleOwner) = proPrompts.onBackgrounded()
            },
        )
    }

    /** Cold start begins a Pro-prompt session (spec §5.4a); a return after ≥ 30 min in the background begins another. */
    private fun startProPromptSession() {
        val installTime = runCatching { packageManager.getPackageInfo(packageName, 0).firstInstallTime }
            .getOrDefault(System.currentTimeMillis())
        appScope.launch { proPrompts.startSession(installTime) }
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

    internal companion object {
        val LAUNCHED_BEFORE = booleanPreferencesKey("launched_before")
    }
}
