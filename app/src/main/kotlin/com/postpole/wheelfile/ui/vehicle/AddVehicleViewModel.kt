package com.postpole.wheelfile.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.ProPromptCoordinator
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.analytics.AnalyticsParams
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.DocumentRepository
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.document.PresetPlan
import com.postpole.wheelfile.domain.document.Region
import com.postpole.wheelfile.domain.document.RegionPresets
import com.postpole.wheelfile.domain.units.LocaleDefaults
import com.postpole.wheelfile.domain.units.UnitChoice
import com.postpole.wheelfile.domain.vehicle.VehicleLimit
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

enum class AddVehicleStep { DETAILS, UNITS, REGION }

data class AddVehicleState(
    val step: AddVehicleStep = AddVehicleStep.DETAILS,
    /** Step 3: the region whose document presets are offered (from the device locale, never assumed) and the ticked ones. */
    val region: Region = Region.GENERIC,
    val selectedPresets: Set<String> = emptySet(),
    val name: String = "",
    val type: VehicleType = VehicleType.CAR,
    val powertrain: Powertrain = Powertrain.PETROL,
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val plate: String = "",
    val vin: String = "",
    val units: UnitChoice,
    val nameMissing: Boolean = false,
    val yearInvalid: Boolean = false,
    val vinSoftWarning: Boolean = false,
    /** Shown as the free-limit dialog (spec §5.1) when adding a 3rd active vehicle. */
    val showFreeLimit: Boolean = false,
    /** Active vehicles the user can archive to make room. */
    val archiveCandidates: List<Vehicle> = emptyList(),
    val showArchivePicker: Boolean = false,
    val saving: Boolean = false,
) {
    val isElectric: Boolean get() = powertrain == Powertrain.ELECTRIC
}

sealed interface AddVehicleEvent {
    data class Saved(val vehicleId: Long) : AddVehicleEvent
    data object OpenPro : AddVehicleEvent
}

