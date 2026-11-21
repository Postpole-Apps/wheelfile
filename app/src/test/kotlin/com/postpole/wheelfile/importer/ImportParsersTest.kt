package com.postpole.wheelfile.importer

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.importer.drivvo.DrivvoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One parser test per format, with locale variants (comma decimals, D/M/YYYY, Tak/Ja/Si). Fixtures are synthetic. */
class ImportParsersTest {
    private val utc = ZoneOffset.UTC

    private fun resource(path: String): ImportFile {
        val bytes = javaClass.getResourceAsStream("/import/$path")!!.readBytes()
        return ImportFile(path, bytes)
    }

    private fun zip(vararg files: ImportFile): ImportFile {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            files.forEach {
                z.putNextEntry(ZipEntry(it.name))
                z.write(it.bytes)
                z.closeEntry()
            }
        }
        return ImportFile("picked.zip", out.toByteArray())
    }

    private fun millis(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun parse(vararg files: ImportFile): ParsedImport = FormatDetector.parse(files.toList(), utc)!!

    // ---- Fuelio ----------------------------------------------------------------------------------------------

    @Test
    fun fuelioCsv() {
        val p = parse(resource("fuelio/vehicle_km.csv"))
        assertEquals(ImportFormat.FUELIO, p.format)
        assertFalse(p.format.beta)
        val v = p.vehicles.single()
        assertEquals("Test Hatch", v.name)
        assertEquals("Acme", v.make)
        assertEquals(2019, v.year)
        assertEquals("AB-123-CD", v.plate)
        assertEquals(DistanceUnit.KM, v.distanceUnit)
        assertEquals(VolumeUnit.L, v.volumeUnit)
        assertEquals(3, v.fills.size)
        assertEquals(1, v.services.size)
        assertEquals("Oil change", v.services.single().title)
        assertEquals(120.5, v.services.single().total!!, 0.0)
        // The insurance-like cost is an expense; the template row is not an entry at all.
        assertEquals(1, v.expenses.size)
        val first = v.fills[0]
        assertEquals(millis(2024, 1, 5, 10, 30), first.dateTime)
        assertEquals(10000.0, first.odometer, 0.0)
        assertEquals(40.0, first.quantity, 0.0)
        assertEquals(72.0, first.total!!, 0.0)
        assertEquals(1.8, first.pricePerUnit!!, 0.0)
        assertTrue(first.isFull)
        assertFalse(v.fills[1].isFull)
        // Line numbers are physical lines of the file: the bad date is on line 9.
        assertEquals(listOf(9), p.errors.map { it.line })
        assertEquals("Unreadable date", p.errors.single().reason)
    }

    @Test
    fun fuelioMilesWithCommaDecimalsAndDayFirstDates() {
        val p = parse(resource("fuelio/vehicle_mi_comma.csv"))
        val v = p.vehicles.single()
        assertEquals(DistanceUnit.MI, v.distanceUnit)
        assertEquals(VolumeUnit.UK_GAL, v.volumeUnit)
        assertEquals(2, v.fills.size)
        assertEquals(12000.5, v.fills[0].odometer, 0.0)
        assertEquals(9.25, v.fills[0].quantity, 0.0)
        assertEquals(60.75, v.fills[0].total!!, 0.0)
        assertEquals(millis(2024, 1, 5, 10, 30), v.fills[0].dateTime) // 05/01/2024 is D/M/YYYY
        assertEquals(millis(2024, 1, 25, 9, 0), v.fills[1].dateTime)
        assertTrue(v.fills[1].missedPrevious)
        assertEquals(1234.5, v.expenses.single().amount, 0.0)
        assertEquals(ExpenseCategory.PARKING, v.expenses.single().category)
        assertTrue(p.errors.isEmpty())
    }

    @Test
    fun fuelioBackupAllZipHoldsOneVehiclePerCsv() {
        val picked = zip(
            ImportFile("vehicle-1-sync.csv", resource("fuelio/vehicle_km.csv").bytes),
            ImportFile("vehicle-2-sync.csv", resource("fuelio/vehicle_mi_comma.csv").bytes),
            ImportFile("pictures.data", byteArrayOf(1, 2, 3)),
        )
        val p = FormatDetector.parse(picked, utc)!!
        assertEquals(ImportFormat.FUELIO, p.format)
        assertEquals(listOf("Test Hatch", "Imperial Van"), p.vehicles.map { it.name })
        assertEquals(5, p.fillCount)
        assertEquals(1, p.errors.size)
        assertNotEquals(p.vehicles[0].key, p.vehicles[1].key)
    }

    private fun assertNotEquals(a: Any, b: Any) = assertFalse(a == b)

    // ---- Drivvo (beta) ---------------------------------------------------------------------------------------

    @Test
    fun drivvoEnglishIsBetaAndReadsByIndexAndHeader() {
        val p = parse(resource("drivvo/drivvo_en.csv"))
        assertEquals(ImportFormat.DRIVVO, p.format)
        assertTrue(p.format.beta)
        val v = p.vehicles.single()
        assertEquals("Family Wagon", v.name)
        assertEquals("Estate 2.0", v.model)
        assertEquals("XY-99-ZZ", v.plate)
        assertEquals(2018, v.year)
        assertEquals(3, v.fills.size)
        assertEquals(listOf(true, false, true), v.fills.map { it.isFull })
        assertEquals(millis(2024, 1, 15), v.fills[0].dateTime)
        assertEquals(40.0, v.fills[0].quantity, 0.0)
        assertEquals(60.0, v.fills[0].total!!, 0.0)
        assertEquals(1.5, v.fills[0].pricePerUnit!!, 0.0)
        assertEquals("first fill", v.fills[0].note)
        assertEquals("Gasoline", v.fills[0].fuelGrade)
        assertEquals(1, v.services.size)
        assertEquals("Brake pads", v.services.single().title)
        assertEquals(150.0, v.services.single().total!!, 0.0)
        assertEquals(1, v.expenses.size)
        assertEquals(ExpenseCategory.PARKING, v.expenses.single().category)
        assertEquals(listOf(10), p.errors.map { it.line })
        // Drivvo does not state units: they stay unset and the importer applies device defaults.
        assertNull(v.distanceUnit)
    }

    @Test
    fun drivvoPolishUsesIndexesWithTakNieAndCommaDecimals() {
        val p = parse(resource("drivvo/drivvo_pl.csv"))
        assertEquals(ImportFormat.DRIVVO, p.format)
        val v = p.vehicles.single()
        assertEquals("Kombi", v.name)
        assertEquals(listOf(true, false, true), v.fills.map { it.isFull }) // Tak / Nie / Tak
        assertEquals(15000.0, v.fills[0].odometer, 0.0)
        assertEquals(6.5, v.fills[0].pricePerUnit!!, 0.0)
        assertEquals(260.0, v.fills[0].total!!, 0.0)
        assertEquals(40.0, v.fills[0].quantity, 0.0)
        assertEquals(millis(2024, 1, 15), v.fills[0].dateTime)
        assertEquals(650.0, v.services.single().total!!, 0.0)
        assertTrue(p.errors.isEmpty())
    }

    @Test
    fun drivvoGermanUsesJaNeinAndDottedDates() {
        val p = parse(resource("drivvo/drivvo_de.csv"))
        assertEquals(ImportFormat.DRIVVO, p.format)
        val v = p.vehicles.single()
        assertEquals(listOf(true, false, true), v.fills.map { it.isFull }) // Ja / Nein / Ja
        assertEquals(1.799, v.fills[0].pricePerUnit!!, 0.0)
        assertEquals(71.96, v.fills[0].total!!, 0.0)
        assertEquals(millis(2024, 2, 20), v.fills[1].dateTime)
        assertEquals(1250.0, v.services.single().total!!, 0.0) // 1.250,00
        assertEquals(9.5, v.expenses.single().amount, 0.0)
        assertTrue(p.errors.isEmpty())
    }

    @Test
    fun drivvoSpanishUsesSiNoAndLocalisedSectionTitles() {
        val p = parse(resource("drivvo/drivvo_es.csv"))
        assertEquals(ImportFormat.DRIVVO, p.format)
        val v = p.vehicles.single()
        assertEquals("Furgoneta", v.name)
        assertEquals(listOf(true, false, true), v.fills.map { it.isFull }) // Si / No / Si
        assertEquals(1.5, v.fills[0].pricePerUnit!!, 0.0)
        assertEquals(1, v.services.size)
        assertEquals(150.0, v.services.single().total!!, 0.0)
        assertTrue(p.errors.isEmpty())
    }

    @Test
    fun localisedYesNoWords() {
        listOf("Yes", "yes", "Si", "Sí", "Sim", "Ja", "Tak", "Oui", "1", " TRUE ").forEach { assertTrue(it, DrivvoParser.isYes(it)) }
        listOf("No", "Nie", "Nein", "Não", "Non", "0", "", "false", null).forEach { assertFalse(it.toString(), DrivvoParser.isYes(it)) }
    }

    // ---- aCar ------------------------------------------------------------------------------------------------

    @Test
    fun acarAbpBackup() {
        val abp = zip(
            ImportFile("metadata.inf", "acar.backup.type=Full-Backup\n".toByteArray()),
            resource("acar/vehicles.xml").let { ImportFile("vehicles.xml", it.bytes) },
        )
        val p = FormatDetector.parse(abp, utc)!!
        assertEquals(ImportFormat.ACAR, p.format)
        assertEquals(listOf("Test Wagon", "Second Car"), p.vehicles.map { it.name })
        val a = p.vehicles[0]
        assertEquals(DistanceUnit.MI, a.distanceUnit)
        assertEquals(VolumeUnit.US_GAL, a.volumeUnit)
        assertEquals("ACAR-1", a.plate)
        assertEquals(3, a.fills.size)
        assertEquals(listOf(true, false, true), a.fills.map { it.isFull })
        assertEquals(listOf(false, false, true), a.fills.map { it.missedPrevious })
        assertEquals(millis(2024, 1, 5, 10, 30), a.fills[0].dateTime)
        assertEquals(38.85, a.fills[0].total!!, 0.0)
        assertEquals(1, a.services.size)
        assertEquals("Oil change", a.services.single().title)
        assertEquals(1, a.expenses.size)
        assertEquals(ExpenseCategory.PARKING, a.expenses.single().category)
        assertEquals(1, p.vehicles[1].fills.size)
        assertEquals(DistanceUnit.KM, p.vehicles[1].distanceUnit)
        assertEquals(VolumeUnit.L, p.vehicles[1].volumeUnit)
        // The unreadable record is the fourth <fillup-record>, which opens on line 39 of the file.
        assertEquals(listOf(39), p.errors.map { it.line })
    }

    @Test
    fun acarCsvWithUsDatesAndLocaleFormattedNumbers() {
        val p = parse(resource("acar/export_us.csv"))
        assertEquals(ImportFormat.ACAR, p.format)
        val v = p.vehicles.single()
        assertEquals("CSV Car", v.name)
        assertEquals("CSV-1", v.plate)
        assertEquals(DistanceUnit.MI, v.distanceUnit)
        assertEquals(VolumeUnit.US_GAL, v.volumeUnit)
        assertEquals(3, v.fills.size)
        assertEquals(134417.0, v.fills[0].odometer, 0.0) // "134,417" is a thousands separator
        assertEquals(millis(2014, 1, 25, 10, 30), v.fills[0].dateTime) // 01/25/2014 is M/D/YYYY
        assertEquals(millis(2014, 2, 3, 18, 0), v.fills[1].dateTime)
        assertEquals(45.0, v.fills[0].total!!, 0.0) // "$45.00"
        assertEquals(listOf(true, false, true), v.fills.map { it.isFull })
        assertTrue(v.fills[2].missedPrevious)
        assertEquals(1, v.services.size)
        assertEquals(79.99, v.services.single().total!!, 0.0)
        assertEquals(1, v.expenses.size)
        assertEquals(listOf(14), p.errors.map { it.line })
    }

    // ---- Simply Auto -----------------------------------------------------------------------------------------

    @Test
    fun simplyAutoCsvSet() {
        val p = parse(resource("simplyauto/Vehicles.csv"), resource("simplyauto/Fuel_Log.csv"), resource("simplyauto/Services.csv"))
        assertEquals(ImportFormat.SIMPLY_AUTO, p.format)
        assertEquals(listOf("Acme Roadster", "Foo Bar"), p.vehicles.map { it.name })
        val a = p.vehicles[0]
        assertEquals("SA-1", a.plate)
        assertEquals(3, a.fills.size)
        assertEquals(listOf(true, false, true), a.fills.map { it.isFull })
        assertTrue(a.fills[2].missedPrevious)
        assertEquals(millis(2024, 1, 5), a.fills[0].dateTime)
        assertEquals(2, a.services.size) // one service row in Fuel_Log (type 1), one in Services.csv
        assertEquals(1, a.expenses.size)
        assertEquals(ExpenseCategory.WASHING, a.expenses.single().category)
        assertEquals(1, p.vehicles[1].fills.size)
        // 31 February is not a date: line 8 of Fuel_Log.csv.
        assertEquals(listOf(8), p.errors.map { it.line })
        assertNull(a.distanceUnit)
    }

    @Test
    fun simplyAutoWithSemicolonsAndCommaDecimals() {
        val p = parse(resource("simplyauto_eu/Vehicles.csv"), resource("simplyauto_eu/Fuel_Log.csv"))
        val v = p.vehicles.single()
        assertEquals("Marke Kombi", v.name)
        assertEquals(2, v.fills.size)
        assertEquals(40.5, v.fills[0].quantity, 0.0)
        assertEquals(72.9, v.fills[0].total!!, 0.0)
        assertTrue(p.errors.isEmpty())
    }

    // ---- Detector --------------------------------------------------------------------------------------------

    @Test
    fun detectorRejectsUnknownContentAndDetectsByContentNotName() {
        assertNull(FormatDetector.parse(ImportFile("notes.csv", "a,b,c\n1,2,3\n".toByteArray()), utc))
        assertNull(FormatDetector.parse(ImportFile("empty.zip", ByteArray(0)), utc))
        // A Fuelio file with a misleading name is still recognised from its section headers.
        val renamed = ImportFile("export.csv", resource("fuelio/vehicle_km.csv").bytes)
        assertEquals(ImportFormat.FUELIO, FormatDetector.detect(listOf(renamed)))
        assertNotNull(FormatDetector.parse(renamed, utc))
    }

    @Test
    fun byteOrderMarkIsIgnored() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val file = ImportFile("with-bom.csv", bom + resource("fuelio/vehicle_km.csv").bytes)
        val p = FormatDetector.parse(file, utc)!!
        assertEquals(ImportFormat.FUELIO, p.format)
        assertEquals(3, p.fillCount)
    }

    @Test
    fun damagedXmlIsReportedNotThrown() {
        val p = FormatDetector.parse(
            zip(ImportFile("vehicles.xml", "<vehicles><vehicle><name>x".toByteArray())),
            utc,
        )
        assertEquals(ImportFormat.ACAR, p!!.format)
        assertTrue(p.vehicles.isEmpty())
        assertEquals(1, p.errors.size)
    }

    // ---- Shared helpers --------------------------------------------------------------------------------------

    @Test
    fun csvParserKeepsQuotedNewlinesAndPhysicalLineNumbers() {
        val rows = Csv.parse("a,b\n\"x\ny\",2\r\nlast,\"q\"\"uote\"\n", ',')
        assertEquals(listOf(1, 2, 4), rows.map { it.line })
        assertEquals("x\ny", rows[1].cells[0])
        assertEquals("q\"uote", rows[2].cells[1])
        assertEquals(';', Csv.sniffDelimiter("a;b;c\n1,5;2,5;3\n"))
        assertEquals(',', Csv.sniffDelimiter("\"a;b\",c\n"))
    }

    @Test
    fun numberStyles() {
        val comma = DecimalStyle.sniff(sequenceOf("12,5", "1.234,56"))
        assertEquals(DecimalStyle.COMMA, comma)
        assertEquals(1234.56, NumberParser.parse("1.234,56", comma)!!, 0.0)
        assertEquals(DecimalStyle.DOT, DecimalStyle.sniff(sequenceOf("134,417", "45.5")))
        assertEquals(134417.0, NumberParser.parse("134,417", DecimalStyle.DOT)!!, 0.0)
        assertEquals(-3.5, NumberParser.parse("-3.5", DecimalStyle.DOT)!!, 0.0)
        assertEquals(1299.0, NumberParser.parse("\$1,299.00", DecimalStyle.DOT)!!, 0.0)
        assertNull(NumberParser.parse("abc", DecimalStyle.DOT))
    }

    @Test
    fun slashDatesFollowTheSniffedOrder() {
        val dmy = DateParser(SlashOrder.DMY, utc)
        val mdy = DateParser(SlashOrder.MDY, utc)
        assertEquals(millis(2024, 3, 4), dmy.parse("04/03/2024"))
        assertEquals(millis(2024, 4, 3), mdy.parse("04/03/2024"))
        assertEquals(millis(2024, 3, 25), dmy.parse("3/25/2024")) // unambiguous wins
        assertEquals(millis(2024, 1, 5, 22, 30), dmy.parse("2024-01-05", "10:30 PM"))
        assertEquals(SlashOrder.DMY, DateParser.sniffOrder(sequenceOf("13/01/2024", "02/02/2024"), SlashOrder.MDY))
        assertEquals(SlashOrder.MDY, DateParser.sniffOrder(sequenceOf("01/13/2024"), SlashOrder.DMY))
        assertNull(dmy.parse("31/02/2024"))
    }
}
