package com.postpole.wheelfile.ui.document

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.analytics.AnalyticsParams
import com.postpole.wheelfile.data.DocumentRepository
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.domain.document.DocumentPreset
import com.postpole.wheelfile.domain.document.PresetDue
import com.postpole.wheelfile.domain.document.PresetRegion
import com.postpole.wheelfile.domain.document.RegionPresets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

/** One row of the preset offer: the preset, whether it is ticked and the date it would suggest. */
data class PresetRow(val preset: DocumentPreset, val selected: Boolean, val due: PresetDue)

data class PresetsUiState(
    val vehicle: Vehicle? = null,
    val region: PresetRegion = PresetRegion.GENERIC,
    /** First registration date as epoch millis at the start of the day; presets that depend on vehicle age need it. */
    val firstRegistration: Long? = null,
    val rows: List<PresetRow> = emptyList(),
    val saving: Boolean = false,
) {
    val selectedCount: Int get() = rows.count { it.selected }
}

sealed interface PresetsEvent {
    data object Done : PresetsEvent
}

/**
 * Regional document presets (s01d-region-presets): suggestions only, always labelled "Suggested schedule; check your
 * local authority". Used as onboarding step 3 and from the Documents tab. The region starts at the device locale.
 */
@HiltViewModel
class PresetsViewModel internal constructor(
    private val vehicles: VehicleDao,
    private val repo: DocumentRepository,
    private val analytics: Analytics,
    private val vehicleId: Long,
    locale: Locale,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    @Inject
    constructor(
        savedState: SavedStateHandle,
        vehicles: VehicleDao,
        repo: DocumentRepository,
        analytics: Analytics,
    ) : this(vehicles, repo, analytics, checkNotNull(savedState.get<Long>("vehicleId")), Locale.getDefault())

    private val _state = MutableStateFlow(
        RegionPresets.regionFor(locale).let { region ->
            rebuild(PresetsUiState(region = region), defaultSelection(region), zone)
        },
    )
    val state: StateFlow<PresetsUiState> = _state.asStateFlow()

    private val _events = Channel<PresetsEvent>(Channel.BUFFERED)
    val events: Flow<PresetsEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val vehicle = vehicles.get(vehicleId) ?: return@launch
            _state.update { it.copy(vehicle = vehicle) }
        }
    }

    private fun currentSelection(): Set<String> = _state.value.rows.filter { it.selected }.map { it.preset.code }.toSet()

    fun setRegion(region: PresetRegion) = _state.update { rebuild(it.copy(region = region), defaultSelection(region), zone) }

    fun toggle(code: String) = _state.update {
        val now = currentSelection()
        rebuild(it, if (code in now) now - code else now + code, zone)
    }

    fun setFirstRegistration(epochMillis: Long) = _state.update {
        val day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        rebuild(it.copy(firstRegistration = day), currentSelection(), zone)
    }

    fun clearFirstRegistration() = _state.update { rebuild(it.copy(firstRegistration = null), currentSelection(), zone) }

    /** Creates one document per ticked preset. [titles] maps preset codes to their localized titles. */
    fun apply(titles: Map<String, String>) {
        val s = _state.value
        val vehicle = s.vehicle ?: return
        if (s.saving || vehicle.archived) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val chosen = s.rows.filter { it.selected }
                chosen.forEach { row ->
                    val expiry = row.due.dueDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
                    repo.save(
                        VehicleDocument(
                            vehicleId = vehicle.id,
                            type = row.preset.type,
                            title = titles[row.preset.code] ?: row.preset.code,
                            expiryDate = expiry,
                        ),
                        presetCode = row.preset.code,
                    )
                }
                analytics.logEvent(
                    AnalyticsEvents.PRESETS_ADDED,
                    mapOf("region" to s.region.code, AnalyticsParams.ITEM_COUNT to chosen.size.toString()),
                )
            } catch (e: Exception) {
                analytics.recordNonFatal(e)
            }
            _events.send(PresetsEvent.Done)
        }
    }

    fun skip() {
        _events.trySend(PresetsEvent.Done)
    }
}

/** Region-specific presets start ticked; the shared ones (insurance, licence) are offered but start off. */
private fun defaultSelection(region: PresetRegion): Set<String> =
    RegionPresets.forRegion(region).filter { it.region == region }.map { it.code }.toSet()

private fun rebuild(state: PresetsUiState, selected: Set<String>, zone: ZoneId): PresetsUiState {
    val registration = state.firstRegistration?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    return state.copy(
        rows = RegionPresets.forRegion(state.region).map {
            PresetRow(it, it.code in selected, RegionPresets.firstDue(it, registration))
        },
    )
}
