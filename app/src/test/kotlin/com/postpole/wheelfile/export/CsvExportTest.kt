package com.postpole.wheelfile.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.ZoneOffset
import java.util.zip.ZipInputStream

class CsvExportTest {
    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                out[e.name] = String(zip.readBytes(), Charsets.UTF_8)
            }
        }
        return out
    }

    private val files get() = unzip(CsvExporter.export(ExportSampleData.snapshot(), ZoneOffset.UTC))

    private fun lines(name: String) = files.getValue(name).trimEnd().split("\r\n")

    @Test
    fun zipHasOneCsvPerTable() {
        assertEquals(CsvExporter.FILES + "README.txt", files.keys.toList())
    }

    @Test
    fun headersAreExplicitAboutUnitsAndCurrency() {
        assertEquals(
            "id,vehicle_id,vehicle_name,stream,date_time,odometer,distance_unit,quantity,quantity_unit,price_per_unit,total," +
                "currency,is_full,missed_previous,fuel_grade,charger_type,soc_start,soc_end,station,note",
            lines("energy_logs.csv").first(),
        )
        assertTrue(lines("vehicles.csv").first().contains("distance_unit,volume_unit,currency"))
        assertTrue(lines("expenses.csv").first().contains("amount,currency"))
    }

    @Test
    fun datesAreIsoAndDecimalsUseADot() {
        val first = files.getValue("energy_logs.csv")
        assertTrue(first.contains("2024-01-05T08:30:00Z"))
        assertTrue(first.contains("2024-01-06T08:30:00Z"))
        assertTrue(first.contains(",10000.5,KM,40.25,L,1.55,62.39,EUR,true,false,95,,,,"))
        assertTrue(first.contains(",ELECTRIC,"))
        assertTrue(first.contains(",kWh,"))
    }

    @Test
    fun moneyFollowsTheVehiclesCurrencyDigits() {
        val vehicles = ExportSampleData.snapshot(
            listOf(ExportSampleData.vehicle(1, "Yen car", currency = "JPY")),
        )
        val csv = unzip(CsvExporter.export(vehicles, ZoneOffset.UTC)).getValue("service_records.csv")
        // 12000 minor units of a zero-decimal currency stay 12000; EUR would be 120.00.
        assertTrue(csv.contains(",12000,JPY,"))
        assertTrue(files.getValue("service_records.csv").contains(",120.00,EUR,"))
    }

    @Test
    fun valuesWithCommasQuotesAndNewlinesAreQuoted() {
        assertTrue(files.getValue("vehicles.csv").contains("\"Daily \"\"Hatch\"\", blue\""))
        assertTrue(files.getValue("energy_logs.csv").contains("\"Main St, Springfield\""))
        assertTrue(files.getValue("energy_logs.csv").contains("\"first\nsecond\""))
    }

    @Test
    fun spreadsheetFormulasInTextAreDefused() {
        assertTrue(files.getValue("vehicles.csv").contains(",'=Van,"))
    }

    @Test
    fun vehicleFilterLimitsEveryTable() {
        val one = unzip(CsvExporter.export(ExportSampleData.snapshot(), ZoneOffset.UTC, vehicleIds = setOf(2L)))
        assertEquals(2, one.getValue("vehicles.csv").trimEnd().split("\r\n").size) // header + vehicle 2
        assertEquals(1, one.getValue("energy_logs.csv").trimEnd().split("\r\n").size) // header only
    }

    @Test
    fun dateOnlyColumnsAreIsoDates() {
        assertTrue(files.getValue("documents.csv").contains(",2024-01-05,2025-01-04,"))
    }
}
