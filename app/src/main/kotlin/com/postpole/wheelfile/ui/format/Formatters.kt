package com.postpole.wheelfile.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.domain.economy.EconomyDisplay
import com.postpole.wheelfile.domain.fillup.PriceMath
import com.postpole.wheelfile.domain.units.EconomyUnits
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

/** Display formatting. Rounding happens here, once, never in the engine (spec §4.3). */
object Formatters {

    fun number(value: Double, maxDigits: Int, locale: Locale, minDigits: Int = 0): String =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = maxDigits
            minimumFractionDigits = minDigits
        }.format(value)

    fun money(minor: Long, currencyCode: String, locale: Locale): String {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        val format = NumberFormat.getCurrencyInstance(locale)
        if (currency != null) format.currency = currency
        return format.format(PriceMath.fromMinor(minor, currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 2))
    }

    /** Minor-unit amount with a fractional part, for example a cost per kilometre. */
    fun moneyPrecise(minor: Double, currencyCode: String, locale: Locale): String {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        val digits = currency?.defaultFractionDigits?.coerceAtLeast(0) ?: 2
        val format = NumberFormat.getCurrencyInstance(locale)
        if (currency != null) format.currency = currency
        format.minimumFractionDigits = digits + 1
        format.maximumFractionDigits = digits + 2
        return format.format(minor / Math.pow(10.0, digits.toDouble()))
    }

    fun date(epochMillis: Long, locale: Locale): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(epochMillis))

    /** "7.4 L/100 km"; null when the value cannot be shown in [unit]. */
    fun economy(per100Km: Double, unit: EconomyUnit, locale: Locale): String? =
        EconomyDisplay.value(per100Km, unit)?.let { "${number(it, 1, locale, minDigits = 1)} ${EconomyUnits.label(unit)}" }
}

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
