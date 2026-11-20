package com.postpole.wheelfile.domain.vehicle

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.VolumeUnit
import java.util.Locale

/** Free tier: 2 active vehicles. Archived vehicles are read-only and do not count (spec §1.2, §5.1). */
object VehicleCap {
    const val FREE_ACTIVE_VEHICLES = 2

    fun canAddActive(activeCount: Int, isPro: Boolean): Boolean = isPro || activeCount < FREE_ACTIVE_VEHICLES

    /** Restoring an archived vehicle makes it active again, so it follows the same rule. */
    fun canUnarchive(activeCount: Int, isPro: Boolean): Boolean = canAddActive(activeCount, isPro)
}

/** Units prefilled on the Units step from the device locale; the user can change every one. */
data class UnitDefaults(
    val distance: DistanceUnit,
    val volume: VolumeUnit,
    val fuelEconomy: EconomyUnit,
    val electricEconomy: EconomyUnit,
)

object LocaleDefaults {
    fun forLocale(locale: Locale): UnitDefaults = when (locale.country) {
        "US" -> UnitDefaults(DistanceUnit.MI, VolumeUnit.US_GAL, EconomyUnit.MPG_US, EconomyUnit.MI_PER_KWH)
        "GB" -> UnitDefaults(DistanceUnit.MI, VolumeUnit.L, EconomyUnit.MPG_UK, EconomyUnit.MI_PER_KWH)
        "LR", "MM" -> UnitDefaults(DistanceUnit.MI, VolumeUnit.L, EconomyUnit.L_PER_100KM, EconomyUnit.KWH_PER_100MI)
        else -> UnitDefaults(DistanceUnit.KM, VolumeUnit.L, EconomyUnit.L_PER_100KM, EconomyUnit.KWH_PER_100KM)
    }
}

fun Powertrain.hasLiquidStream(): Boolean = this != Powertrain.ELECTRIC

fun Powertrain.hasElectricStream(): Boolean = this == Powertrain.ELECTRIC || this == Powertrain.PHEV

/** Volume unit for the non-electric stream of this powertrain (hydrogen and CNG are sold by mass). */
fun Powertrain.volumeUnitFor(preferred: VolumeUnit): VolumeUnit = when (this) {
    Powertrain.HYDROGEN, Powertrain.CNG -> VolumeUnit.KG
    else -> if (preferred == VolumeUnit.KG) VolumeUnit.L else preferred
}
