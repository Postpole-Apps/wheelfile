package com.postpole.wheelfile.ui.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.data.Clock
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.economy.CostSummary
import com.postpole.wheelfile.domain.economy.StreamEconomy
import com.postpole.wheelfile.domain.economy.VehicleSummary
import com.postpole.wheelfile.domain.history.HistoryBuilder
import com.postpole.wheelfile.domain.history.HistoryFilter
import com.postpole.wheelfile.domain.history.MonthGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class VehicleDetailState(
    val loading: Boolean = true,
    val vehicle: Vehicle? = null,
    val logs: List<EnergyLog> = emptyList(),
    val streams: List<StreamEconomy> = emptyList(),
    val costs: CostSummary? = null,
    val odometer: Double? = null,
    val recent: List<EnergyLog> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val history: List<MonthGroup> = emptyList(),
    /** Other active vehicles, for the switcher. */
    val switchTargets: List<Vehicle> = emptyList(),
) {
    val readOnly: Boolean get() = vehicle?.archived == true
}

/** Vehicle detail: Overview and History tabs share one state (Documents and Reports arrive in later milestones). */
@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val vehicleId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_VEHICLE_ID))
    private val filter = MutableStateFlow(HistoryFilter.ALL)

    private data class Data(
        val vehicle: Vehicle?,
        val logs: List<EnergyLog>,
        val services: List<ServiceRecord>,
        val expenses: List<Expense>,
        val all: List<Vehicle>,
    )

    private val data = combine(
        repository.observe(vehicleId),
        repository.observeEnergyLogs(vehicleId),
        repository.observeServices(vehicleId),
        repository.observeExpenses(vehicleId),
        repository.observeAll(),
    ) { vehicle, logs, services, expenses, all -> Data(vehicle, logs, services, expenses, all) }

    val state: StateFlow<VehicleDetailState> = combine(data, filter) { d, f ->
        val vehicle = d.vehicle ?: return@combine VehicleDetailState(loading = false)
        VehicleDetailState(
            loading = false,
            vehicle = vehicle,
            logs = d.logs,
            streams = VehicleSummary.streams(d.logs, vehicle),
            costs = VehicleSummary.costs(vehicle, d.logs, d.services, d.expenses, clock.now()),
            odometer = VehicleSummary.latestOdometer(d.logs),
            recent = d.logs.sortedWith(compareByDescending<EnergyLog> { it.dateTime }.thenByDescending { it.id }).take(RECENT),
            filter = f,
            history = HistoryBuilder.build(vehicle, d.logs, d.services, d.expenses, f),
            switchTargets = d.all.filter { !it.archived && it.id != vehicleId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleDetailState())

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    companion object {
        const val ARG_VEHICLE_ID = "vehicleId"
        private const val RECENT = 3
    }
}
