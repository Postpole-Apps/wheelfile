package com.postpole.wheelfile.domain.units

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.VolumeUnit
import java.util.Currency
import java.util.Locale

/** Unit choices for one vehicle. */
data class UnitChoice(
    val distance: DistanceUnit,
    val volume: VolumeUnit,
    val economy: EconomyUnit,
    val currency: String,
)

/** Device-locale defaults for the Units step (spec §2.1 step 3). The user can change every value. */
object LocaleDefaults {
    private val MILE_COUNTRIES = setOf("US", "GB", "LR", "MM")
    private val US_GALLON_COUNTRIES = setOf("US", "LR", "MM")

    fun forLocale(locale: Locale, powertrain: Powertrain = Powertrain.PETROL): UnitChoice {
        val country = locale.country
        val distance = if (country in MILE_COUNTRIES) DistanceUnit.MI else DistanceUnit.KM
        val volume = if (country in US_GALLON_COUNTRIES) VolumeUnit.US_GAL else VolumeUnit.L
        val currency = try {
            Currency.getInstance(locale).currencyCode
        } catch (_: IllegalArgumentException) {
            "USD"
        } catch (_: NullPointerException) {
            "USD"
        }
        return UnitChoice(distance, volume, economyFor(distance, volume, country, powertrain), currency)
    }

    fun economyFor(distance: DistanceUnit, volume: VolumeUnit, country: String, powertrain: Powertrain): EconomyUnit =
        when (powertrain) {
            Powertrain.ELECTRIC -> if (distance == DistanceUnit.MI) EconomyUnit.MI_PER_KWH else EconomyUnit.KWH_PER_100KM
            Powertrain.HYDROGEN, Powertrain.CNG -> EconomyUnit.KG_PER_100KM
            else -> when {
                volume == VolumeUnit.US_GAL -> EconomyUnit.MPG_US
                volume == VolumeUnit.UK_GAL || (distance == DistanceUnit.MI && country == "GB") -> EconomyUnit.MPG_UK
                else -> EconomyUnit.L_PER_100KM
            }
        }

    val fuelEconomyOptions: List<EconomyUnit> =
        listOf(EconomyUnit.L_PER_100KM, EconomyUnit.KM_PER_L, EconomyUnit.MPG_US, EconomyUnit.MPG_UK)

    val electricEconomyOptions: List<EconomyUnit> =
        listOf(EconomyUnit.KWH_PER_100KM, EconomyUnit.MI_PER_KWH, EconomyUnit.KWH_PER_100MI)

    fun currencyCodes(): List<String> = Currency.getAvailableCurrencies().map { it.currencyCode }.sorted()
}
