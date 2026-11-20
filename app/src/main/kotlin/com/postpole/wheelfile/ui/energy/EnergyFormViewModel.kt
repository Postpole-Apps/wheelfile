package com.postpole.wheelfile.ui.energy

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.SaveKind
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.data.Clock
import com.postpole.wheelfile.data.LocaleProvider
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.ChargerType
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.economy.EconomyEngine
import com.postpole.wheelfile.domain.economy.EnergyEntry
import com.postpole.wheelfile.domain.economy.VehicleSummary
import com.postpole.wheelfile.domain.economy.toEntry
import com.postpole.wheelfile.domain.fillup.NumberText
import com.postpole.wheelfile.domain.fillup.PriceField
import com.postpole.wheelfile.domain.fillup.PriceInputs
import com.postpole.wheelfile.domain.fillup.PriceMath
import com.postpole.wheelfile.domain.units.UnitConversion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class EnergyFormState(
    val vehicle: Vehicle? = null,
    val stream: EnergyStream = EnergyStream.LIQUID,
    val dateTime: Long = 0,
    val odometer: String = "",
    val quantity: String = "",
    val price: String = "",
    val total: String = "",
    /** Fields in the order they were edited, most recent last. */
    val editOrder: List<PriceField> = emptyList(),
    /** The field that is currently calculated for the user, shown with the auto chip. */
    val derived: PriceField? = null,
    val isFull: Boolean = true,
    val missedPrevious: Boolean = false,
    val fuelGrade: String = "",
    val station: String = "",
    val note: String = "",
    val chargerType: ChargerType? = null,
    val socStart: String = "",
    val socEnd: String = "",
    /** Highest odometer already logged, shown as a hint (vehicle's distance unit). */
    val lastOdometer: Double? = null,
    /** Soft warning: the odometer contradicts the date order of existing entries. Never blocks saving. */
    val odometerWarning: Boolean = false,
    val odometerError: Boolean = false,
    val quantityError: Boolean = false,
    val socError: Boolean = false,
    val saving: Boolean = false,
    /** Saving failed; the entry is kept so the user can retry (`st-error-save`). */
    val saveFailed: Boolean = false,
    /** Archived vehicles are read-only. */
    val readOnly: Boolean = false,
) {
    val isCharge: Boolean get() = stream == EnergyStream.ELECTRIC
}

sealed interface EnergyFormEvent {
    data object Saved : EnergyFormEvent
}

