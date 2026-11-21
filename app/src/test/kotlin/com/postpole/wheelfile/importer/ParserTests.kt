package com.postpole.wheelfile.importer

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.VolumeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

private fun millis(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0): Long =
    LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

private fun parse(input: ImportInput) = ImportParser.parse(input, ImportFixtures.zone)

class NumberAndDateParsingTest {
    @Test
    fun numbersInEveryLocaleShape() {
        assertEquals(1234.56, Parse.number("1,234.56")!!, 0.0)
        assertEquals(1234.56, Parse.number("1.234,56")!!, 0.0)
        assertEquals(12.5, Parse.number("12,5")!!, 0.0)
        assertEquals(3.499, Parse.number("\$3.499")!!, 0.0)
        assertEquals(134417.0, Parse.number("134,417", threeDigitsAreGrouping = true)!!, 0.0)
        assertEquals(1.234, Parse.number("1,234")!!, 0.0)
        assertEquals(1234567.0, Parse.number("1.234.567")!!, 0.0)
        assertNull(Parse.number("abc"))
        assertNull(Parse.number(null))
    }

    @Test
    fun yesNoInManyLanguages() {
        listOf("Yes", "Si", "Sí", "Tak", "Ja", "Sim", "1", "true").forEach { assertEquals(it, true, Parse.bool(it)) }
        listOf("No", "Nie", "Nein", "Não", "0", "false").forEach { assertEquals(it, false, Parse.bool(it)) }
        assertNull(Parse.bool("maybe"))
    }

    @Test
    fun dateOrderIsInferredFromTheWholeFile() {
        assertEquals(DateOrder.DAY_FIRST, DateParser.inferOrder(sequenceOf("05/03/2024", "25/03/2024")))
        assertEquals(DateOrder.MONTH_FIRST, DateParser.inferOrder(sequenceOf("03/05/2024", "03/25/2024")))
        assertEquals(DateOrder.MONTH_FIRST, DateParser.inferOrder(sequenceOf("03/05/2024"), DateOrder.MONTH_FIRST))
        val dayFirst = DateParser(ImportFixtures.zone, DateOrder.DAY_FIRST)
        val monthFirst = DateParser(ImportFixtures.zone, DateOrder.MONTH_FIRST)
        assertEquals(millis(2024, 3, 4, 0, 0), dayFirst.parse("4/3/2024 00:00"))
        assertEquals(millis(2024, 4, 3, 0, 0), monthFirst.parse("4/3/2024 00:00"))
        assertEquals(millis(2024, 1, 5, 8, 10), dayFirst.parse("2024-01-05 08:10"))
        assertEquals(millis(2024, 1, 5, 8, 10), dayFirst.parse("2024-01-05T09:10:00+01:00"))
        assertNull(dayFirst.parse("31/02/2024 10:00"))
    }

    @Test
    fun quotedFieldsKeepCommasAndLineNumbers() {
        val rows = Csv.parse("a,\"b,1\",c\n\"multi\nline\",x\nlast,row\n")
        assertEquals(listOf("a", "b,1", "c"), rows[0].cells)
        assertEquals(2, rows[1].line)
        assertEquals(4, rows[2].line)
    }
}

class FuelioParserTest {
    @Test
    fun csvCountsUnitsAndBadRows() {
        val file = parse(ImportFixtures.csv("fuelio/vehicle.csv"))
        assertEquals(ImportFormat.FUELIO_CSV, file.format)
        assertFalse(file.beta)
        val v = file.vehicles.single()
        assertEquals("Test Hatch", v.name)
        assertEquals("Example", v.make)
        assertEquals(2015, v.year)
        assertEquals(DistanceUnit.KM, v.distanceUnit)
        assertEquals(VolumeUnit.L, v.volumeUnit)
        assertEquals(3, v.energy.size)
        assertEquals(1, v.services.size)
        assertEquals(1, v.expenses.size)
        assertEquals(ExpenseCategory.PARKING, v.expenses.single().category)
        assertEquals(listOf(9, 10), file.issues.map { it.line })
        val first = v.energy.first()
        assertEquals(millis(2024, 1, 5, 8, 10), first.dateTime)
        assertEquals(10000.0, first.odometer, 0.0)
        assertEquals(40.0, first.quantity, 0.0)
        assertEquals(60.0, first.totalMajor!!, 0.0)
        assertEquals(1.5, first.pricePerUnitMajor!!, 0.0)
        assertTrue(first.isFull)
        assertFalse(v.energy[2].isFull)
        assertEquals(89.9, v.services.single().totalMajor!!, 0.0)
        assertNull(v.expenses.single().odometer)
    }

