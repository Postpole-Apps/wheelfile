package com.postpole.wheelfile.domain.units

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.VolumeUnit

/** Constants from docs/spec.md §4.2. Pinned by `UnitConversionTest`. */
object UnitConstants {
    const val KM_PER_MILE = 1.609344
    const val LITRES_PER_US_GALLON = 3.785411784
    const val LITRES_PER_UK_GALLON = 4.54609

    /** MPG (US) = 235.215 / (L/100km). */
    const val MPG_US_NUMERATOR = 235.215

    /** MPG (UK) = 282.481 / (L/100km). */
    const val MPG_UK_NUMERATOR = 282.481

    /** mi/kWh = 62.1371 / (kWh/100km). */
    const val MI_PER_KWH_NUMERATOR = 62.1371
}

/**
 * Pure unit maths. Everything is normalised internally to km and L (or kWh, kg) as `Double`;
 * nothing here rounds. Rounding happens only when a value is displayed.
 */
object UnitConversion {

    fun toKm(distance: Double, unit: DistanceUnit): Double = when (unit) {
        DistanceUnit.KM -> distance
        DistanceUnit.MI -> distance * UnitConstants.KM_PER_MILE
    }

    fun fromKm(km: Double, unit: DistanceUnit): Double = when (unit) {
        DistanceUnit.KM -> km
        DistanceUnit.MI -> km / UnitConstants.KM_PER_MILE
    }

    /**
     * Normalises a quantity to litres. [VolumeUnit.KG] (hydrogen, CNG) and kWh quantities are not
     * volumes, so they pass through unchanged (kg / kWh).
     */
    fun toBaseQuantity(quantity: Double, unit: VolumeUnit): Double = when (unit) {
        VolumeUnit.L, VolumeUnit.KG -> quantity
        VolumeUnit.US_GAL -> quantity * UnitConstants.LITRES_PER_US_GALLON
        VolumeUnit.UK_GAL -> quantity * UnitConstants.LITRES_PER_UK_GALLON
    }

    fun fromLitres(litres: Double, unit: VolumeUnit): Double = when (unit) {
        VolumeUnit.L, VolumeUnit.KG -> litres
        VolumeUnit.US_GAL -> litres / UnitConstants.LITRES_PER_US_GALLON
        VolumeUnit.UK_GAL -> litres / UnitConstants.LITRES_PER_UK_GALLON
    }

    /**
     * Converts the engine's base economy (L/100km, kWh/100km or kg/100km) to [unit] for display.
     * Returns null when the conversion is undefined (a zero base value for the inverse units).
     */
    fun economyForDisplay(per100Km: Double, unit: EconomyUnit): Double? = when (unit) {
        EconomyUnit.L_PER_100KM, EconomyUnit.KWH_PER_100KM, EconomyUnit.KG_PER_100KM -> per100Km
        EconomyUnit.KM_PER_L -> inverse(100.0, per100Km)
        EconomyUnit.MPG_US -> inverse(UnitConstants.MPG_US_NUMERATOR, per100Km)
        EconomyUnit.MPG_UK -> inverse(UnitConstants.MPG_UK_NUMERATOR, per100Km)
        EconomyUnit.MI_PER_KWH -> inverse(UnitConstants.MI_PER_KWH_NUMERATOR, per100Km)
        EconomyUnit.KWH_PER_100MI -> per100Km * UnitConstants.KM_PER_MILE
    }

    private fun inverse(numerator: Double, value: Double): Double? =
        if (value > 0.0) numerator / value else null
}
