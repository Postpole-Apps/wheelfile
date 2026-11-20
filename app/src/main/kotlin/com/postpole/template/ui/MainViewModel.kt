package com.postpole.template.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.template.analytics.Analytics
import com.postpole.template.analytics.AnalyticsEvents
import com.postpole.template.data.SettingsRepository
import com.postpole.template.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ThemeState(val mode: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = true)

/** Activity-level state: theme for [com.postpole.template.ui.theme.TemplateTheme] and screen-view logging. */
@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    private val analytics: Analytics,
) : ViewModel() {

    val theme: StateFlow<ThemeState> = combine(settings.themeMode, settings.dynamicColor, ::ThemeState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeState())

    fun onScreen(route: String) {
        analytics.logScreenView(route)
        if (route == Routes.PRO) analytics.logEvent(AnalyticsEvents.PRO_SCREEN_VIEW)
    }
}
