package com.postpole.wheelfile.data.backup

import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
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
import com.postpole.wheelfile.domain.report.ReportBuilder
import com.postpole.wheelfile.domain.report.ReportWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale

/**
 * Robolectric's JVM has no native PDF writer (the real [PdfDocument] reports "document is closed"), so these tests run
 * the whole report layout (text, chart, pagination) against a minimal stand-in that keeps the page count. The bytes that
 * the real [PdfDocument] writes are not checked here.
 */
@Config(shadows = [PdfReportTest.ShadowPdfDocument::class])
@RunWith(RobolectricTestRunner::class)
class PdfReportTest {
    @Implements(PdfDocument::class)
    class ShadowPdfDocument {
        private var pages = 0
        private var open: PdfDocument.Page? = null
        private var closed = false

        @Implementation
        fun startPage(info: PdfDocument.PageInfo): PdfDocument.Page {
            check(!closed && open == null) { "previous page not finished" }
            return ReflectionHelpers.callConstructor(
                PdfDocument.Page::class.java,
                ClassParameter.from(Canvas::class.java, Canvas()),
                ClassParameter.from(PdfDocument.PageInfo::class.java, info),
            ).also { open = it }
        }

        @Implementation
        fun finishPage(page: PdfDocument.Page) {
            check(page === open) { "unknown page" }
            open = null
            pages++
        }

        @Implementation
        fun writeTo(out: OutputStream) {
            check(open == null) { "page not finished" }
            out.write("%PDF-1.4 pages=$pages\n".toByteArray())
        }

        @Implementation
        fun close() {
            closed = true
        }
    }

    private val labels = PdfLabels(
        title = "Vehicle history report", generated = { "Created $it" }, details = "Vehicle", summary = "Costs and economy",
        economy = "Economy over time", services = "Service history", expenses = "Expenses", attachments = "Attachments",
        totalCost = "Total cost", energyCost = "Fuel and energy", serviceCost = "Service", expenseCost = "Expenses",
        odometer = "Odometer", fillUps = "Fill-ups and charges", none = "Nothing recorded", page = { "Page $it" },
        economyUnit = { it.name }, attachmentsCount = { "$it attachments" },
    )

    private val vehicle = Vehicle(
        id = 1, name = "Test Hatch", type = VehicleType.CAR, powertrain = Powertrain.PETROL, make = "Acme", model = "H1", year = 2019,
        plate = "AB-123", vin = "VIN123", distanceUnit = DistanceUnit.KM, volumeUnit = VolumeUnit.L,
        economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", createdAt = 1, updatedAt = 1,
    )

    private fun input(logs: List<EnergyLog>, services: List<ServiceRecord>, expenses: List<Expense>): PdfReportInput {
        val stats = VehicleStatsCalculator.compute(vehicle, logs, services, expenses)
        return PdfReportInput(vehicle, logs, services, expenses, emptyList(), stats, ReportBuilder.economySeries(vehicle, logs, ReportWindow(null, null)))
    }

    private fun log(i: Int) = EnergyLog(
        id = i.toLong(), vehicleId = 1, stream = EnergyStream.LIQUID, dateTime = 1_700_000_000_000 + i * 86_400_000L * 10,
        odometer = 1000.0 + i * 500, quantity = 35.0 + i, totalMinor = 6000L + i * 100, isFull = true, createdAt = 1, updatedAt = 1,
    )

    @Test
    fun smokeTestWritesAtLeastOnePage() {
        val out = ByteArrayOutputStream()
        val pages = VehiclePdfReport(labels, Locale.US).render(
            input(
                logs = (0..5).map(::log),
                services = listOf(ServiceRecord(vehicleId = 1, dateTime = 1_700_500_000_000, items = listOf("Oil"), totalMinor = 12000, createdAt = 1, updatedAt = 1)),
                expenses = listOf(Expense(vehicleId = 1, dateTime = 1_700_600_000_000, category = ExpenseCategory.PARKING, amountMinor = 500, createdAt = 1, updatedAt = 1)),
            ),
            out,
        )
        assertTrue("page count $pages", pages > 0)
        assertEquals("%PDF-1.4 pages=$pages\n", out.toString())
    }

    @Test
    fun emptyVehicleStillGivesAPage() {
        val pages = VehiclePdfReport(labels, Locale.GERMANY).render(input(emptyList(), emptyList(), emptyList()), ByteArrayOutputStream())
        assertTrue(pages > 0)
    }

    @Test
    fun longHistoryFlowsOntoMorePages() {
        val services = (1..120).map {
            ServiceRecord(vehicleId = 1, dateTime = 1_700_000_000_000 + it * 86_400_000L, items = listOf("Item $it"), totalMinor = 1000L + it, createdAt = 1, updatedAt = 1)
        }
        val pages = VehiclePdfReport(labels, Locale.US).render(input((0..3).map(::log), services, emptyList()), ByteArrayOutputStream())
        assertTrue("page count $pages", pages > 1)
    }
}
