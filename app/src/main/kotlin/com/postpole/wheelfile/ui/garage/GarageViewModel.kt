package com.postpole.wheelfile.ui.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.billing.ProStatus
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.economy.StreamEconomy
import com.postpole.wheelfile.domain.economy.VehicleSummary
import com.postpole.wheelfile.domain.vehicle.VehicleCap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VehicleCardData(
    val vehicle: Vehicle,
    /** Highest odometer in the vehicle's distance unit. */
    val odometer: Double?,
    val streams: List<StreamEconomy>,
)

data class GarageState(
    /** True until the first database read; the slot-reserving loading state (`st-loading`). */
    val loading: Boolean = true,
    val active: List<VehicleCardData> = emptyList(),
    val archived: List<VehicleCardData> = emptyList(),
    val selectedId: Long? = null,
) {
    val selected: VehicleCardData? get() = active.firstOrNull { it.vehicle.id == selectedId } ?: active.firstOrNull()

    /** The empty first-run Garage is ad-free: no banner and no ad request until at least one vehicle exists. */
    val showsBanner: Boolean get() = !loading && (active.isNotEmpty() || archived.isNotEmpty())
}

sealed interface GarageEvent {
    data object OpenAddVehicle : GarageEvent

    /** `st-free-limit`: the 3rd active vehicle on the free tier. */
    data object ShowFreeLimit : GarageEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GarageViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val pro: ProStatus,
) : ViewModel() {
    private val selectedId = MutableStateFlow<Long?>(null)

    private val cards: Flow<List<VehicleCardData>?> = repository.observeAll().flatMapLatest { vehicles ->
        if (vehicles.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(vehicles.map { v -> repository.observeEnergyLogs(v.id).map { logs -> card(v, logs) } }) { it.toList() }
        }
    }

    val state: StateFlow<GarageState> = combine(cards, selectedId) { list, selected ->
        val all = list ?: emptyList()
        GarageState(
            loading = false,
            active = all.filter { !it.vehicle.archived },
            archived = all.filter { it.vehicle.archived },
            selectedId = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarageState())

    val isPro: StateFlow<Boolean> = pro.isPro

    private val _events = Channel<GarageEvent>(Channel.BUFFERED)
    val events: Flow<GarageEvent> = _events.receiveAsFlow()

    private fun card(vehicle: Vehicle, logs: List<com.postpole.wheelfile.data.db.EnergyLog>) = VehicleCardData(
        vehicle = vehicle,
        odometer = VehicleSummary.latestOdometer(logs),
        streams = VehicleSummary.streams(logs, vehicle),
    )

    fun select(vehicleId: Long) {
        selectedId.value = vehicleId
    }

    /** Opens the add-vehicle flow, or the free-limit dialog when a 3rd active vehicle would be added. */
    fun onAddVehicle() {
        viewModelScope.launch {
            val active = repository.observeActiveCount().first()
            _events.send(
                if (VehicleCap.canAddActive(active, pro.isPro.value)) GarageEvent.OpenAddVehicle else GarageEvent.ShowFreeLimit,
            )
        }
    }

    /** "Archive a vehicle" from the free-limit dialog: archives [vehicleId], then continues to add. */
    fun archiveThenAdd(vehicleId: Long) {
        viewModelScope.launch {
            repository.archive(vehicleId)
            _events.send(GarageEvent.OpenAddVehicle)
        }
    }

    fun archive(vehicleId: Long) {
        viewModelScope.launch { repository.archive(vehicleId) }
    }

    /** Restores an archived vehicle; at the free cap this shows the free-limit dialog instead. */
    fun unarchive(vehicleId: Long) {
        viewModelScope.launch {
            if (!repository.unarchive(vehicleId, pro.isPro.value)) _events.send(GarageEvent.ShowFreeLimit)
        }
    }
}
