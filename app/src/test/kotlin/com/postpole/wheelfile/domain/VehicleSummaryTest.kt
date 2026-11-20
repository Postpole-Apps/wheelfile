package com.postpole.wheelfile.domain

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.economy.VehicleSummary
import com.postpole.wheelfile.domain.history.EntryFlag
import com.postpole.wheelfile.domain.history.HistoryBuilder
import com.postpole.wheelfile.domain.history.HistoryFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class VehicleSummaryTest {
    private val vehicle = Vehicle(
        id = 1, name = "Golf", type = VehicleType.CAR, powertrain = Powertrain.PHEV,
        distanceUnit = DistanceUnit.KM, volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM,
        currency = "EUR", createdAt = 0, updatedAt = 0,
    )

    private var nextId = 1L
    private fun log(odo: Double, qty: Double, stream: EnergyStream = EnergyStream.LIQUID, full: Boolean = true, at: Long = odo.toLong(), total: Long? = null) =
        EnergyLog(
            id = nextId++, vehicleId = 1, stream = stream, dateTime = at, odometer = odo, quantity = qty, isFull = full,
            totalMinor = total, createdAt = 0, updatedAt = 0,
        )

    @Test
    fun phevHasTwoIndependentStreams() {
        val logs = listOf(
            log(1000.0, 40.0), log(1500.0, 35.0), // liquid: 35 L / 500 km = 7.0
            log(1000.0, 10.0, EnergyStream.ELECTRIC), log(1100.0, 15.0, EnergyStream.ELECTRIC), // 15 kWh / 100 km
        )
        val streams = VehicleSummary.streams(logs, vehicle)
        assertEquals(listOf(EnergyStream.LIQUID, EnergyStream.ELECTRIC), streams.map { it.stream })
        assertEquals(7.0, streams[0].last100Km!!, 1e-9)
        assertEquals(15.0, streams[1].last100Km!!, 1e-9)
    }

    @Test
    fun deltaComparesTheLatestWindowWithTheAverageOfEarlierOnes() {
        val logs = listOf(log(0.0, 40.0), log(500.0, 40.0), log(1000.0, 30.0)) // 8.0, then 6.0 -> 25% better
        val delta = VehicleSummary.streams(logs, vehicle).single().delta!!
        assertTrue(delta.better)
        assertEquals(25.0, delta.percent, 1e-9)
    }

    @Test
    fun noDeltaWithASingleWindow() {
        val logs = listOf(log(0.0, 40.0), log(500.0, 40.0))
        assertNull(VehicleSummary.streams(logs, vehicle).single().delta)
    }

    @Test
    fun noEconomyBeforeTwoFullEntries() {
        val streams = VehicleSummary.streams(listOf(log(0.0, 40.0)), vehicle)
        assertNull(streams.single().last100Km)
    }

    @Test
    fun costsCombineEnergyAndDistance() {
        val now = 2_000_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val logs = listOf(
            log(1000.0, 40.0, at = now - 60 * day, total = 7000), log(1500.0, 35.0, at = now - 30 * day, total = 6000),
        )
        val costs = VehicleSummary.costs(vehicle, logs, emptyList(), emptyList(), now)
        assertEquals(13_000L, costs.totalMinor)
        assertEquals(26.0, costs.costPerKmMinor!!, 1e-9) // 13000 minor over 500 km
        assertNotNull(costs.perMonthMinor)
    }

    @Test
    fun entriesOlderThanTwelveMonthsAreLeftOutOfCosts() {
        val now = 2_000_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val logs = listOf(log(1000.0, 40.0, at = now - 400 * day, total = 9999))
        assertEquals(0L, VehicleSummary.costs(vehicle, logs, emptyList(), emptyList(), now).totalMinor)
    }

    @Test
    fun historyGroupsByMonthNewestFirstAndFilters() {
        val jan = 1_704_110_400_000L // 2024-01-01T12:00Z
        val feb = 1_706_788_800_000L // 2024-02-01T12:00Z
        val logs = listOf(
            log(100.0, 10.0, at = jan, total = 1000),
            log(200.0, 10.0, at = feb, total = 2000),
            log(250.0, 12.0, EnergyStream.ELECTRIC, at = feb + 1000, total = 500),
        )
        val all = HistoryBuilder.build(vehicle, logs, emptyList(), emptyList(), HistoryFilter.ALL, ZoneOffset.UTC)
        assertEquals(listOf(2, 1), all.map { it.month.monthValue })
        assertEquals(2500L, all.first().totalMinor)
        val charges = HistoryBuilder.build(vehicle, logs, emptyList(), emptyList(), HistoryFilter.CHARGES, ZoneOffset.UTC)
        assertEquals(1, charges.single().entries.size)
        val fills = HistoryBuilder.build(vehicle, logs, emptyList(), emptyList(), HistoryFilter.FILL_UPS, ZoneOffset.UTC)
        assertEquals(2, fills.sumOf { it.entries.size })
    }

    @Test
    fun nonIncreasingOdometerIsFlaggedCheckOdometer() {
        val first = log(1000.0, 40.0, at = 1)
        val second = log(1000.0, 35.0, at = 2)
        val flags = HistoryBuilder.flagsFor(listOf(first, second), vehicle)
        assertEquals(EntryFlag.CHECK_ODOMETER, flags[second.id])
        assertFalse(flags.containsKey(first.id))
    }
}