    @Test
    fun milesUsGallonsAndCommaDecimals() {
        val v = parse(ImportFixtures.csv("fuelio/vehicle_us_comma.csv")).vehicles.single()
        assertEquals(DistanceUnit.MI, v.distanceUnit)
        assertEquals(VolumeUnit.US_GAL, v.volumeUnit)
        assertEquals(12345.0, v.energy[0].odometer, 0.0)
        assertEquals(10.5, v.energy[0].quantity, 0.0)
        assertEquals(35.7, v.energy[0].totalMajor!!, 0.0)
        assertEquals(3.4, v.energy[0].pricePerUnitMajor!!, 0.0)
        assertTrue(v.energy[1].missedPrevious)
    }

    @Test
    fun backupAllZipHoldsOneVehiclePerCsv() {
        val input = ImportFixtures.zip(
            mapOf(
                "vehicle-1.csv" to "fuelio/vehicle.csv",
                "vehicle-2.csv" to "fuelio/vehicle_us_comma.csv",
                "pictures.data" to "acar/event-subtypes.xml", // not a log: must be ignored
            ),
        )
        val file = parse(input)
        assertEquals(ImportFormat.FUELIO_BACKUP_ZIP, file.format)
        assertEquals(listOf("Test Hatch", "Road Wagon"), file.vehicles.map { it.name })
        assertEquals(listOf("vehicle-1.csv", "vehicle-2.csv"), file.vehicles.map { it.key })
    }
}

class SimplyAutoParserTest {
    @Test
    fun fuelServiceAndExpenseRecords() {
        val file = parse(ImportFixtures.simplyAuto())
        assertEquals(ImportFormat.SIMPLY_AUTO, file.format)
        assertEquals(2, file.vehicles.size)
        val wagon = file.vehicles.first()
        assertEquals("Example Wagon", wagon.name)
        assertEquals("XY 12 345", wagon.plate)
        assertEquals(3, wagon.energy.size)
        assertFalse(wagon.energy[2].isFull)
        assertEquals(1, wagon.services.size)
        assertEquals("Brake pads", wagon.services.single().title)
        assertEquals(ExpenseCategory.PARKING, wagon.expenses.single().category)
        assertEquals(1, file.vehicles[1].energy.size)
        assertNull(wagon.distanceUnit)
        assertEquals(millis(2024, 3, 5), wagon.energy[0].dateTime)
        assertEquals(listOf(8, 9), file.issues.map { it.line })
        assertTrue(file.issues.all { it.file == "Fuel_Log.csv" })
    }
}

class AcarParserTest {
    @Test
    fun csvWithLocaleFormattedNumbers() {
        val file = parse(ImportFixtures.csv("acar/export.csv"))
        assertEquals(ImportFormat.ACAR_CSV, file.format)
        val v = file.vehicles.single()
        assertEquals("Family Car", v.name)
        assertEquals("Example", v.make)
        assertEquals(DistanceUnit.MI, v.distanceUnit)
        assertEquals(VolumeUnit.US_GAL, v.volumeUnit)
        assertEquals(2, v.energy.size)
        assertEquals(134417.0, v.energy[0].odometer, 0.0)
        assertEquals(10.5, v.energy[0].quantity, 0.0)
        assertEquals(36.74, v.energy[0].totalMajor!!, 0.0)
        assertEquals(millis(2024, 3, 1, 8, 0), v.energy[0].dateTime)
        assertTrue(v.energy[0].isFull)
        assertFalse(v.energy[1].isFull)
        assertEquals(1, v.services.size)
        assertEquals(55.0, v.services.single().totalMajor!!, 0.0)
        assertEquals(ExpenseCategory.PARKING, v.expenses.single().category)
        assertEquals(listOf(13), file.issues.map { it.line })
    }

    @Test
    fun csvWithDayFirstDatesAndCommaDecimals() {
        val v = parse(ImportFixtures.csv("acar/export_dmy_semicolon.csv")).vehicles.single()
        assertEquals(DistanceUnit.KM, v.distanceUnit)
        assertEquals(VolumeUnit.L, v.volumeUnit)
        assertEquals(88250.0, v.energy[0].odometer, 0.0)
        assertEquals(41.25, v.energy[0].quantity, 0.0)
        assertEquals(millis(2024, 3, 5, 7, 30), v.energy[0].dateTime)
        assertEquals(millis(2024, 3, 25, 18, 10), v.energy[1].dateTime)
        assertTrue(v.energy[0].isFull)
        assertFalse(v.energy[1].isFull)
    }

    @Test
    fun abpBackupXml() {
        val file = parse(ImportFixtures.acarBackup())
        assertEquals(ImportFormat.ACAR_BACKUP, file.format)
        val v = file.vehicles.single()
        assertEquals("Blue Van", v.name)
        assertEquals("VAN-1", v.plate)
        assertEquals(DistanceUnit.KM, v.distanceUnit)
        assertEquals(VolumeUnit.L, v.volumeUnit)
        assertEquals(2, v.energy.size)
        assertTrue(v.energy[1].missedPrevious)
        assertFalse(v.energy[1].isFull)
        assertEquals(millis(2024, 4, 15, 8, 0), v.energy[1].dateTime)
        assertEquals("Brake service", v.services.single().title)
        assertEquals(ExpenseCategory.WASHING, v.expenses.single().category)
        assertEquals(1, file.issues.size)
        assertEquals(3, file.issues.single().line)
    }

