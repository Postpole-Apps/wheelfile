package com.postpole.wheelfile.domain.fillup

import kotlin.math.pow
import kotlin.math.roundToLong

/** The three price fields of a fill-up or charge. Any two determine the third. */
enum class PriceField { TOTAL, PRICE, VOLUME }

/** Parsed values of the three fields; null when empty or not a number. */
data class PriceInputs(
    val total: Double? = null,
    val price: Double? = null,
    val volume: Double? = null,
) {
    operator fun get(field: PriceField): Double? = when (field) {
        PriceField.TOTAL -> total
        PriceField.PRICE -> price
        PriceField.VOLUME -> volume
    }
}

/** [derived] is the field that was calculated, or null when fewer than two inputs are known. */
data class PriceResult(
    val total: Double?,
    val price: Double?,
    val volume: Double?,
    val derived: PriceField?,
)

/**
 * Any-two-of-three price maths (spec §2.2).
 *
 * The two most recently edited non-empty fields are the inputs; the third is derived. Editing the derived
 * field turns it into an input and the least recently edited input becomes the derived one.
 * Rounding: money to the currency's fraction digits, price per unit to 3 decimals, volume to 3 decimals.
 */
object PriceMath {
    const val PRICE_DECIMALS = 3
    const val VOLUME_DECIMALS = 3

    /**
     * @param editOrder fields in the order the user edited them, most recent last (duplicates allowed).
     * @param currencyDigits fraction digits of the vehicle's currency (2 for EUR/USD, 0 for JPY).
     */
    fun resolve(inputs: PriceInputs, editOrder: List<PriceField>, currencyDigits: Int = 2): PriceResult {
        val known = editOrder.asReversed().distinct().filter { (inputs[it] ?: 0.0) > 0.0 }
        if (known.size < 2) return PriceResult(inputs.total, inputs.price, inputs.volume, null)
        val used = known.take(2).toSet()
        val derived = PriceField.entries.first { it !in used }
        val total = inputs.total
        val price = inputs.price
        val volume = inputs.volume
        return when (derived) {
            PriceField.TOTAL -> PriceResult(round(price!! * volume!!, currencyDigits), price, volume, derived)
            PriceField.PRICE -> PriceResult(total, round(total!! / volume!!, PRICE_DECIMALS), volume, derived)
            PriceField.VOLUME -> PriceResult(total, price, round(total!! / price!!, VOLUME_DECIMALS), derived)
        }
    }

    /** Amount in minor currency units (for example cents), rounded half up. */
    fun toMinor(amount: Double, currencyDigits: Int): Long = (amount * 10.0.pow(currencyDigits)).roundToLong()

    fun fromMinor(minor: Long, currencyDigits: Int): Double = minor / 10.0.pow(currencyDigits)

    fun round(value: Double, digits: Int): Double {
        val factor = 10.0.pow(digits)
        return (value * factor).roundToLong() / factor
    }
}
