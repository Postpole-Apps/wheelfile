package com.postpole.wheelfile.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.ConsentManager
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.billing.RestoreResult
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot results shown as a snackbar. */
sealed interface SettingsEvent {
    data object PurchasesRestored : SettingsEvent
    data object NoPurchasesFound : SettingsEvent
    data object RestoreFailed : SettingsEvent
    data object PrivacyOptionsFailed : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val settings: SettingsRepository,
    private val consent: ConsentManager,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = billing.isPro

    /** Show the "Privacy options" row only when UMP says it is required (EEA/UK and US-state regions). */
    val privacyOptionsRequired: StateFlow<Boolean> = consent.privacyOptionsRequired

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val defaultCurrency: StateFlow<String?> = settings.defaultCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _restoring = MutableStateFlow(false)

    /** True while "Restore purchases" is talking to Google Play (the row shows progress). */
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch { settings.setDefaultCurrency(code) }
    }

    /** Always ends in visible feedback: a "restored" or "none found" message, or an error. */
    fun restore() {
        if (_restoring.value) return
        _restoring.value = true
        viewModelScope.launch {
            val event = try {
                when (billing.restore()) {
                    RestoreResult.RESTORED -> SettingsEvent.PurchasesRestored
                    RestoreResult.NONE_FOUND -> SettingsEvent.NoPurchasesFound
                    RestoreResult.FAILED -> SettingsEvent.RestoreFailed
                }
            } catch (_: Exception) {
                SettingsEvent.RestoreFailed
            } finally {
                _restoring.value = false
            }
            _events.trySend(event)
        }
    }

    /** The row was tapped but there is no Activity to host the form: tell the user instead of doing nothing. */
    fun onPrivacyOptionsUnavailable() {
        _events.trySend(SettingsEvent.PrivacyOptionsFailed)
    }

    /** Opens the UMP privacy options form with the current [activity]. */
    fun openPrivacyOptions(activity: Activity) {
        consent.showPrivacyOptions(activity) { _events.trySend(SettingsEvent.PrivacyOptionsFailed) }
    }
}
