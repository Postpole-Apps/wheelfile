package com.postpole.wheelfile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.db.VehicleDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Decides the first screen once: Welcome on a true first run, otherwise the Garage. Null until known. */
@HiltViewModel
class StartViewModel @Inject constructor(
    settings: SettingsRepository,
    vehicles: VehicleDao,
) : ViewModel() {
    val startRoute: StateFlow<String?> = flow {
        val done = settings.onboardingDone.first()
        val hasVehicle = vehicles.getAll().isNotEmpty()
        emit(if (done || hasVehicle) Routes.GARAGE else Routes.WELCOME)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
