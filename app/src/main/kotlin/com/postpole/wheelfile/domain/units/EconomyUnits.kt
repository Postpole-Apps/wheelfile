package com.postpole.wheelfile.domain.units

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VolumeUnit

object EconomyUnits {
    val LIQUID = listOf(EconomyUnit.L_PER_100KM, EconomyUnit.KM_PER_L, EconomyUnit.MPG_US, EconomyUnit.MPG_UK)
    val ELECTRIC = listOf(EconomyUnit.KWH_PER_100KM, EconomyUnit.MI_PER_KWH, EconomyUnit.KWH_PER_100MI)

    /** Economy display units offered for [powertrain]; mass-sold fuels have a single unit. */
    fun optionsFor(powertrain: Powertrain): List<EconomyUnit> = when (powertrain) {
        Powertrain.ELECTRIC -> ELECTRIC
        Powertrain.HYDROGEN, Powertrain.CNG -> listOf(EconomyUnit.KG_PER_100KM)
        else -> LIQUID
    }

    /**
     * The unit shown for [stream] of [vehicle]. The vehicle stores one `economyDisplay`; for a PHEV the
     * electric stream follows the distance unit (kWh/100 km or mi/kWh).
     */
    fun forStream(vehicle: Vehicle, stream: EnergyStream): EconomyUnit = when (stream) {
        EnergyStream.ELECTRIC ->
            if (vehicle.economyDisplay in ELECTRIC) {
                vehicle.economyDisplay
            } else if (vehicle.distanceUnit == DistanceUnit.MI) {
                EconomyUnit.MI_PER_KWH
            } else {
                EconomyUnit.KWH_PER_100KM
            }
        EnergyStream.GAS_KG -> EconomyUnit.KG_PER_100KM
        EnergyStream.LIQUID -> if (vehicle.economyDisplay in LIQUID) vehicle.economyDisplay else EconomyUnit.L_PER_100KM
    }

    /** Default for [powertrain] given the locale's fuel and electric preferences. */
    fun defaultFor(powertrain: Powertrain, fuel: EconomyUnit, electric: EconomyUnit): EconomyUnit = when (powertrain) {
        Powertrain.ELECTRIC -> electric
        Powertrain.HYDROGEN, Powertrain.CNG -> EconomyUnit.KG_PER_100KM
        else -> fuel
    }

    /** Short label such as "L/100 km" or "MPG (US)". */
    fun label(unit: EconomyUnit): String = when (unit) {
        EconomyUnit.L_PER_100KM -> "L/100 km"
        EconomyUnit.KM_PER_L -> "km/L"
        EconomyUnit.MPG_US -> "MPG (US)"
        EconomyUnit.MPG_UK -> "MPG (UK)"
        EconomyUnit.KWH_PER_100KM -> "kWh/100 km"
        EconomyUnit.MI_PER_KWH -> "mi/kWh"
        EconomyUnit.KWH_PER_100MI -> "kWh/100 mi"
        EconomyUnit.KG_PER_100KM -> "kg/100 km"
    }

    fun distanceLabel(unit: DistanceUnit): String = if (unit == DistanceUnit.KM) "km" else "mi"

    /** Quantity unit of [stream] for [vehicle]: litres/gallons/kg for fuel, kWh for charging. */
    fun quantityLabel(vehicle: Vehicle, stream: EnergyStream): String = when (stream) {
        EnergyStream.ELECTRIC -> "kWh"
        EnergyStream.GAS_KG -> "kg"
        EnergyStream.LIQUID -> when (vehicle.volumeUnit) {
            VolumeUnit.L -> "L"
            VolumeUnit.US_GAL -> "US gal"
            VolumeUnit.UK_GAL -> "UK gal"
            VolumeUnit.KG -> "kg"
        }
    }

    /** The non-electric stream of [powertrain] (what a "Fill-up" logs). */
    fun fuelStream(powertrain: Powertrain): EnergyStream =
        defaultStream(powertrain).takeIf { it != EnergyStream.ELECTRIC } ?: EnergyStream.LIQUID

    fun defaultStream(powertrain: Powertrain): EnergyStream = when (powertrain) {
        Powertrain.ELECTRIC -> EnergyStream.ELECTRIC
        Powertrain.HYDROGEN, Powertrain.CNG -> EnergyStream.GAS_KG
        else -> EnergyStream.LIQUID
    }
}