/** Fill-up and Charge form (spec §2.2). The same logic serves both; [EnergyFormState.stream] tells them apart. */
@HiltViewModel
class EnergyFormViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val adsPolicy: AdsPolicy,
    private val analytics: Analytics,
    private val locales: LocaleProvider,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val vehicleId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_VEHICLE_ID))
    private val locale: Locale get() = locales.locale()

    private val _state = MutableStateFlow(
        EnergyFormState(
            stream = savedStateHandle.get<String>(ARG_STREAM)?.let { name -> EnergyStream.entries.firstOrNull { it.name == name } }
                ?: EnergyStream.LIQUID,
            dateTime = clock.now(),
        ),
    )
    val state: StateFlow<EnergyFormState> = _state.asStateFlow()

    private val _events = Channel<EnergyFormEvent>(Channel.BUFFERED)
    val events: Flow<EnergyFormEvent> = _events.receiveAsFlow()

    private var existing: List<EnergyEntry> = emptyList()

    init {
        viewModelScope.launch {
            val vehicle = repository.get(vehicleId) ?: return@launch
            val logs = repository.energyLogs(vehicleId)
            existing = logs.map { it.toEntry(vehicle) }
            _state.update {
                it.copy(
                    vehicle = vehicle,
                    lastOdometer = VehicleSummary.latestOdometer(logs),
                    readOnly = vehicle.archived,
                )
            }
        }
    }

    private val digits: Int get() = VehicleSummary.fractionDigits(_state.value.vehicle?.currency ?: "USD")

    fun onDate(epochMillis: Long) {
        _state.update { it.copy(dateTime = epochMillis) }
        recheckOdometer()
    }

    fun onOdometer(text: String) {
        if (!NumberText.isValidInput(text)) return
        _state.update { it.copy(odometer = text, odometerError = false) }
        recheckOdometer()
    }

    fun onQuantity(text: String) = onPriceField(PriceField.VOLUME, text)
    fun onPrice(text: String) = onPriceField(PriceField.PRICE, text)
    fun onTotal(text: String) = onPriceField(PriceField.TOTAL, text)

    private fun onPriceField(field: PriceField, text: String) {
        if (!NumberText.isValidInput(text)) return
        _state.update { s ->
            val typed = s.withText(field, text)
            if (text.isEmpty()) {
                // Clearing an input also clears the value that was calculated from it, so nothing reappears.
                val cleared = s.derived?.takeIf { it != field }?.let { typed.withText(it, "") } ?: typed
                cleared.copy(editOrder = cleared.editOrder.filter { it != field }, derived = null)
            } else {
                recalculate(typed.copy(editOrder = (typed.editOrder + field).takeLast(MAX_ORDER)), edited = field)
            }
        }
    }

    /**
     * Applies [PriceMath] to [s]: fills the derived field's text, or clears it again when fewer than two
     * usable inputs remain. [edited] is the field the user just typed in and is never overwritten.
     */
    private fun recalculate(s: EnergyFormState, edited: PriceField): EnergyFormState {
        val inputs = PriceInputs(
            total = NumberText.parse(s.total),
            price = NumberText.parse(s.price),
            volume = NumberText.parse(s.quantity),
        )
        val result = PriceMath.resolve(inputs, s.editOrder, digits)
        val derived = result.derived
        if (derived == null || derived == edited) {
            val stale = s.derived?.takeIf { it != edited }
            return (stale?.let { s.withText(it, "") } ?: s).copy(derived = null)
        }
        val value = when (derived) {
            PriceField.TOTAL -> result.total
            PriceField.PRICE -> result.price
            PriceField.VOLUME -> result.volume
        } ?: return s
        return s.withText(derived, formatField(derived, value)).copy(derived = derived)
    }

    private fun EnergyFormState.withText(field: PriceField, text: String): EnergyFormState = when (field) {
        PriceField.TOTAL -> copy(total = text)
        PriceField.PRICE -> copy(price = text)
        PriceField.VOLUME -> copy(quantity = text, quantityError = false)
    }

    private fun formatField(field: PriceField, value: Double): String = when (field) {
        PriceField.TOTAL -> NumberText.format(value, digits, digits, locale)
        PriceField.PRICE -> NumberText.format(value, minOf(2, PriceMath.PRICE_DECIMALS), PriceMath.PRICE_DECIMALS, locale)
        PriceField.VOLUME -> NumberText.format(value, 0, PriceMath.VOLUME_DECIMALS, locale)
    }

    fun onFull(value: Boolean) = _state.update { it.copy(isFull = value) }
    fun onMissed(value: Boolean) = _state.update { it.copy(missedPrevious = value) }
    fun onFuelGrade(value: String) = _state.update { it.copy(fuelGrade = value) }
    fun onStation(value: String) = _state.update { it.copy(station = value) }
    fun onNote(value: String) = _state.update { it.copy(note = value) }
    fun onCharger(value: ChargerType?) = _state.update { it.copy(chargerType = value) }

    fun onSocStart(text: String) {
        if (text.all(Char::isDigit) && text.length <= 3) _state.update { it.copy(socStart = text, socError = false) }
    }

    fun onSocEnd(text: String) {
        if (text.all(Char::isDigit) && text.length <= 3) _state.update { it.copy(socEnd = text, socError = false) }
    }

    private fun recheckOdometer() {
        val s = _state.value
        val vehicle = s.vehicle ?: return
        val odometer = NumberText.parse(s.odometer)
        val warning = odometer != null && EconomyEngine.odometerLooksWrong(
            existing,
            EnergyEntry(
                id = 0,
                dateTime = s.dateTime,
                odometerKm = UnitConversion.toKm(odometer, vehicle.distanceUnit),
                quantity = 0.0,
                isFull = true,
            ),
        )
        _state.update { it.copy(odometerWarning = warning) }
    }

    fun save() {
        val s = _state.value
        val vehicle = s.vehicle ?: return
        if (s.saving || s.readOnly) return
        val odometer = NumberText.parse(s.odometer)
        val quantity = NumberText.parse(s.quantity)
        val socStart = s.socStart.toIntOrNull()
        val socEnd = s.socEnd.toIntOrNull()
        val odometerError = odometer == null
        val quantityError = quantity == null || quantity <= 0.0
        val socError = s.isCharge && ((socStart != null && socStart > 100) || (socEnd != null && socEnd > 100))
        if (odometerError || quantityError || socError) {
            _state.update { it.copy(odometerError = odometerError, quantityError = quantityError, socError = socError) }
            return
        }
        val total = NumberText.parse(s.total)
        val now = clock.now()
        val log = EnergyLog(
            vehicleId = vehicle.id,
            stream = s.stream,
            dateTime = s.dateTime,
            odometer = odometer!!,
            quantity = quantity!!,
            // The per-unit price is not persisted: money is stored as exact minor units, and a price such as
            // 1.859 per litre does not fit. It is derived on display as total / quantity.
            pricePerUnitMinor = null,
            totalMinor = total?.takeIf { it > 0.0 }?.let { PriceMath.toMinor(it, digits) },
            isFull = s.isFull,
            missedPrevious = s.missedPrevious,
            fuelGrade = s.fuelGrade.trim().ifBlank { null },
            chargerType = if (s.isCharge) s.chargerType else null,
            socStart = if (s.isCharge) socStart else null,
            socEnd = if (s.isCharge) socEnd else null,
            station = s.station.trim().ifBlank { null },
            note = s.note.trim().ifBlank { null },
            createdAt = now,
            updatedAt = now,
        )
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                if (repository.addEnergyLog(log)) {
                    adsPolicy.onSaved(if (s.isCharge) SaveKind.EV_CHARGE else SaveKind.FILL_UP)
                    _events.send(EnergyFormEvent.Saved)
                } else {
                    _state.update { it.copy(saveFailed = true) }
                }
            } catch (e: Exception) {
                analytics.recordNonFatal(e)
                _state.update { it.copy(saveFailed = true) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun dismissSaveError() = _state.update { it.copy(saveFailed = false) }

    companion object {
        const val ARG_VEHICLE_ID = "vehicleId"
        const val ARG_STREAM = "stream"
        private const val MAX_ORDER = 6
    }
}
