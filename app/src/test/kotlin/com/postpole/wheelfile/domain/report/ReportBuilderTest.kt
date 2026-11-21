package com.postpole.wheelfile.domain.report

import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import com.postpole.wheelfile.ui.vehicle.VehicleDetailViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class ReportBuilderTest {
    private val zone = ZoneId.of("UTC")
    private val now = ms(LocalDateTime.of(2026, 6, 15, 10, 0))

    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    private fun vehicle(id: Long = 1, currency: String = "EUR") = Vehicle(
        id = id, name = "Car $id", type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = currency, createdAt = 1, updatedAt = 1,
    )

    private fun log(
        id: Long, at: LocalDateTime, odometer: Double, quantity: Double, totalMinor: Long?,
        pricePerUnitMinor: Long? = null, full: Boolean = true, vehicleId: Long = 1, stream: EnergyStream = EnergyStream.LIQUID,
    ) = EnergyLog(
        id = id, vehicleId = vehicleId, stream = stream, dateTime = ms(at), odometer = odometer, quantity = quantity,
        pricePerUnitMinor = pricePerUnitMinor, totalMinor = totalMinor, isFull = full, createdAt = 1, updatedAt = 1,
    )

    private fun expense(id: Long, at: LocalDateTime, minor: Long) = Expense(
        id = id, vehicleId = 1, dateTime = ms(at), category = ExpenseCategory.PARKING, amountMinor = minor, createdAt = 1, updatedAt = 1,
    )

    private fun service(id: Long, at: LocalDateTime, minor: Long) = ServiceRecord(
        id = id, vehicleId = 1, dateTime = ms(at), items = listOf("Oil"), totalMinor = minor, createdAt = 1, updatedAt = 1,
    )

    // ---- the 12-month window ----

    @Test fun twelveMonthWindowIsTheCurrentMonthAndTheElevenBefore() {
        val w = ReportWindow.last12Months(now, zone)
        assertEquals(ms(LocalDateTime.of(2025, 7, 1, 0, 0)), w.startMillis)
        assertEquals(ms(LocalDateTime.of(2026, 7, 1, 0, 0)), w.endMillis)
    }

    @Test fun windowBoundariesAreInclusiveAtTheStartAndExclusiveAtTheEnd() {
        val w = ReportWindow.last12Months(now, zone)
        assertTrue(w.contains(ms(LocalDateTime.of(2025, 7, 1, 0, 0))))
        assertFalse(w.contains(ms(LocalDateTime.of(2025, 7, 1, 0, 0)) - 1))
        assertTrue(w.contains(ms(LocalDateTime.of(2026, 7, 1, 0, 0)) - 1))
        assertFalse(w.contains(ms(LocalDateTime.of(2026, 7, 1, 0, 0))))
    }

    @Test fun windowCrossesAYearEndAndALeapDayCorrectly() {
        val january = ms(LocalDateTime.of(2024, 1, 20, 12, 0))
        val w = ReportWindow.last12Months(january, zone)
        assertEquals(ms(LocalDateTime.of(2023, 2, 1, 0, 0)), w.startMillis)
        assertEquals(ms(LocalDateTime.of(2024, 2, 1, 0, 0)), w.endMillis)
    }

    @Test fun customWindowIncludesBothDaysAndSwapsReversedDates() {
        val w = ReportWindow.custom(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), zone)
        assertTrue(w.contains(ms(LocalDateTime.of(2026, 3, 10, 0, 0))))
        assertTrue(w.contains(ms(LocalDateTime.of(2026, 3, 12, 23, 59))))
        assertFalse(w.contains(ms(LocalDateTime.of(2026, 3, 13, 0, 0))))
        assertEquals(w, ReportWindow.custom(LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 10), zone))
    }

    // ---- monthly cost ----

    @Test fun monthlyCostFillsTwelveMonthsAndSplitsCategories() {
        val source = CostSource(
            "EUR",
            logs = listOf(log(1, LocalDateTime.of(2026, 6, 2, 8, 0), 1000.0, 40.0, 7000), log(2, LocalDateTime.of(2026, 6, 20, 8, 0), 1400.0, 30.0, 5000)),
            services = listOf(service(1, LocalDateTime.of(2026, 5, 3, 9, 0), 20_000)),
            expenses = listOf(expense(1, LocalDateTime.of(2025, 7, 15, 9, 0), 1_500)),
        )
        val costs = ReportBuilder.monthlyCosts(listOf(source), ReportWindow.last12Months(now, zone), zone).single()
        assertEquals("EUR", costs.currency)
        assertEquals(12, costs.months.size)
        assertEquals(YearMonth.of(2025, 7), costs.months.first().month)
        assertEquals(YearMonth.of(2026, 6), costs.months.last().month)
        assertEquals(1_500L, costs.months.first().expenseMinor)
        assertEquals(20_000L, costs.months.first { it.month == YearMonth.of(2026, 5) }.serviceMinor)
        // The second log is dated after "now" but still inside the current month, so it counts.
        assertEquals(12_000L, costs.months.last().energyMinor)
        assertEquals(0L, costs.months.first { it.month == YearMonth.of(2025, 12) }.totalMinor)
        assertEquals(33_500L, costs.totalMinor)
    }

    @Test fun entriesOutsideTheWindowAreLeftOut() {
        val source = CostSource(
            "EUR", emptyList(), emptyList(),
            listOf(
                expense(1, LocalDateTime.of(2025, 6, 30, 23, 59), 9_999), // one minute before the window
                expense(2, LocalDateTime.of(2025, 7, 1, 0, 0), 100),
                expense(3, LocalDateTime.of(2026, 7, 1, 0, 0), 9_999), // first instant after it
            ),
        )
        val costs = ReportBuilder.monthlyCosts(listOf(source), ReportWindow.last12Months(now, zone), zone).single()
        assertEquals(100L, costs.totalMinor)
    }

    @Test fun currenciesAreKeptSeparateAndNeverConverted() {
        val eur = CostSource("EUR", emptyList(), emptyList(), listOf(expense(1, LocalDateTime.of(2026, 6, 1, 0, 0), 1_000)))
        val usd = CostSource("USD", emptyList(), emptyList(), listOf(expense(2, LocalDateTime.of(2026, 6, 2, 0, 0), 2_000)))
        val eur2 = CostSource("EUR", emptyList(), emptyList(), listOf(expense(3, LocalDateTime.of(2026, 6, 3, 0, 0), 500)))
        val costs = ReportBuilder.monthlyCosts(listOf(eur, usd, eur2), ReportWindow.last12Months(now, zone), zone)
        assertEquals(listOf("EUR", "USD"), costs.map { it.currency })
        // Same currency adds up; a different currency never joins that total.
        assertEquals(1_500L, costs[0].totalMinor)
        assertEquals(2_000L, costs[1].totalMinor)
    }

    @Test fun allTimeRunsFromTheFirstToTheLastMonthWithData() {
        val source = CostSource(
            "EUR", emptyList(), emptyList(),
            listOf(expense(1, LocalDateTime.of(2022, 11, 5, 0, 0), 100), expense(2, LocalDateTime.of(2023, 2, 5, 0, 0), 200)),
        )
        val costs = ReportBuilder.monthlyCosts(listOf(source), ReportWindow.ALL, zone).single()
        assertEquals(listOf(YearMonth.of(2022, 11), YearMonth.of(2022, 12), YearMonth.of(2023, 1), YearMonth.of(2023, 2)), costs.months.map { it.month })
    }

    @Test fun noDataGivesNoMonthsOnAnUnboundedWindow() {
        val costs = ReportBuilder.monthlyCosts(listOf(CostSource("EUR", emptyList(), emptyList(), emptyList())), ReportWindow.ALL, zone).single()
        assertTrue(costs.months.isEmpty())
    }

    // ---- economy and price ----

    private val tanks = listOf(
        log(1, LocalDateTime.of(2025, 1, 5, 8, 0), 10_000.0, 40.0, 6_000),
        log(2, LocalDateTime.of(2025, 2, 5, 8, 0), 10_500.0, 30.0, 4_500), // 6.0 L/100 km, closes in February 2025
        log(3, LocalDateTime.of(2026, 3, 5, 8, 0), 11_000.0, 35.0, 5_250), // 7.0 L/100 km, closes in March 2026
    )

    @Test fun economyPointsAreFilteredByWhenTheirSegmentCloses() {
        val v = vehicle()
        val all = ReportBuilder.economySeries(v, tanks, ReportWindow.ALL).single()
        assertEquals(listOf(6.0, 7.0), all.points.map { it.value })
        val year = ReportBuilder.economySeries(v, tanks, ReportWindow.last12Months(now, zone)).single()
        assertEquals(listOf(7.0), year.points.map { it.value })
        assertEquals(EconomyUnit.L_PER_100KM, year.unit)
    }

    @Test fun noFullTanksMeansNoEconomyLine() {
        assertTrue(ReportBuilder.economySeries(vehicle(), tanks.take(1), ReportWindow.ALL).isEmpty())
    }

    @Test fun priceUsesTheStoredUnitPriceOrTotalOverQuantity() {
        val v = vehicle()
        val logs = listOf(
            log(1, LocalDateTime.of(2026, 5, 1, 8, 0), 1.0, 40.0, 7_436, pricePerUnitMinor = 185_900), // 1.859 per L stored
            log(2, LocalDateTime.of(2026, 6, 1, 8, 0), 2.0, 30.0, 6_000), // 60.00 / 30 = 2.00
            log(3, LocalDateTime.of(2026, 6, 2, 8, 0), 3.0, 30.0, null), // no price: left out
        )
        val series = ReportBuilder.priceSeries(v, logs, ReportWindow.ALL).single()
        assertEquals(listOf(1.859, 2.0), series.points.map { it.price })
    }

    // ---- compare and the Pro gate ----

    @Test fun comparisonKeepsEachVehicleInItsOwnCurrency() {
        val a = vehicle(1, "EUR")
        val b = vehicle(2, "USD")
        val ca = ReportBuilder.compare(a, tanks, emptyList(), emptyList(), ReportWindow.ALL)
        val cb = ReportBuilder.compare(b, emptyList(), emptyList(), listOf(expense(1, LocalDateTime.of(2026, 1, 1, 0, 0), 4_000)), ReportWindow.ALL)
        assertEquals(15_750L, ca.totalMinor)
        assertEquals("EUR", ca.vehicle.currency)
        assertEquals(4_000L, cb.totalMinor)
        assertEquals("USD", cb.vehicle.currency)
        assertNull(cb.averageEconomy)
    }

    private fun data(v: Vehicle = vehicle(), logs: List<EnergyLog> = tanks, expenses: List<Expense> = emptyList()) =
        VehicleData(v, logs, emptyList(), expenses, VehicleStatsCalculator.compute(v, logs, emptyList(), expenses))

    private val old = expense(9, LocalDateTime.of(2023, 4, 1, 0, 0), 500)

    @Test fun freeUsersSeeOnlyTwelveMonthsEvenWhenAnotherRangeIsPicked() {
        val free = VehicleDetailViewModel.buildReports(data(expenses = listOf(old)), ReportRange.ALL_TIME, null, null, false, emptyList(), now, zone)
        assertTrue(free.locked)
        assertEquals(12, free.costs.single().months.size)
        assertEquals(listOf(7.0), free.economy.single().points.map { it.value })
        assertEquals(ReportRange.ALL_TIME, free.range)
    }

    @Test fun proUsersSeeEveryRange() {
        val all = VehicleDetailViewModel.buildReports(data(expenses = listOf(old)), ReportRange.ALL_TIME, null, null, true, emptyList(), now, zone)
        assertFalse(all.locked)
        assertEquals(listOf(6.0, 7.0), all.economy.single().points.map { it.value })
        assertEquals(YearMonth.of(2023, 4), all.costs.single().months.first().month)

        val custom = VehicleDetailViewModel.buildReports(
            data(), ReportRange.CUSTOM, ms(LocalDateTime.of(2025, 1, 1, 0, 0)), ms(LocalDateTime.of(2025, 2, 28, 0, 0)), true, emptyList(), now, zone,
        )
        assertEquals(listOf(6.0), custom.economy.single().points.map { it.value })

        val compare = VehicleDetailViewModel.buildReports(data(), ReportRange.COMPARE, null, null, true, listOf(data(), data(vehicle(2, "USD"))), now, zone)
        assertEquals(2, compare.comparisons.size)
    }

    @Test fun compareOnTheFreePlanProducesNoComparison() {
        val free = VehicleDetailViewModel.buildReports(data(), ReportRange.COMPARE, null, null, false, listOf(data()), now, zone)
        assertTrue(free.locked)
        assertTrue(free.comparisons.isEmpty())
    }

    @Test fun onlyTheTwelveMonthRangeIsFree() {
        assertEquals(listOf(ReportRange.LAST_12_MONTHS), ReportRange.entries.filter { !it.isPro })
    }
}
