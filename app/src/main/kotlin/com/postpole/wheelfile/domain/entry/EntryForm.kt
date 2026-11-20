package com.postpole.wheelfile.domain.entry

import com.postpole.wheelfile.data.db.ChargerType
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.economy.EconomyEngine
import com.postpole.wheelfile.domain.economy.toEntry
import java.math.BigDecimal
import java.util.Currency

/** Text-field state of the Fill-up and Charge forms. Only what the user typed is stored in the text fields. */
data class EntryFormState(
    val isCharge: Boolean,
    val dateTime: Long,
    val odometer: String = "",
    val quantity: String = "",
    val unitPrice: String = "",
    val total: String = "",
    /** Price fields in the order they were last edited, oldest first. */
    val editOrder: List<PriceField> = emptyList(),
    val isFull: Boolean = true,
    val missedPrevious: Boolean = false,
    val fuelGrade: String = "",
    val station: String = "",
    val note: String = "",
    val charger: ChargerType? = null,
    val socStart: String = "",
    val socEnd: String = "",
    /** The user already saw the odometer soft warning and pressed Save again. */
    val warningConfirmed: Boolean = false,
)

enum class FieldError { REQUIRED, INVALID, TOO_LARGE }

enum class FormField { ODOMETER, QUANTITY, UNIT_PRICE, TOTAL, SOC_START, SOC_END }

/** Soft odometer warning (spec §2.2): never blocks, only asks "Save anyway?". */
data class OdometerWarning(
    /** True when the reading is lower than an earlier entry; false when higher than a later one. */
    val tooLow: Boolean,
    /** The conflicting reading, in the vehicle's distance unit. */
    val reference: Double,
)

