package com.postpole.wheelfile.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.entry.PriceMath
import com.postpole.wheelfile.importer.Csv
import com.postpole.wheelfile.importer.ImportFormat
import com.postpole.wheelfile.importer.ImportOptions
import com.postpole.wheelfile.importer.ImportService
import com.postpole.wheelfile.importer.ParsedImport
import com.postpole.wheelfile.importer.SourceFill
import com.postpole.wheelfile.importer.SourceVehicle
import com.postpole.wheelfile.importer.VehicleTarget
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Unit price is stored at 1000x minor units ([PriceMath.UNIT_PRICE_STORAGE_FACTOR]). CSV export and Fuelio import must
 * convert through [PriceMath]; one native stored row and one imported Fuelio row, in litres and US gallons.
 */
@RunWith(RobolectricTestRunner::class)
class UnitPriceScaleTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun vehicle(name: String, volume: VolumeUnit) = runBlocking {
        db.vehicleDao().insert(
            Vehicle(
                name = name, type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
                volumeUnit = volume, economyDisplay = EconomyUnit.L_PER_100KM, currency = "USD", createdAt = 1, updatedAt = 1,
            ),
        )
    }

    private fun nativeFill(vehicleId: Long, quantity: Double, stored: Long, totalMinor: Long) = runBlocking {
        db.energyLogDao().insert(
            EnergyLog(
                vehicleId = vehicleId, stream = EnergyStream.LIQUID, dateTime = 1_704_450_600_000, odometer = 1000.0,
                quantity = quantity, pricePerUnitMinor = stored, totalMinor = totalMinor, isFull = true, createdAt = 1, updatedAt = 1,
            ),
        )
    }

    private fun fuelioImport(vehicleId: Long, quantity: Double, price: Double, total: Double) = runBlocking {
        val fill = SourceFill(
            line = 2, dateTime = 1_704_537_000_000, odometer = 1500.0, quantity = quantity, pricePerUnit = price, total = total,
            isFull = true, missedPrevious = false,
        )
        val parsed = ParsedImport(ImportFormat.FUELIO, listOf(SourceVehicle(key = "v", name = "v", fills = listOf(fill))), emptyList())
        ImportService(db, { true }, { 1_000L }, { Locale.US })
            .import(parsed, ImportOptions("USD", mapOf("v" to VehicleTarget.Existing(vehicleId))))
    }

    private fun exportedFills(): List<Map<String, String>> {
        val out = ByteArrayOutputStream()
        runBlocking { CsvExporter(db).export(out) }
        var text = ""
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val body = String(z.readBytes(), Charsets.UTF_8)
                if (e.name == "fillups.csv") text = body
            }
        }
        val rows = Csv.parse(text, ',').filter { !it.isBlank }
        val header = rows.first().cells
        return rows.drop(1).map { header.zip(it.cells).toMap() }
    }

    @Test
    fun litresNativeAndFuelioRowsStoreAndExportThePerLitrePrice() {
        val id = vehicle("Litres", VolumeUnit.L)
        nativeFill(id, quantity = 40.0, stored = 100_000L * 100, totalMinor = 400_000) // 100.00 per L
        fuelioImport(id, quantity = 40.0, price = 100.0, total = 4000.0)

        val stored = runBlocking { db.energyLogDao().getAll(id) }.map { it.pricePerUnitMinor }
        assertEquals(listOf(10_000_000L, 10_000_000L), stored)

        val rows = exportedFills()
        assertEquals(listOf("100.00", "100.00"), rows.map { it.getValue("price_per_unit") })
        assertEquals(listOf("4000.00", "4000.00"), rows.map { it.getValue("total") })
    }

    @Test
    fun usGallonNativeAndFuelioRowsStoreAndExportThePerGallonPrice() {
        val id = vehicle("Gallons", VolumeUnit.US_GAL)
        nativeFill(id, quantity = 3.5, stored = 3_500L * 100, totalMinor = 1225) // 3.50 per gal
        fuelioImport(id, quantity = 3.5, price = 3.5, total = 12.25)

        val stored = runBlocking { db.energyLogDao().getAll(id) }.map { it.pricePerUnitMinor }
        assertEquals(listOf(350_000L, 350_000L), stored)

        val rows = exportedFills()
        assertEquals(listOf("3.50", "3.50"), rows.map { it.getValue("price_per_unit") })
        assertEquals(listOf("US_GAL", "US_GAL"), rows.map { it.getValue("quantity_unit") })
    }

    @Test
    fun threeDecimalPricesSurviveExport() {
        val id = vehicle("Precise", VolumeUnit.L)
        nativeFill(id, quantity = 40.0, stored = 185_900L, totalMinor = 7436) // 1.859 per L
        assertEquals("1.859", exportedFills().single().getValue("price_per_unit"))
        assertEquals(185_900L, PriceMath.unitPriceToStored(PriceMath.unitPriceFromStored(185_900L, 2), 2))
    }
}