    @Test
    fun xmlWithDoctypeIsRejectedNotExpanded() {
        val evil = "<?xml version=\"1.0\"?><!DOCTYPE v [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><vehicles><vehicle><name>&x;</name></vehicle></vehicles>"
        val input = ImportFixtures.zip(mapOf("vehicles.xml" to "acar/vehicles.xml"))
        val tampered = ImportInput(input.files + ("vehicles.xml" to evil.toByteArray()))
        try {
            parse(tampered)
            throw AssertionError("expected ImportException")
        } catch (e: ImportException) {
            assertNotNull(e.message)
        }
    }
}

class DrivvoParserTest {
    @Test
    fun polishNoHeaderIndexBased() {
        val file = parse(ImportFixtures.csv("drivvo/pl_no_header.csv"))
        assertEquals(ImportFormat.DRIVVO_CSV, file.format)
        assertTrue("Drivvo is labelled beta", file.beta)
        val v = file.vehicles.single()
        assertEquals("Auto testowe", v.name)
        assertEquals("PL 1234", v.plate)
        assertEquals(2015, v.year)
        assertEquals(3, v.energy.size)
        assertEquals(10000.0, v.energy[0].odometer, 0.0)
        assertEquals(40.0, v.energy[0].quantity, 0.0)
        assertEquals(235.6, v.energy[0].totalMajor!!, 0.0)
        assertEquals(5.89, v.energy[0].pricePerUnitMajor!!, 0.0)
        assertTrue("Tak = yes", v.energy[0].isFull)
        assertFalse("Nie = no", v.energy[1].isFull)
        assertEquals("pierwsze", v.energy[0].note)
        assertEquals(millis(2024, 3, 25), v.energy[1].dateTime) // D/M/YYYY inferred from 25/03
        assertEquals(1, v.services.size)
        assertEquals(350.0, v.services.single().totalMajor!!, 0.0)
        assertEquals(1, v.expenses.size)
        assertEquals(ExpenseCategory.PARKING, v.expenses.single().category)
        assertEquals(listOf(7), file.issues.map { it.line })
    }

    @Test
    fun germanWithHeaderAndSemicolons() {
        val file = parse(ImportFixtures.csv("drivvo/de_header_semicolon.csv"))
        val v = file.vehicles.single()
        assertEquals("Testwagen", v.name)
        assertEquals(2, v.energy.size)
        assertTrue("Ja = yes", v.energy[0].isFull)
        assertFalse("Nein = no", v.energy[1].isFull)
        assertEquals(1.799, v.energy[0].pricePerUnitMajor!!, 0.0)
        assertEquals(30.0, v.energy[0].quantity, 0.0)
        assertEquals(millis(2024, 4, 3), v.energy[0].dateTime)
        assertEquals("erste", v.energy[0].note)
        assertEquals(120.5, v.services.single().totalMajor!!, 0.0)
        assertTrue(file.issues.isEmpty())
    }

    @Test
    fun spanishSingleHashHeadings() {
        val v = parse(ImportFixtures.csv("drivvo/es_single_hash.csv")).vehicles.single()
        assertEquals("Coche de prueba", v.name)
        assertTrue("Si = yes", v.energy[0].isFull)
        assertFalse("No = no", v.energy[1].isFull)
        assertEquals(1.659, v.energy[0].pricePerUnitMajor!!, 0.0)
        assertEquals(1, v.services.size)
    }
}

class FormatDetectorTest {
    @Test
    fun detectsEveryFormatFromContent() {
        assertEquals(ImportFormat.FUELIO_CSV, FormatDetector.detect(ImportFixtures.csv("fuelio/vehicle.csv")))
        assertEquals(ImportFormat.SIMPLY_AUTO, FormatDetector.detect(ImportFixtures.simplyAuto()))
        assertEquals(ImportFormat.ACAR_BACKUP, FormatDetector.detect(ImportFixtures.acarBackup()))
        assertEquals(ImportFormat.ACAR_CSV, FormatDetector.detect(ImportFixtures.csv("acar/export.csv")))
        assertEquals(ImportFormat.DRIVVO_CSV, FormatDetector.detect(ImportFixtures.csv("drivvo/pl_no_header.csv")))
    }

    @Test
    fun unrelatedFilesAreNotRecognised() {
        assertNull(FormatDetector.detect(ImportInput.fromBytes("notes.csv", "a,b,c\n1,2,3\n".toByteArray())))
        assertNull(FormatDetector.detect(ImportInput.fromBytes("empty.csv", ByteArray(0))))
        try {
            ImportParser.parse(ImportInput.fromBytes("x.csv", "hello".toByteArray()))
            throw AssertionError("expected ImportException")
        } catch (e: ImportException) {
            assertNotNull(e.message)
        }
    }
}
