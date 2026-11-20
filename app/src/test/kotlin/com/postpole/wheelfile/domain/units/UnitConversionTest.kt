package com.postpole.wheelfile.domain.units

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.VolumeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConversionTest {
    private val eps = 1e-9

    @Test
    fun constants_arePinned() {
        assertEquals(1.609344, UnitConstants.KM_PER_MILE, 0.0)
        assertEquals(3.785411784, UnitConstants.LITRES_PER_US_GALLON, 0.0)
        assertEquals(4.54609, UnitConstants.LITRES_PER_UK_GALLON, 0.0)
        assertEquals(235.215, UnitConstants.MPG_US_NUMERATOR, 0.0)
        assertEquals(282.481, UnitConstants.MPG_UK_NUMERATOR, 0.0)
        assertEquals(62.1371, UnitConstants.MI_PER_KWH_NUMERATOR, 0.0)
    }

    @Test
    fun miles_toKm_andBack() {
        assertEquals(1.609344, UnitConversion.toKm(1.0, DistanceUnit.MI), eps)
        assertEquals(160.9344, UnitConversion.toKm(100.0, DistanceUnit.MI), eps)
        assertEquals(100.0, UnitConversion.toKm(100.0, DistanceUnit.KM), eps)
        assertEquals(100.0, UnitConversion.fromKm(160.9344, DistanceUnit.MI), eps)
    }

    @Test
    fun gallons_toLitres_usAndUk() {
        assertEquals(3.785411784, UnitConversion.toBaseQuantity(1.0, VolumeUnit.US_GAL), eps)
        assertEquals(4.54609, UnitConversion.toBaseQuantity(1.0, VolumeUnit.UK_GAL), eps)
        assertEquals(12.5, UnitConversion.toBaseQuantity(12.5, VolumeUnit.L), eps)
        assertEquals(7.5, UnitConversion.toBaseQuantity(7.5, VolumeUnit.KG), eps)
        assertEquals(2.0, UnitConversion.fromLitres(2 * 3.785411784, VolumeUnit.US_GAL), eps)
        assertEquals(2.0, UnitConversion.fromLitres(2 * 4.54609, VolumeUnit.UK_GAL), eps)
    }

    @Test
    fun economy_conversions() {
        val lPer100 = 8.0
        assertEquals(8.0, UnitConversion.economyForDisplay(lPer100, EconomyUnit.L_PER_100KM)!!, eps)
        assertEquals(12.5, UnitConversion.economyForDisplay(lPer100, EconomyUnit.KM_PER_L)!!, eps)
        assertEquals(235.215 / 8.0, UnitConversion.economyForDisplay(lPer100, EconomyUnit.MPG_US)!!, eps)
        assertEquals(282.481 / 8.0, UnitConversion.economyForDisplay(lPer100, EconomyUnit.MPG_UK)!!, eps)
        // 8 L/100km is about 29.4 US mpg and about 35.3 UK mpg.
        assertEquals(29.4, UnitConversion.economyForDisplay(lPer100, EconomyUnit.MPG_US)!!, 0.05)
        assertEquals(35.3, UnitConversion.economyForDisplay(lPer100, EconomyUnit.MPG_UK)!!, 0.05)
    }

    @Test
    fun electric_economy_conversions() {
        val kwhPer100Km = 16.0
        assertEquals(16.0, UnitConversion.economyForDisplay(kwhPer100Km, EconomyUnit.KWH_PER_100KM)!!, eps)
        assertEquals(62.1371 / 16.0, UnitConversion.economyForDisplay(kwhPer100Km, EconomyUnit.MI_PER_KWH)!!, eps)
        assertEquals(16.0 * 1.609344, UnitConversion.economyForDisplay(kwhPer100Km, EconomyUnit.KWH_PER_100MI)!!, eps)
        assertEquals(5.0, UnitConversion.economyForDisplay(5.0, EconomyUnit.KG_PER_100KM)!!, eps)
    }

    @Test
    fun zeroEconomy_hasNoInverseUnits() {
        assertNull(UnitConversion.economyForDisplay(0.0, EconomyUnit.KM_PER_L))
        assertNull(UnitConversion.economyForDisplay(0.0, EconomyUnit.MPG_US))
        assertNull(UnitConversion.economyForDisplay(0.0, EconomyUnit.MPG_UK))
        assertNull(UnitConversion.economyForDisplay(0.0, EconomyUnit.MI_PER_KWH))
    }
}
