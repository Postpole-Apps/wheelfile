package com.postpole.wheelfile.domain.economy

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyEngineTest {
    private val eps = 1e-9

    private fun e(id: Long, odo: Double, qty: Double, full: Boolean, missed: Boolean = false, day: Long = id) =
        EnergyEntry(id = id, dateTime = day * 86_400_000L, odometerKm = odo, quantity = qty, isFull = full, missedPrevious = missed)

    @Test
    fun fullToFull_simpleSegment() {
        val r = EconomyEngine.compute(listOf(e(1, 1000.0, 40.0, true), e(2, 1500.0, 35.0, true)))
        assertEquals(1, r.segments.size)
        assertEquals(500.0, r.segments[0].distanceKm, eps)
        assertEquals(35.0, r.segments[0].quantity, eps)
        assertEquals(7.0, r.segments[0].per100Km!!, eps)
        assertEquals(7.0, r.average100Km!!, eps)
    }

    @Test
    fun partialThenFull_partialsAreSummedIntoNextFull() {
        val r = EconomyEngine.compute(
            listOf(
                e(1, 1000.0, 40.0, true),
                e(2, 1200.0, 10.0, false),
                e(3, 1400.0, 12.0, false),
                e(4, 1600.0, 18.0, true),
            ),
        )
        assertEquals(1, r.segments.size)
        assertEquals(600.0, r.segments[0].distanceKm, eps)
        assertEquals(40.0, r.segments[0].quantity, eps)
        assertEquals(40.0 / 600.0 * 100.0, r.segments[0].per100Km!!, eps)
    }

    @Test
    fun entriesBeforeFirstFull_areBaselineOnly() {
        val r = EconomyEngine.compute(
            listOf(e(1, 900.0, 20.0, false), e(2, 1000.0, 30.0, true), e(3, 1400.0, 28.0, true)),
        )
        assertEquals(2L, r.baselineEntryId)
        assertEquals(1, r.segments.size)
        assertEquals(28.0, r.segments[0].quantity, eps)
    }

    @Test
    fun noFullEntry_givesNoEconomy() {
        val r = EconomyEngine.compute(listOf(e(1, 1000.0, 10.0, false), e(2, 1100.0, 10.0, false)))
        assertTrue(r.segments.isEmpty())
        assertNull(r.average100Km)
    }

    @Test
    fun missedFill_invalidatesSegment_andResetsBaseline() {
        val r = EconomyEngine.compute(
            listOf(
                e(1, 1000.0, 40.0, true),
                e(2, 1500.0, 20.0, true, missed = true), // segment 1->2 invalid
                e(3, 2000.0, 35.0, true), // baseline reset at 2: valid 500 km, 35 L
            ),
        )
        assertEquals(2, r.segments.size)
        assertFalse(r.segments[0].isValid)
        assertEquals(InvalidReason.MISSED_FILL, r.segments[0].invalidReason)
        assertNull(r.segments[0].per100Km)
        assertTrue(r.segments[1].isValid)
        assertEquals(7.0, r.average100Km!!, eps)
        assertEquals(500.0, r.totalDistanceKm, eps)
    }

    @Test
    fun missedFlagOnPartialInsideSegment_invalidatesIt() {
        val r = EconomyEngine.compute(
            listOf(e(1, 0.0, 40.0, true), e(2, 200.0, 10.0, false, missed = true), e(3, 500.0, 25.0, true)),
        )
        assertFalse(r.segments.single().isValid)
        assertNull(r.average100Km)
    }

    @Test
    fun periodAverage_isSumOverSum_notMeanOfSegmentAverages() {
        val r = EconomyEngine.compute(
            listOf(
                e(1, 0.0, 40.0, true),
                e(2, 100.0, 10.0, true), // 10 L/100km over 100 km
                e(3, 1100.0, 60.0, true), // 6 L/100km over 1000 km
            ),
        )
        val mean = (10.0 + 6.0) / 2.0
        assertEquals(70.0 / 1100.0 * 100.0, r.average100Km!!, eps)
        assertTrue(Math.abs(r.average100Km!! - mean) > 1.0)
    }

    @Test
    fun usGallonsAndMiles_areNormalisedBeforeComputing() {
        val vehicle = vehicle(DistanceUnit.MI, VolumeUnit.US_GAL, Powertrain.PETROL)
        val logs = listOf(
            log(1, 10_000.0, 12.0, EnergyStream.LIQUID, true),
            log(2, 10_300.0, 10.0, EnergyStream.LIQUID, true), // 300 mi on 10 US gal = 30 mpg
        )
        val seg = computeByStream(logs, vehicle).getValue(EnergyStream.LIQUID).segments.single()
        assertEquals(300 * 1.609344, seg.distanceKm, eps)
        assertEquals(10 * 3.785411784, seg.quantity, eps)
        val mpg = EconomyDisplay.value(seg.per100Km!!, EconomyUnit.MPG_US, 1)!!
        assertEquals(30.0, mpg, 0.1)
    }

    @Test
    fun ukGallonsAndMiles_areNormalisedBeforeComputing() {
        val vehicle = vehicle(DistanceUnit.MI, VolumeUnit.UK_GAL, Powertrain.DIESEL)
        val logs = listOf(
            log(1, 5_000.0, 10.0, EnergyStream.LIQUID, true),
            log(2, 5_400.0, 8.0, EnergyStream.LIQUID, true), // 400 mi on 8 UK gal = 50 mpg
        )
        val seg = computeByStream(logs, vehicle).getValue(EnergyStream.LIQUID).segments.single()
        assertEquals(8 * 4.54609, seg.quantity, eps)
        val mpg = EconomyDisplay.value(seg.per100Km!!, EconomyUnit.MPG_UK, 1)!!
        assertEquals(50.0, mpg, 0.1)
    }

    @Test
    fun outOfOrderInsert_givesSameResultAsChronological() {
        val ordered = listOf(
            e(1, 1000.0, 40.0, true), e(2, 1300.0, 20.0, false), e(3, 1600.0, 25.0, true), e(4, 2200.0, 40.0, true),
        )
        val shuffled = listOf(ordered[3], ordered[0], ordered[2], ordered[1])
        assertEquals(EconomyEngine.compute(ordered), EconomyEngine.compute(shuffled))
    }

    @Test
    fun outOfOrderInsert_splitsAnExistingSegment() {
        val before = EconomyEngine.compute(listOf(e(1, 1000.0, 40.0, true), e(3, 2000.0, 70.0, true)))
        assertEquals(1, before.segments.size)
        // A full entry is back-dated into the middle of the existing segment.
        val after = EconomyEngine.compute(
            listOf(e(1, 1000.0, 40.0, true), e(3, 2000.0, 70.0, true), e(2, 1400.0, 28.0, true, day = 2)),
        )
        assertEquals(2, after.segments.size)
        assertEquals(400.0, after.segments[0].distanceKm, eps)
        assertEquals(28.0, after.segments[0].quantity, eps)
        assertEquals(600.0, after.segments[1].distanceKm, eps)
        assertEquals(70.0, after.segments[1].quantity, eps)
    }

    @Test
    fun odometerCorrection_fixesInvalidSegment() {
        // Typo: 15000 entered instead of 1500 -> next full has a lower odometer.
        val wrong = listOf(e(1, 1000.0, 40.0, true), e(2, 15000.0, 35.0, true), e(3, 2000.0, 35.0, true, day = 3))
        val before = EconomyEngine.compute(wrong)
        // Sorted by odometer: 1000 -> 2000 -> 15000; the date order contradicts it.
        assertTrue(EconomyEngine.odometerLooksWrong(wrong.filter { it.id != 2L }, wrong[1]))
        assertEquals(2, before.segments.size)
        val corrected = EconomyEngine.compute(
            listOf(e(1, 1000.0, 40.0, true), e(2, 1500.0, 35.0, true), e(3, 2000.0, 35.0, true, day = 3)),
        )
        assertEquals(2, corrected.segments.size)
        assertTrue(corrected.segments.all { it.isValid })
        assertEquals(7.0, corrected.average100Km!!, eps)
    }

    @Test
    fun odometerWarning_onlyWhenDecreasingByDate() {
        val existing = listOf(e(1, 1000.0, 40.0, true, day = 1), e(2, 2000.0, 40.0, true, day = 3))
        assertFalse(EconomyEngine.odometerLooksWrong(existing, e(9, 1500.0, 10.0, true, day = 2)))
        assertTrue(EconomyEngine.odometerLooksWrong(existing, e(9, 900.0, 10.0, true, day = 2)))
        assertTrue(EconomyEngine.odometerLooksWrong(existing, e(9, 2500.0, 10.0, true, day = 2)))
        // Editing an existing entry does not compare it with itself.
        assertFalse(EconomyEngine.odometerLooksWrong(existing, existing[0]))
    }

    @Test
    fun zeroDistance_isInvalid_andFlagged() {
        val r = EconomyEngine.compute(listOf(e(1, 1000.0, 40.0, true), e(2, 1000.0, 10.0, true)))
        assertEquals(InvalidReason.NON_POSITIVE_DISTANCE, r.segments.single().invalidReason)
        assertNull(r.segments.single().per100Km)
        assertNull(r.average100Km)
    }

    @Test
    fun equalOdometerTie_isInvalid_andBaselineStillAdvances() {
        // Entries are ordered by odometer, so a decreasing odometer shows up as a tie or a date/odometer
        // conflict (see odometerWarning_onlyWhenDecreasingByDate); a tie has zero distance.
        val r = EconomyEngine.compute(
            listOf(e(1, 1000.0, 40.0, true), e(2, 1000.0, 5.0, true), e(3, 1400.0, 28.0, true)),
        )
        assertEquals(InvalidReason.NON_POSITIVE_DISTANCE, r.segments[0].invalidReason)
        assertTrue(r.segments[1].isValid)
        assertEquals(7.0, r.average100Km!!, eps)
    }

    @Test
    fun phevMixedStream_hasSeparateEconomyPerStream() {
        val vehicle = vehicle(DistanceUnit.KM, VolumeUnit.L, Powertrain.PHEV)
        val logs = listOf(
            log(1, 0.0, 40.0, EnergyStream.LIQUID, true),
            log(2, 500.0, 25.0, EnergyStream.LIQUID, true), // 5 L/100km
            log(3, 0.0, 10.0, EnergyStream.ELECTRIC, true),
            log(4, 500.0, 60.0, EnergyStream.ELECTRIC, true), // 12 kWh/100km
        )
        val byStream = computeByStream(logs, vehicle)
        assertEquals(5.0, byStream.getValue(EnergyStream.LIQUID).average100Km!!, eps)
        assertEquals(12.0, byStream.getValue(EnergyStream.ELECTRIC).average100Km!!, eps)
        // Cost per distance combines both streams' costs over the total distance driven.
        val perKm = EconomyEngine.costPerKmMinor(
            energyMinor = 3_000 + 1_500, serviceMinor = 0, expensesMinor = 0,
            distanceKm = EconomyEngine.periodDistanceKm(logs.map { it.odometer }),
        )
        assertEquals(9.0, perKm!!, eps)
    }

    @Test
    fun evPartialCharges_sumIntoNextFull() {
        val r = EconomyEngine.compute(
            listOf(
                e(1, 0.0, 50.0, true),
                e(2, 100.0, 15.0, false),
                e(3, 200.0, 10.0, false),
                e(4, 400.0, 30.0, true),
            ),
        )
        assertEquals(55.0, r.segments.single().quantity, eps)
        assertEquals(55.0 / 400.0 * 100.0, r.average100Km!!, eps) // 13.75 kWh/100km
    }

    @Test
    fun rounding_happensAtDisplayOnly() {
        val r = EconomyEngine.compute(listOf(e(1, 0.0, 10.0, true), e(2, 300.0, 20.0, true)))
        val raw = r.average100Km!! // 6.6666...
        assertEquals(20.0 / 300.0 * 100.0, raw, 0.0)
        assertNotNull(EconomyDisplay.value(raw, EconomyUnit.L_PER_100KM, 1))
        assertEquals(6.7, EconomyDisplay.value(raw, EconomyUnit.L_PER_100KM, 1)!!, 0.0)
        assertEquals(6.67, EconomyDisplay.value(raw, EconomyUnit.L_PER_100KM, 2)!!, 0.0)
        // The unrounded value is untouched, and a later conversion starts from it, not from 6.7.
        assertEquals(raw, r.average100Km!!, 0.0)
        assertEquals(235.215 / raw, UnitConversionHelper.mpgUs(raw), 0.0)
    }

    @Test
    fun costPerKm_isNullWithoutDistance() {
        assertNull(EconomyEngine.costPerKmMinor(1_000, 0, 0, 0.0))
        assertEquals(2.5, EconomyEngine.costPerKmMinor(1_000, 500, 500, 800.0)!!, eps)
        assertEquals(0.0, EconomyEngine.periodDistanceKm(listOf(5.0)), 0.0)
        assertEquals(300.0, EconomyEngine.periodDistanceKm(listOf(1300.0, 1000.0, 1100.0)), 0.0)
    }

    private object UnitConversionHelper {
        fun mpgUs(per100Km: Double) =
            com.postpole.wheelfile.domain.units.UnitConversion.economyForDisplay(per100Km, EconomyUnit.MPG_US)!!
    }

    private fun vehicle(distance: DistanceUnit, volume: VolumeUnit, powertrain: Powertrain) = Vehicle(
        id = 1, name = "Test", type = VehicleType.CAR, powertrain = powertrain,
        distanceUnit = distance, volumeUnit = volume, economyDisplay = EconomyUnit.L_PER_100KM,
        currency = "EUR", createdAt = 0, updatedAt = 0,
    )

    private fun log(id: Long, odo: Double, qty: Double, stream: EnergyStream, full: Boolean) = EnergyLog(
        id = id, vehicleId = 1, stream = stream, dateTime = id * 86_400_000L, odometer = odo, quantity = qty,
        isFull = full, createdAt = 0, updatedAt = 0,
    )
}
