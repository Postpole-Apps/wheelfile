package com.postpole.wheelfile.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.SaveKind
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.analytics.AnalyticsParams
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.ChargerType
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.entry.EntryForm
import com.postpole.wheelfile.domain.entry.EntryFormState
import com.postpole.wheelfile.domain.entry.FormValidation
import com.postpole.wheelfile.domain.entry.PriceField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EntryUiState(
    val vehicle: Vehicle? = null,
    val form: EntryFormState,
    val validation: FormValidation? = null,
    /** Field errors only show after the first Save attempt. */
    val showErrors: Boolean = false,
    /** Last saved reading (highest odometer), shown as a hint. */
    val lastOdometer: Double? = null,
    val saving: Boolean = false,
) {
    val isCharge: Boolean get() = form.isCharge

    /** The Save button reads "Save anyway" once the odometer warning is showing and was acknowledged by pressing Save. */
    val saveAnyway: Boolean get() = validation?.warning != null && form.warningConfirmed
}

sealed interface EntryEvent {
    data object Saved : EntryEvent
    data object SaveFailed : EntryEvent
}

@HiltViewModel
class EntryFormViewModel internal constructor(
    private val repo: VehicleRepository,
    private val adsPolicy: AdsPolicy,
    private val analytics: Analytics,
    private val vehicleId: Long,
    isCharge: Boolean,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        savedState: SavedStateHandle,
        repo: VehicleRepository,
        adsPolicy: AdsPolicy,
        analytics: Analytics,
    ) : this(
        repo, adsPolicy, analytics,
        vehicleId = checkNotNull(savedState.get<Long>("vehicleId")),
        isCharge = savedState.get<Boolean>("charge") ?: false,
        clock = System::currentTimeMillis,
    )

    private val _state = MutableStateFlow(EntryUiState(form = EntryFormState(isCharge = isCharge, dateTime = clock())))
    val state: StateFlow<EntryUiState> = _state.asStateFlow()

    private val _events = Channel<EntryEvent>(Channel.BUFFERED)
    val events: Flow<EntryEvent> = _events.receiveAsFlow()

    private var existing: List<EnergyLog> = emptyList()

    init {
        viewModelScope.launch {
            val vehicle = repo.vehicles.get(vehicleId) ?: return@launch
            // The odometer is one reading per vehicle, so every stream counts for the soft warning.
            existing = repo.energyLogs.getAll(vehicleId)
            _state.update { it.copy(vehicle = vehicle, lastOdometer = existing.maxOfOrNull { l -> l.odometer }) }
            recompute()
        }
    }

    private fun update(block: (EntryFormState) -> EntryFormState) {
        _state.update { it.copy(form = block(it.form)) }
        recompute()
    }

    private fun recompute() {
        _state.update { s ->
            val v = s.vehicle ?: return@update s
            s.copy(validation = EntryForm.validate(s.form, v, existing))
        }
    }

    fun setDate(epochMillis: Long) = update { it.copy(dateTime = epochMillis, warningConfirmed = false) }
    fun setOdometer(t: String) = update { it.copy(odometer = t.take(12), warningConfirmed = false) }
    fun setQuantity(t: String) = update { EntryForm.withPriceEdit(it, PriceField.QUANTITY, t.take(12)) }
    fun setUnitPrice(t: String) = update { EntryForm.withPriceEdit(it, PriceField.UNIT_PRICE, t.take(12)) }
    fun setTotal(t: String) = update { EntryForm.withPriceEdit(it, PriceField.TOTAL, t.take(12)) }
    fun setFull(v: Boolean) = update { it.copy(isFull = v) }
    fun setMissed(v: Boolean) = update { it.copy(missedPrevious = v) }
    fun setFuelGrade(t: String) = update { it.copy(fuelGrade = t.take(40)) }
    fun setStation(t: String) = update { it.copy(station = t.take(80)) }
    fun setNote(t: String) = update { it.copy(note = t.take(500)) }
    fun setCharger(c: ChargerType?) = update { it.copy(charger = c) }
    fun setSocStart(t: String) = update { it.copy(socStart = t.filter(Char::isDigit).take(3)) }
    fun setSocEnd(t: String) = update { it.copy(socEnd = t.filter(Char::isDigit).take(3)) }

    /** True when the vehicle is archived: archived vehicles are read-only. */
    fun isReadOnly(): Boolean = _state.value.vehicle?.archived == true

    fun save() {
        val s = _state.value
        val vehicle = s.vehicle ?: return
        if (s.saving || vehicle.archived) return
        val validation = EntryForm.validate(s.form, vehicle, existing)
        _state.update { it.copy(validation = validation, showErrors = true) }
        if (!validation.isValid) return
        if (validation.warning != null && !s.form.warningConfirmed) {
            // First press only shows the soft warning; pressing Save again ("Save anyway") saves.
            update { it.copy(warningConfirmed = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repo.energyLogs.insert(EntryForm.toEnergyLog(s.form, vehicle, clock()))
                // A saved fill-up or charge starts the pump-session rule: no interstitial for the rest of the session.
                adsPolicy.onSaved(if (s.isCharge) SaveKind.EV_CHARGE else SaveKind.FILL_UP)
                analytics.logEvent(
                    if (s.isCharge) AnalyticsEvents.CHARGE_SAVED else AnalyticsEvents.FILLUP_SAVED,
                    mapOf(AnalyticsParams.POWERTRAIN to vehicle.powertrain.name.lowercase()),
                )
                _events.send(EntryEvent.Saved)
            } catch (e: Exception) {
                analytics.recordNonFatal(e)
                _state.update { it.copy(saving = false) }
                _events.send(EntryEvent.SaveFailed)
            }
        }
    }

}

/** Which form the primary FAB opens: Charge for an EV, Fill-up for everything else. A PHEV also gets a Charge action. */
fun Vehicle.defaultFormIsCharge(): Boolean = powertrain == Powertrain.ELECTRIC

fun Vehicle.supportsCharge(): Boolean = powertrain == Powertrain.ELECTRIC || powertrain == Powertrain.PHEV

fun Vehicle.supportsFillUp(): Boolean = powertrain != Powertrain.ELECTRIC
