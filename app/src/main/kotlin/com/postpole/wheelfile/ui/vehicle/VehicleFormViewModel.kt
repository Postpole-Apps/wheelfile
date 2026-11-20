package com.postpole.wheelfile.ui.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.analytics.AnalyticsParams
import com.postpole.wheelfile.billing.ProStatus
import com.postpole.wheelfile.data.AddVehicleResult
import com.postpole.wheelfile.data.LocaleProvider
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.units.EconomyUnits
import com.postpole.wheelfile.domain.vehicle.LocaleDefaults
import com.postpole.wheelfile.domain.vehicle.volumeUnitFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Year
import javax.inject.Inject

enum class VehicleFormStep { DETAILS, UNITS }

data class VehicleFormState(
    val step: VehicleFormStep = VehicleFormStep.DETAILS,
    val name: String = "",
    val type: VehicleType = VehicleType.CAR,
    val powertrain: Powertrain = Powertrain.PETROL,
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val plate: String = "",
    val vin: String = "",
    val distance: DistanceUnit = DistanceUnit.KM,
    val volume: VolumeUnit = VolumeUnit.L,
    val economy: EconomyUnit = EconomyUnit.L_PER_100KM,
    val currency: String = "USD",
    val nameError: Boolean = false,
    val yearError: Boolean = false,
    val saving: Boolean = false,
) {
    /** Soft hint only: a VIN is normally 17 characters. Never blocks saving. */
    val vinLooksOdd: Boolean get() = vin.isNotBlank() && vin.trim().length != 17
}

sealed interface VehicleFormEvent {
    data class Saved(val vehicleId: Long, val onboarding: Boolean) : VehicleFormEvent

    /** Adding would be a 3rd active vehicle on the free tier. */
    data object LimitReached : VehicleFormEvent
}

/** Add-vehicle form: step 1 details, step 2 units. Also the second and third onboarding steps. */
@HiltViewModel
class VehicleFormViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val settings: SettingsRepository,
    private val pro: ProStatus,
    private val analytics: Analytics,
    private val locales: LocaleProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val onboarding: Boolean = savedStateHandle.get<Boolean>(ARG_ONBOARDING) ?: false

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<VehicleFormState> = _state.asStateFlow()

    private val _events = Channel<VehicleFormEvent>(Channel.BUFFERED)
    val events: Flow<VehicleFormEvent> = _events.receiveAsFlow()

    init {
        // The default currency is the user's saved choice or the locale's; prefill it once it is known.
        viewModelScope.launch {
            val currency = settings.defaultCurrency.first()
            _state.update { it.copy(currency = currency) }
        }
    }

    private fun initialState(): VehicleFormState {
        val defaults = LocaleDefaults.forLocale(locales.locale())
        return VehicleFormState(
            distance = defaults.distance,
            volume = defaults.volume,
            economy = defaults.fuelEconomy,
        )
    }

    fun onName(value: String) = _state.update { it.copy(name = value, nameError = false) }
    fun onType(value: VehicleType) = _state.update { it.copy(type = value) }
    fun onMake(value: String) = _state.update { it.copy(make = value) }
    fun onModel(value: String) = _state.update { it.copy(model = value) }
    fun onYear(value: String) = _state.update { it.copy(year = value.filter(Char::isDigit).take(4), yearError = false) }
    fun onPlate(value: String) = _state.update { it.copy(plate = value) }
    fun onVin(value: String) = _state.update { it.copy(vin = value.uppercase().filter { c -> c.isLetterOrDigit() }.take(17)) }

    fun onPowertrain(value: Powertrain) = _state.update { s ->
        val defaults = LocaleDefaults.forLocale(locales.locale())
        val options = EconomyUnits.optionsFor(value)
        s.copy(
            powertrain = value,
            volume = value.volumeUnitFor(s.volume),
            economy = if (s.economy in options) {
                s.economy
            } else {
                EconomyUnits.defaultFor(value, defaults.fuelEconomy, defaults.electricEconomy)
            },
        )
    }

    fun onDistance(value: DistanceUnit) = _state.update { it.copy(distance = value) }
    fun onVolume(value: VolumeUnit) = _state.update { it.copy(volume = value) }
    fun onEconomy(value: EconomyUnit) = _state.update { it.copy(economy = value) }
    fun onCurrency(code: String) = _state.update { it.copy(currency = code) }

    /** Validates step 1 and moves to the Units step. */
    fun next() {
        val s = _state.value
        val nameError = s.name.isBlank()
        val yearError = !isYearValid(s.year)
        if (nameError || yearError) {
            _state.update { it.copy(nameError = nameError, yearError = yearError) }
            return
        }
        _state.update { it.copy(step = VehicleFormStep.UNITS) }
    }

    fun back() = _state.update { it.copy(step = VehicleFormStep.DETAILS) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.name.isBlank() || !isYearValid(s.year)) {
            _state.update {
                it.copy(step = VehicleFormStep.DETAILS, nameError = s.name.isBlank(), yearError = !isYearValid(s.year))
            }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                when (val result = repository.add(s.toVehicle(), pro.isPro.value)) {
                    is AddVehicleResult.Added -> {
                        if (onboarding) {
                            settings.setOnboardingDone()
                            analytics.logEvent(AnalyticsEvents.ONBOARDING_COMPLETE)
                        }
                        analytics.logEvent(
                            AnalyticsEvents.VEHICLE_ADDED,
                            mapOf(
                                AnalyticsParams.POWERTRAIN to s.powertrain.name,
                                AnalyticsParams.UNIT_SYSTEM to if (s.distance == DistanceUnit.KM) "metric" else "imperial",
                            ),
                        )
                        _events.send(VehicleFormEvent.Saved(result.id, onboarding))
                    }
                    AddVehicleResult.LimitReached -> _events.send(VehicleFormEvent.LimitReached)
                }
            } catch (e: Exception) {
                analytics.recordNonFatal(e)
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private fun isYearValid(text: String): Boolean {
        if (text.isBlank()) return true
        val year = text.toIntOrNull() ?: return false
        return year in 1886..(Year.now().value + 1)
    }

    private fun VehicleFormState.toVehicle(): Vehicle = Vehicle(
        name = name.trim(),
        type = type,
        powertrain = powertrain,
        make = make.trim().ifBlank { null },
        model = model.trim().ifBlank { null },
        year = year.toIntOrNull(),
        plate = plate.trim().ifBlank { null },
        vin = vin.trim().ifBlank { null },
        distanceUnit = distance,
        volumeUnit = volume,
        economyDisplay = economy,
        currency = currency,
        createdAt = 0,
        updatedAt = 0,
    )

    companion object {
        const val ARG_ONBOARDING = "onboarding"
    }
}
