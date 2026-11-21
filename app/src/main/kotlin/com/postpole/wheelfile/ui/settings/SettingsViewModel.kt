package com.postpole.wheelfile.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.ConsentEvent
import com.postpole.wheelfile.ads.ConsentManager
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.billing.RestoreResult
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.ThemeMode
import com.postpole.wheelfile.domain.reminder.QuietHours
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
    data object Restored : SettingsEvent
    data object NothingToRestore : SettingsEvent
    data object RestoreFailed : SettingsEvent
    data object PrivacyOptionsUnavailable : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val settings: SettingsRepository,
    private val consent: ConsentManager,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = billing.isPro
    val adsHidden: StateFlow<Boolean> = billing.adsHidden

    /** Show the "Privacy options" row only when UMP says it is required (EEA/UK, US states). */
    val privacyOptionsRequired: StateFlow<Boolean> = consent.privacyOptionsRequired

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val defaultCurrency: StateFlow<String?> = settings.defaultCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val quietHours: StateFlow<QuietHours> = settings.quietHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuietHours())

    fun setQuietHours(quiet: QuietHours) {
        viewModelScope.launch { settings.setQuietHours(quiet) }
    }

    private val _restoring =MutableStateFlow(false)

    /** True while "Restore purchases" is running (the screen shows progress). */
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    init {
        consent.refresh()
        viewModelScope.launch {
            consent.events.collect {
                when (it) {
                    ConsentEvent.PRIVACY_OPTIONS_UNAVAILABLE -> _events.trySend(SettingsEvent.PrivacyOptionsUnavailable)
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch { settings.setDefaultCurrency(code) }
    }

    /** Opens the UMP privacy options form on [activity]; no-op feedback-wise if there is no Activity to host it. */
    fun openPrivacyOptions(activity: Activity?) {
        if (activity == null) {
            _events.trySend(SettingsEvent.PrivacyOptionsUnavailable)
            return
        }
        consent.showPrivacyOptions(activity)
    }

    fun restore() {
        if (_restoring.value) return
        _restoring.value = true
        viewModelScope.launch {
            try {
                _events.trySend(
                    when (billing.restore()) {
                        RestoreResult.RESTORED -> SettingsEvent.Restored
                        RestoreResult.NOTHING_FOUND -> SettingsEvent.NothingToRestore
                        RestoreResult.FAILED -> SettingsEvent.RestoreFailed
                    },
                )
            } finally {
                _restoring.value = false
            }
        }
    }
}
