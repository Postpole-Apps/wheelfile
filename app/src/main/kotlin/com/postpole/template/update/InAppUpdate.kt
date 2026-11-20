package com.postpole.template.update

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Play In-App Update, flexible flow: checks once per [check] call, downloads in the background and
 * exposes [readyToInstall] so the UI can offer a restart. Does nothing outside Play installs.
 * Create before the activity is STARTED (registers an activity-result launcher).
 */
class InAppUpdate(
    private val activity: ComponentActivity,
    private val onError: (Throwable) -> Unit = {},
) {
    private val manager: AppUpdateManager? = runCatching { AppUpdateManagerFactory.create(activity) }
        .onFailure(onError)
        .getOrNull()

    private val _readyToInstall = MutableStateFlow(false)
    val readyToInstall: StateFlow<Boolean> = _readyToInstall.asStateFlow()

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        // Declining is fine; we'll offer again on a later launch.
    }

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) _readyToInstall.value = true
    }

    /** Starts a flexible update if one is available, or surfaces an already-downloaded one. */
    fun check() {
        val m = manager ?: return
        m.registerListener(listener)
        m.appUpdateInfo
            .addOnSuccessListener { info ->
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> _readyToInstall.value = true
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                        runCatching {
                            m.startUpdateFlowForResult(
                                info,
                                launcher,
                                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                            )
                        }.onFailure(onError)
                }
            }
            // Not installed from Play / no Play services: expected, not worth reporting.
            .addOnFailureListener { }
    }

    fun completeUpdate() {
        manager?.completeUpdate()
    }

    fun release() {
        manager?.unregisterListener(listener)
    }
}
