package com.postpole.wheelfile.domain.entry

import java.math.BigDecimal
import java.math.RoundingMode

/** The three linked fields of a fill-up or charge. Any two of them determine the third. */
enum class PriceField { QUANTITY, UNIT_PRICE, TOTAL }

/** Outcome of [PriceMath.resolve]: all three values (when known) and which one was derived. */
data class PriceResult(
    val quantity: BigDecimal?,
    val unitPrice: BigDecimal?,
    val total: BigDecimal?,
    /** The field that was computed from the other two, or null when fewer than two were entered. */
    val derived: PriceField?,
)

/**
 * Any-two-of-three price maths (spec §2.2). Money is in major units here (for example 1.859 per litre,
 * 52.30 total); callers convert to minor units with [toMinor].
 *
 * Rounding is HALF_UP and happens once, on the derived value only:
 * total to the currency's fraction digits, unit price to [UNIT_PRICE_SCALE] decimals, quantity to [QUANTITY_SCALE].
 */
object PriceMath {
    const val UNIT_PRICE_SCALE = 3
    const val QUANTITY_SCALE = 2

    /** Stored `pricePerUnitMinor` is in thousandths of a minor unit so 1.859 per litre is not rounded to 1.86. */
    const val UNIT_PRICE_STORAGE_FACTOR = 1000L

    /**
     * @param entered values the user typed (null or non-positive means empty).
     * @param editOrder fields in the order the user last edited them, oldest first. When all three are
     * filled, the least recently edited one is the derived one.
     */
    fun resolve(
        entered: Map<PriceField, BigDecimal?>,
        editOrder: List<PriceField>,
        fractionDigits: Int,
    ): PriceResult {
        fun value(f: PriceField) = entered[f]?.takeIf { it.signum() > 0 }
        val filled = PriceField.entries.filter { value(it) != null }
        val q = value(PriceField.QUANTITY)
        val p = value(PriceField.UNIT_PRICE)
        val t = value(PriceField.TOTAL)
        if (filled.size < 2) return PriceResult(q, p, t, null)
        val derived = if (filled.size == 2) {
            PriceField.entries.first { it !in filled }
        } else {
            // All three filled: the oldest edit loses. Fields never edited count as oldest.
            PriceField.entries.minByOrNull { editOrder.lastIndexOf(it) }!!
        }
        return when (derived) {
            PriceField.TOTAL ->
                PriceResult(q, p, q!!.multiply(p!!).setScale(fractionDigits, RoundingMode.HALF_UP), derived)
            PriceField.UNIT_PRICE ->
                PriceResult(q, t!!.divide(q!!, UNIT_PRICE_SCALE, RoundingMode.HALF_UP), t, derived)
            PriceField.QUANTITY ->
                PriceResult(t!!.divide(p!!, QUANTITY_SCALE, RoundingMode.HALF_UP), p, t, derived)
        }
    }

    /** Major units to minor units (cents) with [fractionDigits] decimals. */
    fun toMinor(major: BigDecimal, fractionDigits: Int): Long =
        major.movePointRight(fractionDigits).setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun fromMinor(minor: Long, fractionDigits: Int): BigDecimal = BigDecimal.valueOf(minor, fractionDigits)

    fun unitPriceToStored(unitPrice: BigDecimal, fractionDigits: Int): Long =
        unitPrice.movePointRight(fractionDigits).multiply(BigDecimal(UNIT_PRICE_STORAGE_FACTOR))
            .setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun unitPriceFromStored(stored: Long, fractionDigits: Int): BigDecimal =
        BigDecimal.valueOf(stored, fractionDigits + 3)
}
