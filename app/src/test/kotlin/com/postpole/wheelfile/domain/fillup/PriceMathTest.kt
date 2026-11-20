package com.postpole.wheelfile.domain.fillup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceMathTest {
    private val delta = 1e-9

    @Test
    fun totalDerivedFromPriceAndVolume() {
        val r = PriceMath.resolve(
            PriceInputs(price = 1.859, volume = 40.0),
            listOf(PriceField.PRICE, PriceField.VOLUME),
        )
        assertEquals(PriceField.TOTAL, r.derived)
        assertEquals(74.36, r.total!!, delta)
    }

    @Test
    fun priceDerivedFromTotalAndVolume() {
        val r = PriceMath.resolve(
            PriceInputs(total = 74.36, volume = 40.0),
            listOf(PriceField.TOTAL, PriceField.VOLUME),
        )
        assertEquals(PriceField.PRICE, r.derived)
        assertEquals(1.859, r.price!!, delta)
    }

    @Test
    fun volumeDerivedFromTotalAndPrice() {
        val r = PriceMath.resolve(
            PriceInputs(total = 50.0, price = 1.8),
            listOf(PriceField.TOTAL, PriceField.PRICE),
        )
        assertEquals(PriceField.VOLUME, r.derived)
        assertEquals(27.778, r.volume!!, delta)
    }

    @Test
    fun totalRoundsToCurrencyDigits() {
        // 1.999 * 33.33 = 66.62667 -> 66.63 for two digits, 67 for none (JPY-like)
        val inputs = PriceInputs(price = 1.999, volume = 33.33)
        val order = listOf(PriceField.PRICE, PriceField.VOLUME)
        assertEquals(66.63, PriceMath.resolve(inputs, order, 2).total!!, delta)
        assertEquals(67.0, PriceMath.resolve(inputs, order, 0).total!!, delta)
    }

    @Test
    fun twoMostRecentEditsAreTheInputs() {
        // Price edited first, then volume, then total: price is the oldest, so it is recalculated.
        val r = PriceMath.resolve(
            PriceInputs(total = 60.0, price = 1.0, volume = 40.0),
            listOf(PriceField.PRICE, PriceField.VOLUME, PriceField.TOTAL),
        )
        assertEquals(PriceField.PRICE, r.derived)
        assertEquals(1.5, r.price!!, delta)
    }

    @Test
    fun fewerThanTwoInputsDeriveNothing() {
        val r = PriceMath.resolve(PriceInputs(volume = 40.0), listOf(PriceField.VOLUME))
        assertNull(r.derived)
        assertNull(r.total)
        assertNull(r.price)
    }

    @Test
    fun emptyOrZeroFieldsAreNotInputs() {
        val r = PriceMath.resolve(
            PriceInputs(total = 0.0, price = 2.0, volume = 10.0),
            listOf(PriceField.PRICE, PriceField.VOLUME, PriceField.TOTAL),
        )
        // total is 0, so price and volume stay the inputs and the total is calculated
        assertEquals(PriceField.TOTAL, r.derived)
        assertEquals(20.0, r.total!!, delta)
    }

    @Test
    fun minorUnitConversionRoundsHalfUp() {
        assertEquals(7436L, PriceMath.toMinor(74.36, 2))
        assertEquals(1235L, PriceMath.toMinor(12.345, 2))
        assertEquals(500L, PriceMath.toMinor(500.0, 0))
        assertEquals(74.36, PriceMath.fromMinor(7436, 2), delta)
    }
}