@HiltViewModel
class AddVehicleViewModel internal constructor(
    private val repo: VehicleRepository,
    private val isPro: () -> Boolean,
    private val markOnboardingDone: suspend () -> Unit,
    private val analytics: Analytics,
    private val locale: Locale,
    /** Creates the documents for the presets ticked in step 3, once the vehicle has an id. */
    private val applyPresets: suspend (Vehicle, List<PresetPlan>) -> Unit = { _, _ -> },
    private val proPrompts: ProPromptCoordinator? = null,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        repo: VehicleRepository,
        settings: SettingsRepository,
        billing: BillingRepository,
        analytics: Analytics,
        documents: DocumentRepository,
        proPrompts: ProPromptCoordinator,
    ) : this(
        repo, { billing.isPro.value }, settings::setOnboardingDone, analytics, Locale.getDefault(),
        { vehicle, plans -> documents.applyPlans(vehicle, plans) }, proPrompts, System::currentTimeMillis,
    )

    private val _state = MutableStateFlow(
        AddVehicleState(units = LocaleDefaults.forLocale(locale), region = RegionPresets.regionFor(locale)),
    )
    val state: StateFlow<AddVehicleState> = _state.asStateFlow()

    private val _events = Channel<AddVehicleEvent>(Channel.BUFFERED)
    val events: Flow<AddVehicleEvent> = _events.receiveAsFlow()

    fun setName(v: String) = _state.update { it.copy(name = v.take(60), nameMissing = false) }
    fun setType(v: VehicleType) = _state.update { it.copy(type = v) }
    fun setMake(v: String) = _state.update { it.copy(make = v.take(40)) }
    fun setModel(v: String) = _state.update { it.copy(model = v.take(40)) }
    fun setYear(v: String) = _state.update { it.copy(year = v.filter(Char::isDigit).take(4), yearInvalid = false) }
    fun setPlate(v: String) = _state.update { it.copy(plate = v.take(16)) }
    fun setVin(v: String) = _state.update { it.copy(vin = v.uppercase().take(17), vinSoftWarning = false) }

    fun setPowertrain(p: Powertrain) = _state.update {
        // Powertrain decides which units make sense, so re-apply the locale defaults for the new powertrain.
        val base = LocaleDefaults.forLocale(locale, p)
        val volume = when (p) {
            Powertrain.CNG, Powertrain.HYDROGEN -> VolumeUnit.KG
            else -> if (it.units.volume == VolumeUnit.KG) base.volume else it.units.volume
        }
        it.copy(
            powertrain = p,
            units = it.units.copy(
                volume = volume,
                economy = LocaleDefaults.economyFor(it.units.distance, volume, locale.country, p),
            ),
        )
    }

    /** Changing the distance unit re-applies the matching economy default (km → L/100km, mi → MPG or mi/kWh). */
    fun setDistance(u: DistanceUnit) = _state.update {
        it.copy(
            units = it.units.copy(
                distance = u,
                economy = LocaleDefaults.economyFor(u, it.units.volume, locale.country, it.powertrain),
            ),
        )
    }

    fun setVolume(u: VolumeUnit) = _state.update { it.copy(units = it.units.copy(volume = u)) }
    fun setEconomy(u: EconomyUnit) = _state.update { it.copy(units = it.units.copy(economy = u)) }
    fun setCurrency(code: String) = _state.update { it.copy(units = it.units.copy(currency = code)) }

    /** Step 1 → 2. Validates the name and the free vehicle cap. */
    fun onContinue() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameMissing = true) }
            return
        }
        if (s.year.isNotEmpty() && s.year.toInt() !in MIN_YEAR..MAX_YEAR) {
            _state.update { it.copy(yearInvalid = true) }
            return
        }
        viewModelScope.launch {
            if (!withinCap()) {
                showFreeLimit()
                return@launch
            }
            _state.update { it.copy(step = AddVehicleStep.UNITS, vinSoftWarning = it.vin.isNotEmpty() && it.vin.length != 17) }
        }
    }

    /** Step 2 → 3: the suggested documents for the region. */
    fun onUnitsDone() = _state.update { it.copy(step = AddVehicleStep.REGION) }

    fun setRegion(r: Region) = _state.update { it.copy(region = r) }

    fun togglePreset(code: String) = _state.update {
        it.copy(selectedPresets = if (code in it.selectedPresets) it.selectedPresets - code else it.selectedPresets + code)
    }

    /** "Skip" on step 3: save the vehicle without any document. */
    fun skipPresets() {
        _state.update { it.copy(selectedPresets = emptySet()) }
        save()
    }

    /** Back from step 2 or 3. Returns false when nothing was handled and the screen should close instead. */
    fun onBack(): Boolean {
        val previous = when (_state.value.step) {
            AddVehicleStep.REGION -> AddVehicleStep.UNITS
            AddVehicleStep.UNITS -> AddVehicleStep.DETAILS
            AddVehicleStep.DETAILS -> return false
        }
        _state.update { it.copy(step = previous) }
        return true
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.name.isBlank()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                // Guard again: the count may have changed since step 1.
                if (!withinCap()) {
                    _state.update { it.copy(saving = false) }
                    showFreeLimit()
                    return@launch
                }
                val now = clock()
                val firstVehicle = repo.vehicles.getAll().isEmpty()
                val vehicle = Vehicle(
                    name = s.name.trim(),
                    type = s.type,
                    powertrain = s.powertrain,
                    make = s.make.trim().ifEmpty { null },
                    model = s.model.trim().ifEmpty { null },
                    year = s.year.toIntOrNull(),
                    plate = s.plate.trim().ifEmpty { null },
                    vin = s.vin.trim().ifEmpty { null },
                    distanceUnit = s.units.distance,
                    volumeUnit = s.units.volume,
                    economyDisplay = s.units.economy,
                    currency = s.units.currency,
                    createdAt = now,
                    updatedAt = now,
                )
                val id = repo.vehicles.insert(vehicle)
                val chosen = s.selectedPresets.mapNotNull(RegionPresets::find)
                if (chosen.isNotEmpty()) {
                    // The vehicle is saved either way; a failure to add documents never loses it.
                    try {
                        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
                        applyPresets(vehicle.copy(id = id), RegionPresets.plans(chosen, vehicle.year, today))
                        analytics.logEvent(AnalyticsEvents.PRESETS_ADDED, mapOf(AnalyticsParams.ITEM_COUNT to chosen.size))
                    } catch (e: Exception) {
                        analytics.recordNonFatal(e)
                    }
                }
                markOnboardingDone()
                if (firstVehicle) analytics.logEvent(AnalyticsEvents.ONBOARDING_COMPLETE)
                analytics.logEvent(
                    AnalyticsEvents.VEHICLE_ADDED,
                    mapOf(
                        AnalyticsParams.POWERTRAIN to s.powertrain.name.lowercase(),
                        AnalyticsParams.UNIT_SYSTEM to s.units.distance.name.lowercase(),
                    ),
                )
                // The 2nd active vehicle earns the next-session Garage card.
                proPrompts?.onVehicleAdded(repo.vehicles.activeCount())
                _events.send(AddVehicleEvent.Saved(id))
            } catch (e: Exception) {
                analytics.recordNonFatal(e)
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun dismissFreeLimit() = _state.update { it.copy(showFreeLimit = false, showArchivePicker = false) }

    fun seePro() {
        dismissFreeLimit()
        _events.trySend(AddVehicleEvent.OpenPro)
    }

    fun openArchivePicker() = _state.update { it.copy(showFreeLimit = false, showArchivePicker = true) }

    /** Archives [id] (read-only, does not count towards the cap) and carries on with the new vehicle. */
    fun archiveAndContinue(id: Long) {
        viewModelScope.launch {
            repo.setArchived(id, true, clock())
            _state.update { it.copy(showArchivePicker = false) }
            if (_state.value.step == AddVehicleStep.DETAILS) onContinue()
        }
    }

    private suspend fun withinCap(): Boolean = VehicleLimit.canAddActive(repo.vehicles.activeCount(), isPro())

    private suspend fun showFreeLimit() {
        val active = repo.vehicles.getAll().filter { !it.archived }
        _state.update { it.copy(showFreeLimit = true, archiveCandidates = active) }
    }

    private companion object {
        const val MIN_YEAR = 1886
        const val MAX_YEAR = 2100
    }
}
