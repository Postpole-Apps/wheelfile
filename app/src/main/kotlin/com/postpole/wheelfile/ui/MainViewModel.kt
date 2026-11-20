package com.postpole.wheelfile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ThemeState(val mode: ThemeMode = ThemeMode.SYSTEM)

/** Activity-level state: theme for [com.postpole.wheelfile.ui.theme.WheelfileTheme] and screen-view logging. */
@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    private val analytics: Analytics,
) : ViewModel() {

    val theme: StateFlow<ThemeState> = settings.themeMode.map(::ThemeState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeState())

    fun onScreen(route: String) {
        analytics.logScreenView(route)
        if (route == Routes.PRO) analytics.logEvent(AnalyticsEvents.PRO_SCREEN_VIEW)
    }
}
