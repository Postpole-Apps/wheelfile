package com.postpole.wheelfile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Chooses the first screen: Welcome until a vehicle exists or onboarding was completed, then Garage. */
@HiltViewModel
class StartViewModel @Inject constructor(
    settings: SettingsRepository,
    vehicles: VehicleRepository,
) : ViewModel() {
    /** Null until the first read finishes. */
    val startRoute: StateFlow<String?> = combine(settings.onboardingDone, vehicles.observeAll()) { done, all ->
        if (done || all.isNotEmpty()) Routes.GARAGE else Routes.WELCOME
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