data class FormValidation(
    val errors: Map<FormField, FieldError>,
    val warning: OdometerWarning?,
    val price: PriceResult,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object EntryForm {

    /** Tolerates a decimal comma. Returns null for empty, malformed, negative or absurdly long input. */
    fun parseDecimal(text: String): BigDecimal? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty() || t.count { it == '.' } > 1 || t.length > 18) return null
        return t.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
    }

    fun stream(vehicle: Vehicle, isCharge: Boolean): EnergyStream = when {
        isCharge -> EnergyStream.ELECTRIC
        vehicle.powertrain == Powertrain.ELECTRIC -> EnergyStream.ELECTRIC
        vehicle.powertrain == Powertrain.HYDROGEN || vehicle.powertrain == Powertrain.CNG -> EnergyStream.GAS_KG
        else -> EnergyStream.LIQUID
    }

    fun fractionDigits(currency: String): Int =
        try {
            Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
        } catch (_: IllegalArgumentException) {
            2
        }

    fun fieldOf(f: PriceField) = when (f) {
        PriceField.QUANTITY -> FormField.QUANTITY
        PriceField.UNIT_PRICE -> FormField.UNIT_PRICE
        PriceField.TOTAL -> FormField.TOTAL
    }

    fun withPriceEdit(state: EntryFormState, field: PriceField, text: String): EntryFormState {
        val order = state.editOrder.filter { it != field } + field
        return when (field) {
            PriceField.QUANTITY -> state.copy(quantity = text, editOrder = order)
            PriceField.UNIT_PRICE -> state.copy(unitPrice = text, editOrder = order)
            PriceField.TOTAL -> state.copy(total = text, editOrder = order)
        }
    }

    /** Derived price values for display and saving. */
    fun price(state: EntryFormState, currency: String): PriceResult = PriceMath.resolve(
        mapOf(
            PriceField.QUANTITY to parseDecimal(state.quantity),
            PriceField.UNIT_PRICE to parseDecimal(state.unitPrice),
            PriceField.TOTAL to parseDecimal(state.total),
        ),
        state.editOrder,
        fractionDigits(currency),
    )

    /** Validates [state]. [existing] are the vehicle's saved logs of any stream. [editingId] is ignored among them. */
    fun validate(
        state: EntryFormState,
        vehicle: Vehicle,
        existing: List<EnergyLog>,
        editingId: Long = 0,
    ): FormValidation {
        val errors = linkedMapOf<FormField, FieldError>()
        val price = price(state, vehicle.currency)

        val odometer = parseDecimal(state.odometer)
        when {
            state.odometer.isBlank() -> errors[FormField.ODOMETER] = FieldError.REQUIRED
            odometer == null -> errors[FormField.ODOMETER] = FieldError.INVALID
            odometer.signum() == 0 -> errors[FormField.ODOMETER] = FieldError.INVALID
            odometer > MAX_ODOMETER -> errors[FormField.ODOMETER] = FieldError.TOO_LARGE
        }

        val quantity = price.quantity
        val enteredQuantity = parseDecimal(state.quantity)
        when {
            quantity != null && quantity.signum() > 0 -> Unit
            state.quantity.isBlank() -> errors[FormField.QUANTITY] = FieldError.REQUIRED
            enteredQuantity == null || enteredQuantity.signum() == 0 -> errors[FormField.QUANTITY] = FieldError.INVALID
        }
        if (enteredQuantity != null && enteredQuantity > MAX_QUANTITY) errors[FormField.QUANTITY] = FieldError.TOO_LARGE

        for ((field, text) in listOf(
            FormField.UNIT_PRICE to state.unitPrice,
            FormField.TOTAL to state.total,
        )) {
            if (text.isNotBlank() && parseDecimal(text).let { it == null || it.signum() == 0 }) {
                errors[field] = FieldError.INVALID
            }
        }
        // The derived total must still fit a Long of minor units.
        price.total?.let { if (it > MAX_MONEY) errors[FormField.TOTAL] = FieldError.TOO_LARGE }

        if (state.isCharge) {
            val start = state.socStart.toIntOrNull()
            val end = state.socEnd.toIntOrNull()
            if (state.socStart.isNotBlank() && (start == null || start !in 0..100)) errors[FormField.SOC_START] = FieldError.INVALID
            if (state.socEnd.isNotBlank() && (end == null || end !in 0..100)) errors[FormField.SOC_END] = FieldError.INVALID
        }

        val warning = if (FormField.ODOMETER !in errors && odometer != null) {
            odometerWarning(vehicle, existing.filter { it.id != editingId }, state.dateTime, odometer.toDouble())
        } else {
            null
        }
        return FormValidation(errors, warning, price)
    }

    /** Uses [EconomyEngine.odometerLooksWrong] and reports which neighbour conflicts. */
    fun odometerWarning(vehicle: Vehicle, existing: List<EnergyLog>, dateTime: Long, odometer: Double): OdometerWarning? {
        val candidate = EnergyLog(
            id = Long.MAX_VALUE, vehicleId = vehicle.id, stream = EnergyStream.LIQUID, dateTime = dateTime,
            odometer = odometer, quantity = 0.0, isFull = false, createdAt = 0, updatedAt = 0,
        ).toEntry(vehicle)
        if (!EconomyEngine.odometerLooksWrong(existing.map { it.toEntry(vehicle) }, candidate)) return null
        val earlier = existing.filter { it.dateTime < dateTime && it.odometer > odometer }
        return if (earlier.isNotEmpty()) {
            OdometerWarning(tooLow = true, reference = earlier.maxOf { it.odometer })
        } else {
            OdometerWarning(tooLow = false, reference = existing.filter { it.dateTime > dateTime && it.odometer < odometer }.minOf { it.odometer })
        }
    }

    /** Builds the entity. Call only when [validate] returned no errors. */
    fun toEnergyLog(state: EntryFormState, vehicle: Vehicle, now: Long, id: Long = 0): EnergyLog {
        val price = price(state, vehicle.currency)
        val digits = fractionDigits(vehicle.currency)
        val stream = stream(vehicle, state.isCharge)
        return EnergyLog(
            id = id,
            vehicleId = vehicle.id,
            stream = stream,
            dateTime = state.dateTime,
            odometer = parseDecimal(state.odometer)!!.toDouble(),
            quantity = price.quantity!!.toDouble(),
            pricePerUnitMinor = price.unitPrice?.let { PriceMath.unitPriceToStored(it, digits) },
            totalMinor = price.total?.let { PriceMath.toMinor(it, digits) },
            isFull = state.isFull,
            missedPrevious = state.missedPrevious,
            fuelGrade = state.fuelGrade.trim().ifEmpty { null }.takeIf { !state.isCharge },
            chargerType = state.charger.takeIf { state.isCharge },
            socStart = state.socStart.toIntOrNull().takeIf { state.isCharge },
            socEnd = state.socEnd.toIntOrNull().takeIf { state.isCharge },
            station = state.station.trim().ifEmpty { null },
            note = state.note.trim().ifEmpty { null },
            createdAt = now,
            updatedAt = now,
        )
    }

    /** True for the units a fill-up quantity is entered in. */
    fun quantityUnitIsEnergy(vehicle: Vehicle, isCharge: Boolean): Boolean = stream(vehicle, isCharge) == EnergyStream.ELECTRIC

    fun isWeightUnit(vehicle: Vehicle) = vehicle.volumeUnit == VolumeUnit.KG

    private val MAX_ODOMETER = BigDecimal("9999999")
    private val MAX_QUANTITY = BigDecimal("9999")
    private val MAX_MONEY = BigDecimal("9999999999")
}
