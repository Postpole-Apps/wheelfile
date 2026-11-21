package com.postpole.wheelfile.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.ChargerType
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.entry.PriceMath
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@StringRes
fun DistanceUnit.label(): Int = when (this) {
    DistanceUnit.KM -> R.string.unit_km
    DistanceUnit.MI -> R.string.unit_mi
}

@StringRes
fun VolumeUnit.label(): Int = when (this) {
    VolumeUnit.L -> R.string.unit_l
    VolumeUnit.US_GAL -> R.string.unit_us_gal
    VolumeUnit.UK_GAL -> R.string.unit_uk_gal
    VolumeUnit.KG -> R.string.unit_kg
}

@StringRes
fun EconomyUnit.label(): Int = when (this) {
    EconomyUnit.L_PER_100KM -> R.string.economy_l_per_100km
    EconomyUnit.KM_PER_L -> R.string.economy_km_per_l
    EconomyUnit.MPG_US -> R.string.economy_mpg_us
    EconomyUnit.MPG_UK -> R.string.economy_mpg_uk
    EconomyUnit.KWH_PER_100KM -> R.string.economy_kwh_per_100km
    EconomyUnit.MI_PER_KWH -> R.string.economy_mi_per_kwh
    EconomyUnit.KWH_PER_100MI -> R.string.economy_kwh_per_100mi
    EconomyUnit.KG_PER_100KM -> R.string.economy_kg_per_100km
}

@StringRes
fun VehicleType.label(): Int = when (this) {
    VehicleType.CAR -> R.string.type_car
    VehicleType.MOTORBIKE -> R.string.type_motorbike
    VehicleType.VAN -> R.string.type_van
    VehicleType.OTHER -> R.string.type_other
}

@StringRes
fun Powertrain.label(): Int = when (this) {
    Powertrain.PETROL -> R.string.powertrain_petrol
    Powertrain.DIESEL -> R.string.powertrain_diesel
    Powertrain.LPG -> R.string.powertrain_lpg
    Powertrain.CNG -> R.string.powertrain_cng
    Powertrain.ELECTRIC -> R.string.powertrain_electric
    Powertrain.PHEV -> R.string.powertrain_phev
    Powertrain.HYBRID -> R.string.powertrain_hybrid
    Powertrain.HYDROGEN -> R.string.powertrain_hydrogen
}

@StringRes
fun ChargerType.label(): Int = when (this) {
    ChargerType.HOME -> R.string.charger_home
    ChargerType.AC_PUBLIC -> R.string.charger_ac
    ChargerType.DC_FAST -> R.string.charger_dc
}

/** Money from minor units, formatted for the device locale in the vehicle's currency. */
fun formatMoney(minor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
    val currency = try {
        Currency.getInstance(currencyCode)
    } catch (_: IllegalArgumentException) {
        Currency.getInstance("USD")
    }
    val fmt = NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
    return fmt.format(PriceMath.fromMinor(minor, currency.defaultFractionDigits.coerceAtLeast(0)))
}

object PriceFormat {
    /** A price per unit in major units with three decimals (1.859), in [currencyCode] for the device locale. */
    fun unitPrice(value: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val currency = try {
            Currency.getInstance(currencyCode)
        } catch (_: IllegalArgumentException) {
            Currency.getInstance("USD")
        }
        return NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = 3
            maximumFractionDigits = 3
        }.format(value)
    }
}

/** Plain number with at most [digits] decimals (never forced), grouped for the device locale. */
fun formatNumber(value: Double, digits: Int = 1, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = digits
        minimumFractionDigits = 0
    }.format(value)

/** A decimal value for a text field: dot or locale separator is accepted on input, so write the plain form. */
fun plainNumber(value: java.math.BigDecimal): String = value.stripTrailingZeros().toPlainString()

@Composable
fun distanceText(value: Double, unit: DistanceUnit): String = "${formatNumber(value, 0)} ${stringResource(unit.label())}"
