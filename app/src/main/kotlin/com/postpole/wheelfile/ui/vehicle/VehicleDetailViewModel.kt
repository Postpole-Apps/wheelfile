package com.postpole.wheelfile.ui.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.vehicle.VehicleLimit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.postpole.wheelfile.domain.history.HistoryFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VehicleDetailUiState(
    /** Null while loading. */
    val data: VehicleData? = null,
    val switcher: List<Vehicle> = emptyList(),
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
    val showFreeLimit: Boolean = false,
    val loaded: Boolean = false,
) {
    val vehicle: Vehicle? get() = data?.vehicle
    val readOnly: Boolean get() = vehicle?.archived == true
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleDetailViewModel internal constructor(
    private val repo: VehicleRepository,
    private val vehicleId: Long,
    private val isPro: () -> Boolean,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        savedState: SavedStateHandle,
        repo: VehicleRepository,
        billing: BillingRepository,
    ) : this(repo, checkNotNull(savedState.get<Long>("vehicleId")), { billing.isPro.value }, System::currentTimeMillis)

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val freeLimit = MutableStateFlow(false)

    val state: StateFlow<VehicleDetailUiState> = repo.vehicles.observe(vehicleId)
        .flatMapLatest { v -> if (v == null) flowOf<VehicleData?>(null) else repo.observeData(v) }
        .let { data ->
            combine(data, repo.vehicles.observeAll(), filter, freeLimit) { d, all, f, limit ->
                VehicleDetailUiState(
                    data = d,
                    switcher = all.filter { !it.archived },
                    historyFilter = f,
                    showFreeLimit = limit,
                    loaded = true,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleDetailUiState())

    fun setFilter(f: HistoryFilter) {
        filter.value = f
    }

    fun archive() {
        viewModelScope.launch { repo.setArchived(vehicleId, true, clock()) }
    }

    /** Restoring counts against the 2-vehicle free cap, so it can open the free-limit dialog. */
    fun restore() {
        viewModelScope.launch {
            if (VehicleLimit.canAddActive(repo.vehicles.activeCount(), isPro())) {
                repo.setArchived(vehicleId, false, clock())
            } else {
                freeLimit.update { true }
            }
        }
    }

    fun dismissFreeLimit() {
        freeLimit.value = false
    }
}
