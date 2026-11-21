package com.postpole.wheelfile.export

import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.ZoneOffset
import java.util.Locale

// Robolectric cannot create a real PdfDocument on the JVM (its native part is missing), so these tests drive the layout
// through a fake PageSink that hands out a plain Canvas and counts pages. The Android PdfDocument path is
// AndroidPdfSink, a thin wrapper that only a device or emulator run exercises.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class PdfReportTest {
    private class CountingSink : PageSink {
        var started = 0
        var finished = 0
        var written = false
        var closed = false
        override fun startPage(width: Int, height: Int, number: Int): Canvas {
            assertEquals("pages are numbered in order", started + 1, number)
            assertEquals("the previous page was finished first", started, finished)
            started++
            return Canvas()
        }
        override fun finishPage() { finished++ }
        override fun writeTo(out: OutputStream) { written = true; out.write("%PDF".toByteArray()) }
        override fun close() { closed = true }
    }

    private var sink = CountingSink()

    private val labels = PdfLabels(
        title = "Vehicle history report", generated = { "Generated $it" }, vehicleSection = "Vehicle", plate = "Plate",
        vin = "VIN", makeModelYear = "Make, model, year", odometer = "Odometer", unitsLine = "Units: km, L",
        costsSection = "Costs", fuelOrEnergy = "Fuel and energy", service = "Service", expenses = "Expenses", total = "Total",
        costPerDistance = { "About $it per km" }, economySection = "Fuel economy", economyAverage = "Average",
        economyEmpty = "Not enough full fill-ups yet", servicesSection = "Service history", servicesEmpty = "No services",
        colDate = "Date", colOdometer = "Odometer", colItems = "Work done", colCost = "Cost",
        attachmentsSection = "Attachments on file", attachmentsLine = { t, n -> "$t: $n" },
        footer = { page -> "Page $page" }, economyUnit = { it.name }, distanceUnit = "km",
        serviceFallback = "Service",
    )

    private fun builder(): PdfReportBuilder {
        sink = CountingSink()
        return PdfReportBuilder(labels, Locale.US, ZoneOffset.UTC, { ExportSampleData.T0 }, { sink })
    }

    private fun data(extraServices: Int = 0): VehicleReportData {
        val s = ExportSampleData.snapshot()
        val v = s.vehicles.first()
        val services = s.serviceRecords + (1..extraServices).map { s.serviceRecords.first().copy(id = 100L + it, dateTime = ExportSampleData.T0 + it * 86_400_000L) }
        return VehicleReportData(
            vehicle = v, logs = s.energyLogs.filter { it.vehicleId == v.id }, services = services,
            expenses = s.expenses, documents = s.documents, attachments = s.attachments,
            stats = VehicleStatsCalculator.compute(v, s.energyLogs, services, s.expenses),
        )
    }

    @Test
    fun smokeTestProducesAtLeastOnePage() {
        val out = ByteArrayOutputStream()
        val pages = builder().write(data(), out)
        assertTrue("page count > 0", pages > 0)
        assertEquals(pages, sink.finished)
        assertTrue("file written", out.size() > 0 && sink.written)
        assertTrue("the document is always closed", sink.closed)
    }

    @Test
    fun longServiceHistoryFlowsOntoMorePages() {
        val short = builder().write(data(), ByteArrayOutputStream())
        val long = builder().write(data(extraServices = 150), ByteArrayOutputStream())
        assertEquals(1, short)
        assertTrue("150 services need more than one page, got $long", long > 1)
    }

    @Test
    fun reportWithoutAttachmentsIndexStillRenders() {
        assertTrue(builder().write(data(), ByteArrayOutputStream(), includeAttachmentsIndex = false) > 0)
    }

    @Test
    fun emptyVehicleStillRenders() {
        val v = ExportSampleData.vehicle(9, "Empty")
        val pages = builder().write(
            VehicleReportData(v, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), VehicleStatsCalculator.compute(v, emptyList())),
            ByteArrayOutputStream(),
        )
        assertEquals(1, pages)
    }
}
