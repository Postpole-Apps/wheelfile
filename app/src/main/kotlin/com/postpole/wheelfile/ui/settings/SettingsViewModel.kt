package com.postpole.wheelfile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot results shown as a snackbar. */
sealed interface SettingsEvent {
    data object RestoreChecked : SettingsEvent
    data object RestoreFailed : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = billing.isPro

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val defaultCurrency: StateFlow<String?> = settings.defaultCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch { settings.setDefaultCurrency(code) }
    }

    fun restore() {
        viewModelScope.launch {
            _events.trySend(if (billing.restore()) SettingsEvent.RestoreChecked else SettingsEvent.RestoreFailed)
        }
    }
}
