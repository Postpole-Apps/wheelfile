package com.postpole.wheelfile.domain.entry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PriceMathTest {
    private fun d(s: String) = BigDecimal(s)

    private fun resolve(
        q: String? = null,
        p: String? = null,
        t: String? = null,
        order: List<PriceField> = emptyList(),
        digits: Int = 2,
    ) = PriceMath.resolve(
        mapOf(PriceField.QUANTITY to q?.let(::d), PriceField.UNIT_PRICE to p?.let(::d), PriceField.TOTAL to t?.let(::d)),
        order,
        digits,
    )

    @Test fun totalFromVolumeAndPrice() {
        val r = resolve(q = "40", p = "1.859")
        assertEquals(PriceField.TOTAL, r.derived)
        assertEquals(d("74.36"), r.total)
    }

    @Test fun priceFromTotalAndVolume() {
        val r = resolve(q = "40", t = "74.36")
        assertEquals(PriceField.UNIT_PRICE, r.derived)
        assertEquals(d("1.859"), r.unitPrice)
    }

    @Test fun volumeFromTotalAndPrice() {
        val r = resolve(p = "1.859", t = "74.36")
        assertEquals(PriceField.QUANTITY, r.derived)
        assertEquals(d("40.00"), r.quantity)
    }

    @Test fun totalRoundsHalfUpToCurrencyDigits() {
        // 10.5 x 1.005 = 10.5525 -> 10.55; 0.5 x 1.01 = 0.505 -> 0.51
        assertEquals(d("10.55"), resolve(q = "10.5", p = "1.005").total)
        assertEquals(d("0.51"), resolve(q = "0.5", p = "1.01").total)
    }

    @Test fun totalUsesZeroDigitsForYen() {
        assertEquals(d("6500"), resolve(q = "40", p = "162.5", digits = 0).total)
        // 7.5 x 162.5 = 1218.75 -> 1219
        assertEquals(d("1219"), resolve(q = "7.5", p = "162.5", digits = 0).total)
    }

    @Test fun unitPriceRoundsToThreeDecimals() {
        // 50 / 27.3 = 1.8315018...
        assertEquals(d("1.832"), resolve(q = "27.3", t = "50").unitPrice)
    }

    @Test fun quantityRoundsToTwoDecimals() {
        // 50 / 1.899 = 26.3296...
        assertEquals(d("26.33"), resolve(p = "1.899", t = "50").quantity)
    }

    @Test fun oneOrZeroFieldsDeriveNothing() {
        assertNull(resolve(q = "40").derived)
        assertNull(resolve().derived)
        assertNull(resolve(q = "40").total)
    }

    @Test fun zeroCountsAsEmpty() {
        assertNull(resolve(q = "0", p = "1.5").derived)
    }

    @Test fun allThree_oldestEditedIsDerived() {
        val order = listOf(PriceField.TOTAL, PriceField.QUANTITY, PriceField.UNIT_PRICE)
        // Total was typed first, so it is recalculated from volume x price.
        val r = resolve(q = "10", p = "2", t = "99", order = order)
        assertEquals(PriceField.TOTAL, r.derived)
        assertEquals(d("20.00"), r.total)

        val order2 = listOf(PriceField.UNIT_PRICE, PriceField.TOTAL, PriceField.QUANTITY)
        val r2 = resolve(q = "10", p = "2", t = "30", order = order2)
        assertEquals(PriceField.UNIT_PRICE, r2.derived)
        assertEquals(d("3.000"), r2.unitPrice)
    }

    @Test fun minorUnitConversionRoundsHalfUp() {
        assertEquals(7436L, PriceMath.toMinor(d("74.36"), 2))
        assertEquals(1L, PriceMath.toMinor(d("0.005"), 2))
        assertEquals(6500L, PriceMath.toMinor(d("6500"), 0))
        assertEquals(d("74.36"), PriceMath.fromMinor(7436, 2))
    }

    @Test fun unitPriceIsStoredInThousandthsOfAMinorUnit() {
        val stored = PriceMath.unitPriceToStored(d("1.859"), 2)
        assertEquals(185_900L, stored)
        assertEquals(0, d("1.859").compareTo(PriceMath.unitPriceFromStored(stored, 2)))
    }
}
