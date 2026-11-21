package com.postpole.wheelfile.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.data.AppDatabase
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
import com.postpole.wheelfile.importer.Csv
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class CsvExportTest {
    private lateinit var db: AppDatabase
    private var previousLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        // A comma-decimal locale proves the export never follows the device's number format.
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Locale.setDefault(previousLocale)
    }

    private fun vehicle(name: String, currency: String = "EUR") = Vehicle(
        name = name, type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = currency, createdAt = 1, updatedAt = 1,
    )

    private fun export(vehicleId: Long? = null): Map<String, String> {
        val out = ByteArrayOutputStream()
        runBlocking { CsvExporter(db).export(out, vehicleId) }
        val files = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                files[e.name] = String(z.readBytes(), Charsets.UTF_8)
            }
        }
        return files
    }

    private fun seed(): Pair<Long, Long> = runBlocking {
        val a = db.vehicleDao().insert(vehicle("Hatch, the \"first\""))
        val b = db.vehicleDao().insert(vehicle("Yen car", "JPY"))
        db.energyLogDao().insert(
            EnergyLog(
                vehicleId = a, stream = EnergyStream.LIQUID, dateTime = 1_704_450_600_000, // 2024-01-05T10:30:00Z
                odometer = 12345.5, quantity = 40.25, pricePerUnitMinor = 180, totalMinor = 7245, isFull = true,
                station = "=SUM(A1)", note = "line one\nline two", createdAt = 1, updatedAt = 1,
            ),
        )
        db.serviceRecordDao().insert(
            ServiceRecord(vehicleId = a, dateTime = 1_704_450_600_000, items = listOf("Oil", "Filter"), totalMinor = 12050, createdAt = 1, updatedAt = 1),
        )
        db.expenseDao().insert(
            Expense(vehicleId = b, dateTime = 1_704_450_600_000, category = ExpenseCategory.PARKING, amountMinor = 500, createdAt = 1, updatedAt = 1),
        )
        a to b
    }

    @Test
    fun zipHasOneCsvPerTableWithExplicitUnitColumns() {
        seed()
        val files = export()
        assertEquals(listOf("vehicles.csv", "fillups.csv", "services.csv", "expenses.csv", "documents.csv"), files.keys.toList())
        assertEquals(
            "vehicle_id,vehicle,date,odometer,distance_unit,stream,quantity,quantity_unit,price_per_unit,total,currency," +
                "full_tank,missed_previous,fuel_grade,charger_type,soc_start_percent,soc_end_percent,station,note",
            files.getValue("fillups.csv").lineSequence().first().trimEnd('\r'),
        )
        assertTrue(files.getValue("vehicles.csv").startsWith("vehicle_id,name,type,powertrain,make,model,year,plate,vin,distance_unit,volume_unit,currency"))
        assertTrue(files.getValue("services.csv").startsWith("vehicle_id,vehicle,date,odometer,distance_unit,items,total,currency"))
        assertTrue(files.getValue("expenses.csv").startsWith("vehicle_id,vehicle,date,odometer,distance_unit,category,amount,currency"))
    }

    @Test
    fun datesAreIsoAndDecimalsUseADotWhateverTheDeviceLocale() {
        seed()
        val rows = Csv.parse(export().getValue("fillups.csv"), ',')
        assertEquals(2, rows.size) // header + one fill-up
        val h = rows[0].cells
        val r = rows[1].cells
        fun col(name: String) = r[h.indexOf(name)]
        assertEquals("2024-01-05T10:30:00Z", col("date"))
        assertEquals("12345.5", col("odometer"))
        assertEquals("40.25", col("quantity"))
        assertEquals("1.80", col("price_per_unit"))
        assertEquals("72.45", col("total"))
        assertEquals("EUR", col("currency"))
        assertEquals("L", col("quantity_unit"))
        assertEquals("KM", col("distance_unit"))
        assertEquals("true", col("full_tank"))
        assertFalse(export().getValue("fillups.csv").contains("12345,5"))
    }

    @Test
    fun moneyFollowsTheVehicleCurrencyAndTextIsEscaped() {
        seed()
        val files = export()
        // JPY has no minor unit: 500 stays 500.
        assertTrue(files.getValue("expenses.csv").lineSequence().drop(1).first().contains(",500,JPY,"))
        val services = Csv.parse(files.getValue("services.csv"), ',')
        assertEquals("Oil; Filter", services[1].cells[services[0].cells.indexOf("items")])
        assertEquals("120.50", services[1].cells[services[0].cells.indexOf("total")])
        // Quotes, commas and newlines survive a CSV round trip; a leading = is defused for spreadsheets.
        val vehicles = Csv.parse(files.getValue("vehicles.csv"), ',')
        assertEquals("Hatch, the \"first\"", vehicles[1].cells[1])
        val fill = Csv.parse(files.getValue("fillups.csv"), ',')
        assertEquals("'=SUM(A1)", fill[1].cells[fill[0].cells.indexOf("station")])
        assertEquals("line one\nline two", fill[1].cells[fill[0].cells.indexOf("note")])
    }

    @Test
    fun exportCanBeLimitedToOneVehicle() {
        val (a, b) = seed()
        val onlyB = export(b)
        assertEquals(2, Csv.parse(onlyB.getValue("vehicles.csv"), ',').filter { !it.isBlank }.size)
        assertEquals(1, Csv.parse(onlyB.getValue("fillups.csv"), ',').filter { !it.isBlank }.size) // header only
        assertEquals(2, Csv.parse(onlyB.getValue("expenses.csv"), ',').filter { !it.isBlank }.size)
        val onlyA = export(a)
        assertEquals(2, Csv.parse(onlyA.getValue("fillups.csv"), ',').filter { !it.isBlank }.size)
        assertEquals(1, Csv.parse(onlyA.getValue("expenses.csv"), ',').filter { !it.isBlank }.size)
    }

    @Test
    fun emptyDatabaseStillProducesHeaders() {
        val files = export()
        assertEquals(5, files.size)
        files.values.forEach { assertEquals(1, it.lines().count { l -> l.isNotBlank() }) }
    }
}
