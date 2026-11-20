package com.postpole.wheelfile.domain

import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import com.postpole.wheelfile.domain.history.HistoryBuilder
import com.postpole.wheelfile.domain.history.HistoryFilter
import com.postpole.wheelfile.domain.history.HistoryKind
import com.postpole.wheelfile.domain.units.LocaleDefaults
import com.postpole.wheelfile.domain.vehicle.VehicleLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class VehicleDomainTest {
    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_700_000_000_000L

    private fun vehicle(powertrain: Powertrain = Powertrain.PETROL, unit: EconomyUnit = EconomyUnit.L_PER_100KM) = Vehicle(
        id = 1, name = "V", type = VehicleType.CAR, powertrain = powertrain, distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.L, economyDisplay = unit, currency = "EUR", createdAt = 0, updatedAt = 0,
    )

    private var nextId = 1L
    private fun log(day: Int, odo: Double, qty: Double, full: Boolean = true, stream: EnergyStream = EnergyStream.LIQUID, total: Long? = null, missed: Boolean = false) =
        EnergyLog(
            id = nextId++, vehicleId = 1, stream = stream, dateTime = t0 + day * this.day, odometer = odo, quantity = qty,
            totalMinor = total, isFull = full, missedPrevious = missed, createdAt = 0, updatedAt = 0,
        )

    @Test fun freeCap_isTwoActiveVehicles() {
        assertTrue(VehicleLimit.canAddActive(0, false))
        assertTrue(VehicleLimit.canAddActive(1, false))
        assertFalse(VehicleLimit.canAddActive(2, false))
        assertTrue(VehicleLimit.canAddActive(5, true))
    }

    @Test fun stats_averageIsTotalOverTotalAndDeltaComparesLatestToAverage() {
        val logs = listOf(
            log(0, 1000.0, 40.0),
            log(10, 1500.0, 40.0), // 8 L/100km
            log(20, 2000.0, 30.0), // 6 L/100km
        )
        val s = VehicleStatsCalculator.compute(vehicle(), logs).streams.single()
        assertEquals(7.0, s.average!!, 0.0) // (40+30)/(1000)*100
        assertEquals(6.0, s.latest!!, 0.0)
        assertEquals(-14.3, s.deltaPercent!!, 0.0) // (6-7)/7
        assertEquals(2, s.validSegments)
    }

    @Test fun stats_noDeltaWithASingleSegment() {
        val s = VehicleStatsCalculator.compute(vehicle(), listOf(log(0, 1000.0, 40.0), log(5, 1500.0, 40.0))).streams.single()
        assertNull(s.deltaPercent)
        assertEquals(8.0, s.average!!, 0.0)
    }

    @Test fun stats_missedFillSegmentIsLeftOutAndCounted() {
        val logs = listOf(log(0, 1000.0, 40.0), log(10, 1500.0, 40.0, missed = true), log(20, 2000.0, 30.0))
        val s = VehicleStatsCalculator.compute(vehicle(), logs).streams.single()
        assertEquals(1, s.validSegments)
        assertEquals(1, s.invalidSegments)
        assertEquals(6.0, s.average!!, 0.0)
    }

    @Test fun stats_phevHasTwoStreamsWithOwnUnits() {
        val logs = listOf(
            log(0, 1000.0, 40.0), log(10, 1500.0, 30.0),
            log(1, 1000.0, 20.0, stream = EnergyStream.ELECTRIC), log(11, 1400.0, 80.0, stream = EnergyStream.ELECTRIC),
        )
        val stats = VehicleStatsCalculator.compute(vehicle(Powertrain.PHEV), logs)
        assertEquals(listOf(EnergyStream.LIQUID, EnergyStream.ELECTRIC), stats.streams.map { it.stream })
        assertEquals(EconomyUnit.KWH_PER_100KM, stats.streams[1].unit)
        assertEquals(20.0, stats.streams[1].average!!, 0.0)
    }

    @Test fun stats_costPerDistanceCombinesEnergyOverTheDrivenDistance() {
        val logs = listOf(log(0, 1000.0, 40.0, total = 6000), log(10, 1500.0, 40.0, total = 4000))
        val c = VehicleStatsCalculator.compute(vehicle(), logs).costs
        assertEquals(10_000L, c.totalMinor)
        assertEquals(20.0, c.costPerDistanceMinor!!, 0.0) // 10,000 cents / 500 km
    }

    @Test fun stats_odometerIsTheHighestReading() {
        val s = VehicleStatsCalculator.compute(vehicle(), listOf(log(0, 1000.0, 40.0), log(1, 900.0, 5.0, full = false)))
        assertEquals(1000.0, s.odometer!!, 0.0)
        assertNull(VehicleStatsCalculator.compute(vehicle(), emptyList()).odometer)
    }

    private fun data(logs: List<EnergyLog>) = VehicleData(vehicle(), logs, emptyList(), emptyList(), VehicleStatsCalculator.compute(vehicle(), logs))

    @Test fun history_groupsByMonthNewestFirstAndFilters() {
        val logs = listOf(log(0, 1000.0, 40.0, total = 100), log(10, 1500.0, 40.0, total = 200), log(40, 2000.0, 30.0, total = 300))
        val months = HistoryBuilder.build(data(logs), HistoryFilter.ALL, ZoneOffset.UTC)
        assertEquals(2, months.size)
        assertTrue(months[0].month > months[1].month)
        assertEquals(300L, months[0].totalMinor)
        assertEquals(300L, months[1].totalMinor)
        assertTrue(HistoryBuilder.build(data(logs), HistoryFilter.CHARGING, ZoneOffset.UTC).isEmpty())
        assertEquals(3, HistoryBuilder.build(data(logs), HistoryFilter.FUEL, ZoneOffset.UTC).sumOf { it.rows.size })
    }

    @Test fun history_rowsCarryTheEconomyOfTheirSegmentAndFlagBadOdometer() {
        val logs = listOf(log(0, 1000.0, 40.0), log(10, 1500.0, 40.0), log(20, 1500.0, 30.0))
        val rows = HistoryBuilder.build(data(logs), HistoryFilter.ALL, ZoneOffset.UTC).flatMap { it.rows }
        val byOdo = rows.filter { it.kind == HistoryKind.FILL_UP }
        assertNotNull(byOdo.firstOrNull { it.economy == 8.0 })
        assertTrue(byOdo.any { it.checkOdometer })
    }

    @Test fun locale_defaults() {
        val us = LocaleDefaults.forLocale(Locale.US)
        assertEquals(DistanceUnit.MI, us.distance)
        assertEquals(VolumeUnit.US_GAL, us.volume)
        assertEquals(EconomyUnit.MPG_US, us.economy)
        assertEquals("USD", us.currency)

        val de = LocaleDefaults.forLocale(Locale.GERMANY)
        assertEquals(DistanceUnit.KM, de.distance)
        assertEquals(EconomyUnit.L_PER_100KM, de.economy)
        assertEquals("EUR", de.currency)

        val gb = LocaleDefaults.forLocale(Locale.UK)
        assertEquals(DistanceUnit.MI, gb.distance)
        assertEquals(VolumeUnit.L, gb.volume)
        assertEquals(EconomyUnit.MPG_UK, gb.economy)

        assertEquals(EconomyUnit.MI_PER_KWH, LocaleDefaults.forLocale(Locale.US, Powertrain.ELECTRIC).economy)
        assertEquals("USD", LocaleDefaults.forLocale(Locale.ROOT).currency)
    }
}
