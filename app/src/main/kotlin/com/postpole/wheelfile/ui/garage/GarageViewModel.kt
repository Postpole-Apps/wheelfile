package com.postpole.wheelfile.ui.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.ActiveProPrompt
import com.postpole.wheelfile.ads.AdsConfigSource
import com.postpole.wheelfile.ads.ProPromptCoordinator
import com.postpole.wheelfile.ads.ProPromptDismissMethod
import com.postpole.wheelfile.ads.ProPromptRestPoint
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GarageUiState {
    /** Skeleton at final sizes. No ad is requested yet: we do not know whether a vehicle exists. */
    data object Loading : GarageUiState

    /** First run, no vehicles at all: ad-free (DECISIONS.md item 5). */
    data object Empty : GarageUiState

    data class Content(
        val active: List<VehicleData>,
        val archived: List<Vehicle>,
        val selectedId: Long?,
    ) : GarageUiState {
        val selected: VehicleData? get() = active.firstOrNull { it.vehicle.id == selectedId } ?: active.firstOrNull()
        val others: List<VehicleData> get() = active.filter { it.vehicle.id != selected?.vehicle?.id }
    }
}

/** The Pro card the Garage shows now. [count] is the `entries_milestone` threshold that fired. */
data class GarageProPrompt(val prompt: ActiveProPrompt, val count: Int)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GarageViewModel internal constructor(
    private val repo: VehicleRepository,
    private val proPrompts: ProPromptCoordinator? = null,
    /** Play's formatted Pro price; null until loaded. */
    val price: StateFlow<String?> = MutableStateFlow(null),
    private val entriesThreshold: () -> Int = { 0 },
) : ViewModel() {

    @Inject
    constructor(
        repo: VehicleRepository,
        proPrompts: ProPromptCoordinator,
        billing: BillingRepository,
        configSource: AdsConfigSource,
    ) : this(repo, proPrompts, billing.price, { configSource.config.value.proPrompt.coerced().entriesThreshold })

    private val selectedId = MutableStateFlow<Long?>(null)

    private val _proPrompt = MutableStateFlow<GarageProPrompt?>(null)

    /** The Pro card for the slot between the hero card and the other vehicles, or null. */
    val proPrompt: StateFlow<GarageProPrompt?> = _proPrompt.asStateFlow()

    private var evaluating = false

    val state: StateFlow<GarageUiState> = repo.vehicles.observeAll()
        .flatMapLatest<List<Vehicle>, GarageUiState> { all ->
            if (all.isEmpty()) {
                flowOf(GarageUiState.Empty)
            } else {
                val active = all.filter { !it.archived }
                val archived = all.filter { it.archived }
                val activeData = if (active.isEmpty()) flowOf(emptyList()) else combine(active.map(repo::observeData)) { it.toList() }
                combine(activeData, selectedId) { data, sel -> GarageUiState.Content(data, archived, sel) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarageUiState.Loading)

    fun select(id: Long) {
        selectedId.value = id
    }

    /**
     * The Garage settled with its vehicles on screen, a rest point. The Garage has the card slot, so a pending card,
     * including a `remove_ads` owner's converted sheet, can show here. The policy decides; one prompt per session.
     */
    fun onRestPoint() {
        val coordinator = proPrompts ?: return
        if (_proPrompt.value != null || evaluating) return
        evaluating = true
        viewModelScope.launch {
            try {
                val shown = coordinator.onRestPoint(ProPromptRestPoint.GARAGE)
                if (shown != null) _proPrompt.value = GarageProPrompt(shown, entriesThreshold())
            } finally {
                evaluating = false
            }
        }
    }

    /**
     * The Garage resumed. It is a rest point only when the user returns after a completed save or finish; the launch
     * resume, the first resume of a new session and plain navigation do not evaluate and leave a pending card pending.
     */
    fun onResumed() {
        if (proPrompts?.consumeSaveCompleted() == true) onRestPoint()
    }

    /** The card's X. Cards have no swipe-dismiss. */
    fun dismissProPrompt() {
        val shown = _proPrompt.value ?: return
        _proPrompt.value = null
        viewModelScope.launch { proPrompts?.onDismissed(shown.prompt, ProPromptDismissMethod.CLOSE) }
    }

    /** The CTA opens the paywall (the caller navigates). The card stays: Back from the paywall returns to it. */
    fun onProPromptCta() {
        _proPrompt.value?.let { proPrompts?.onCtaTapped(it.prompt) }
    }
}
