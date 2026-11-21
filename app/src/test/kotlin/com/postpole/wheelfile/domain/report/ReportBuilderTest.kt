package com.postpole.wheelfile.domain.report

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class ReportBuilderTest {
    private val zone = ZoneOffset.UTC
    private fun ms(s: String): Long = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    private fun vehicle(id: Long, currency: String, name: String = "Car $id") = Vehicle(
        id = id, name = name, type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = currency, createdAt = 1, updatedAt = 1,
    )

    private var nextId = 1L
    private fun log(
        vehicleId: Long, at: String, odometer: Double = 0.0, quantity: Double = 40.0, total: Long? = null,
        pricePerUnit: Long? = null, full: Boolean = true,
    ) = EnergyLog(
        id = nextId++, vehicleId = vehicleId, stream = EnergyStream.LIQUID, dateTime = ms(at), odometer = odometer,
        quantity = quantity, pricePerUnitMinor = pricePerUnit, totalMinor = total, isFull = full, createdAt = 1, updatedAt = 1,
    )

    private fun service(vehicleId: Long, at: String, total: Long) =
        ServiceRecord(id = nextId++, vehicleId = vehicleId, dateTime = ms(at), totalMinor = total, createdAt = 1, updatedAt = 1)

    private fun expense(vehicleId: Long, at: String, amount: Long) = Expense(
        id = nextId++, vehicleId = vehicleId, dateTime = ms(at), category = ExpenseCategory.INSURANCE, amountMinor = amount,
        createdAt = 1, updatedAt = 1,
    )

    private fun source(v: Vehicle, logs: List<EnergyLog> = emptyList(), services: List<ServiceRecord> = emptyList(), expenses: List<Expense> = emptyList()) =
        ReportSource(v, logs, services, expenses)

    // ---- 12-month window ----

    @Test fun twelveMonthWindowRunsFromTheFirstOfTheEarliestMonthToTheEndOfThisMonth() {
        val w = ReportBuilder.last12Months(ms("2026-11-21T12:00:00"), zone)
        assertEquals(ms("2025-12-01T00:00:00"), w.startMs)
        assertEquals(ms("2026-12-01T00:00:00") - 1, w.endMs)
    }

    @Test fun windowBoundariesAreInclusiveAtTheEdges() {
        val w = ReportBuilder.last12Months(ms("2026-11-21T12:00:00"), zone)
        assertTrue(w.contains(ms("2025-12-01T00:00:00")))
        assertFalse(w.contains(ms("2025-12-01T00:00:00") - 1))
        assertTrue(w.contains(ms("2026-11-30T23:59:59")))
        assertFalse(w.contains(ms("2026-12-01T00:00:00")))
    }

    @Test fun windowCrossesTheYearBoundaryCorrectlyInJanuary() {
        val w = ReportBuilder.last12Months(ms("2026-01-05T08:00:00"), zone)
        assertEquals(ms("2025-02-01T00:00:00"), w.startMs)
        assertEquals(ms("2026-02-01T00:00:00") - 1, w.endMs)
    }

    @Test fun customWindowCoversBothEndDaysAndSwapsReversedDates() {
        val w = ReportBuilder.custom(LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-01"), zone)
        assertEquals(ms("2026-03-01T00:00:00"), w.startMs)
        assertEquals(ms("2026-03-11T00:00:00") - 1, w.endMs)
    }

    @Test fun rangesAreProExceptTheTwelveMonthWindow() {
        assertFalse(ReportRange.LAST_12_MONTHS.isPro)
        assertTrue(ReportRange.ALL_TIME.isPro)
        assertTrue(ReportRange.COMPARE.isPro)
        assertTrue(ReportRange.CUSTOM.isPro)
    }

    @Test fun rangeToWindow() {
        val now = ms("2026-11-21T12:00:00")
        assertEquals(ReportBuilder.last12Months(now, zone), ReportBuilder.window(ReportRange.LAST_12_MONTHS, now, null, zone))
        assertEquals(ReportWindow.ALL, ReportBuilder.window(ReportRange.ALL_TIME, now, null, zone))
        val custom = ReportWindow(1, 2)
        assertEquals(custom, ReportBuilder.window(ReportRange.CUSTOM, now, custom, zone))
    }

    // ---- monthly totals ----

    @Test fun monthlyCostsSplitFuelServiceAndExpensesAndFillEmptyMonths() {
        val v = vehicle(1, "EUR")
        val s = source(
            v,
            logs = listOf(log(1, "2026-10-03T10:00:00", total = 5_000), log(1, "2026-10-20T10:00:00", total = 4_500)),
            services = listOf(service(1, "2026-10-12T10:00:00", 12_000)),
            expenses = listOf(expense(1, "2026-11-02T10:00:00", 30_000)),
        )
        val window = ReportBuilder.last12Months(ms("2026-11-21T12:00:00"), zone)
        val series = ReportBuilder.monthlyCosts(listOf(s), window, zone).single()
        assertEquals("EUR", series.currency)
        assertEquals(12, series.months.size)
        assertEquals(YearMonth.of(2025, 12), series.months.first().month)
        assertEquals(YearMonth.of(2026, 11), series.months.last().month)
        val oct = series.months.first { it.month == YearMonth.of(2026, 10) }
        assertEquals(9_500, oct.fuelMinor)
        assertEquals(12_000, oct.serviceMinor)
        assertEquals(0, oct.expenseMinor)
        assertEquals(21_500, oct.totalMinor)
        assertEquals(30_000, series.months.last().expenseMinor)
        assertEquals(0, series.months.first { it.month == YearMonth.of(2026, 1) }.totalMinor)
    }

    @Test fun entriesOutsideTheWindowAreLeftOut() {
        val v = vehicle(1, "EUR")
        val s = source(
            v,
            logs = listOf(log(1, "2025-11-30T23:59:59", total = 7_000), log(1, "2025-12-01T00:00:00", total = 1_000)),
            expenses = listOf(expense(1, "2026-12-01T00:00:00", 9_999)),
        )
        val window = ReportBuilder.last12Months(ms("2026-11-21T12:00:00"), zone)
        val series = ReportBuilder.monthlyCosts(listOf(s), window, zone).single()
        assertEquals(1_000, series.totalMinor)
        assertEquals(1_000, series.months.first().fuelMinor)
    }

    @Test fun currenciesAreKeptSeparateAndNeverConverted() {
        val eurCar = vehicle(1, "EUR")
        val eurVan = vehicle(2, "EUR")
        val usdCar = vehicle(3, "USD")
        val sources = listOf(
            source(eurCar, logs = listOf(log(1, "2026-10-05T10:00:00", total = 5_000))),
            source(eurVan, expenses = listOf(expense(2, "2026-10-06T10:00:00", 2_500))),
            source(usdCar, logs = listOf(log(3, "2026-10-07T10:00:00", total = 9_900))),
        )
        val window = ReportBuilder.last12Months(ms("2026-11-21T12:00:00"), zone)
        val result = ReportBuilder.monthlyCosts(sources, window, zone)
        assertEquals(listOf("EUR", "USD"), result.map { it.currency })
        val oct = YearMonth.of(2026, 10)
        // Two EUR vehicles add up; the USD vehicle is a separate series with its own amount.
        assertEquals(7_500, result[0].months.first { it.month == oct }.totalMinor)
        assertEquals(9_900, result[1].months.first { it.month == oct }.totalMinor)
        assertEquals(7_500, result[0].totalMinor)
        assertEquals(9_900, result[1].totalMinor)
    }

    @Test fun openWindowRunsFromTheFirstToTheLastMonthWithData() {
        val v = vehicle(1, "EUR")
        val s = source(
            v,
            logs = listOf(log(1, "2024-02-10T10:00:00", total = 100)),
            expenses = listOf(expense(1, "2024-05-10T10:00:00", 200)),
        )
        val series = ReportBuilder.monthlyCosts(listOf(s), ReportWindow.ALL, zone).single()
        assertEquals(listOf(YearMonth.of(2024, 2), YearMonth.of(2024, 3), YearMonth.of(2024, 4), YearMonth.of(2024, 5)), series.months.map { it.month })
        assertEquals(0, series.months[1].totalMinor)
    }

    @Test fun noDataGivesNoMonthsForAnOpenWindowAndZeroMonthsForTheFreeWindow() {
        val v = vehicle(1, "EUR")
        assertTrue(ReportBuilder.monthlyCosts(listOf(source(v)), ReportWindow.ALL, zone).single().months.isEmpty())
        val window = ReportBuilder.last12Months(ms("2026-11-21T12:00:00"), zone)
        val free = ReportBuilder.monthlyCosts(listOf(source(v)), window, zone).single()
        assertEquals(12, free.months.size)
        assertEquals(0, free.totalMinor)
    }

    // ---- compare ----

    @Test fun compareGroupsVehiclesByCurrencyAndSortsBiggestFirst() {
        val a = vehicle(1, "EUR", "Golf")
        val b = vehicle(2, "EUR", "Van")
        val c = vehicle(3, "USD", "Truck")
        val sources = listOf(
            source(a, logs = listOf(log(1, "2026-05-01T10:00:00", total = 1_000))),
            source(b, services = listOf(service(2, "2026-05-02T10:00:00", 4_000))),
            source(c, expenses = listOf(expense(3, "2026-05-03T10:00:00", 700))),
        )
        val result = ReportBuilder.compare(sources, ReportWindow.ALL)
        assertEquals(listOf("EUR", "USD"), result.map { it.currency })
        assertEquals(listOf("Van", "Golf"), result[0].vehicles.map { it.name })
        assertEquals(listOf(4_000L, 1_000L), result[0].vehicles.map { it.totalMinor })
        assertEquals(700, result[1].vehicles.single().totalMinor)
    }

    @Test fun compareRespectsTheWindow() {
        val a = vehicle(1, "EUR")
        val s = source(a, logs = listOf(log(1, "2020-01-01T10:00:00", total = 500), log(1, "2026-05-01T10:00:00", total = 100)))
        val window = ReportBuilder.last12Months(ms("2026-11-21T12:00:00"), zone)
        assertEquals(100, ReportBuilder.compare(listOf(s), window).single().vehicles.single().totalMinor)
    }

    // ---- economy and price series ----

    @Test fun economySeriesHasOnePointPerValidSegmentAtTheClosingFillUp() {
        val v = vehicle(1, "EUR")
        val s = source(
            v,
            logs = listOf(
                log(1, "2026-08-01T10:00:00", odometer = 1_000.0, quantity = 40.0),
                log(1, "2026-09-01T10:00:00", odometer = 1_500.0, quantity = 30.0),
                log(1, "2026-10-01T10:00:00", odometer = 2_000.0, quantity = 35.0),
            ),
        )
        val series = ReportBuilder.economySeries(s, ReportWindow.ALL).single()
        assertEquals(EnergyStream.LIQUID, series.stream)
        assertEquals(EconomyUnit.L_PER_100KM, series.unit)
        assertEquals(listOf(ms("2026-09-01T10:00:00"), ms("2026-10-01T10:00:00")), series.points.map { it.dateTime })
        assertEquals(listOf(6.0, 7.0), series.points.map { it.value })
    }

    @Test fun economySeriesIsWindowedByTheClosingFillUpDate() {
        val v = vehicle(1, "EUR")
        val s = source(
            v,
            logs = listOf(
                log(1, "2026-08-01T10:00:00", odometer = 1_000.0),
                log(1, "2026-09-01T10:00:00", odometer = 1_500.0, quantity = 30.0),
                log(1, "2026-10-01T10:00:00", odometer = 2_000.0, quantity = 35.0),
            ),
        )
        val window = ReportWindow(ms("2026-09-15T00:00:00"), null)
        assertEquals(listOf(7.0), ReportBuilder.economySeries(s, window).single().points.map { it.value })
        assertTrue(ReportBuilder.economySeries(s, ReportWindow(ms("2026-11-01T00:00:00"), null)).isEmpty())
    }

    @Test fun economySeriesNeedsTwoFullFillUps() {
        val v = vehicle(1, "EUR")
        assertTrue(ReportBuilder.economySeries(source(v, logs = listOf(log(1, "2026-08-01T10:00:00", odometer = 1_000.0))), ReportWindow.ALL).isEmpty())
    }

    @Test fun priceSeriesUsesTheStoredUnitPriceOrDerivesItFromTheTotal() {
        val v = vehicle(1, "EUR")
        val s = source(
            v,
            logs = listOf(
                // 1.789 per litre is stored with three extra digits: 1.789 * 100 * 1000.
                log(1, "2026-08-01T10:00:00", quantity = 40.0, pricePerUnit = 178_900, total = 7_156),
                // No unit price: 5000 cents / 50 L = 1.00 per litre.
                log(1, "2026-09-01T10:00:00", quantity = 50.0, total = 5_000),
                log(1, "2026-09-15T10:00:00", quantity = 10.0),
            ),
        )
        val series = ReportBuilder.priceSeries(s, ReportWindow.ALL).single()
        assertEquals("EUR", series.currency)
        assertEquals(2, series.points.size)
        assertEquals(1.789, series.points[0].pricePerUnit, 1e-9)
        assertEquals(1.0, series.points[1].pricePerUnit, 1e-9)
    }
}
