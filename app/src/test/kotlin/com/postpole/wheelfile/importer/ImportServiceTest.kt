package com.postpole.wheelfile.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ImportServiceTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun service(pro: Boolean = false) = ImportService(db, { pro }, { 1_000L }, { Locale.GERMANY })

    private fun parsed(path: String): ParsedImport {
        val bytes = javaClass.getResourceAsStream("/import/$path")!!.readBytes()
        return FormatDetector.parse(ImportFile(path, bytes), ZoneOffset.UTC)!!
    }

    private fun existingVehicle(name: String, archived: Boolean = false, distance: DistanceUnit = DistanceUnit.KM) = runBlocking {
        db.vehicleDao().insert(
            Vehicle(
                name = name, type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = distance,
                volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR",
                archived = archived, createdAt = 1, updatedAt = 1,
            ),
        )
    }

    @Test
    fun importsExactCountsThenSkipsEveryRowOnReimport() = runBlocking {
        val p = parsed("fuelio/vehicle_km.csv")
        val first = service().import(p, ImportOptions("EUR"))
        assertEquals(3, first.fills)
        assertEquals(1, first.services)
        assertEquals(1, first.expenses)
        assertEquals(0, first.skippedDuplicates)
        assertEquals(1, first.errors.size)
        assertEquals(1, first.createdVehicles)
        assertEquals(ImportFormat.FUELIO, first.format)

        val vehicle = db.vehicleDao().getAll().single()
        assertEquals("Test Hatch", vehicle.name)
        assertEquals("EUR", vehicle.currency)
        assertEquals(3, db.energyLogDao().getAll(vehicle.id).size)
        // Money is stored in minor units: 72.00 is 7200, 1.80 per litre is 180.
        val firstLog = db.energyLogDao().getAll(vehicle.id).first()
        assertEquals(7200L, firstLog.totalMinor)
        assertEquals(180_000L, firstLog.pricePerUnitMinor) // unit price is stored at 1000x
        assertEquals(12050L, db.serviceRecordDao().getForVehicle(vehicle.id).single().totalMinor)

        // Re-import into the vehicle just created: nothing new, everything counted as skipped.
        val again = service().import(p, ImportOptions("EUR", mapOf(p.vehicles.single().key to VehicleTarget.Existing(vehicle.id))))
        assertEquals(0, again.fills + again.services + again.expenses)
        assertEquals(5, again.skippedDuplicates)
        assertEquals(1, db.vehicleDao().getAll().size)
        assertEquals(3, db.energyLogDao().getAll(vehicle.id).size)
    }

    @Test
    fun duplicateMeansSameDateWithinAMinuteOdometerAndVolume() = runBlocking {
        val p = parsed("fuelio/vehicle_km.csv")
        val id = existingVehicle("Mine")
        val target = ImportOptions("EUR", mapOf(p.vehicles.single().key to VehicleTarget.Existing(id)))
        service().import(p, target)
        val shifted = p.copy(
            vehicles = p.vehicles.map { v ->
                v.copy(
                    fills = v.fills.mapIndexed { i, f ->
                        when (i) {
                            0 -> f.copy(dateTime = f.dateTime + 59_000) // within a minute: duplicate
                            1 -> f.copy(dateTime = f.dateTime + 120_000) // two minutes: a different entry
                            else -> f.copy(quantity = f.quantity + 1) // other volume: a different entry
                        }
                    },
                    services = emptyList(), expenses = emptyList(),
                )
            },
        )
        val result = service().import(shifted, target)
        assertEquals(1, result.skippedDuplicates)
        assertEquals(2, result.fills)
    }

    @Test
    fun vehiclesOverTheFreeCapAreImportedArchivedNeverDropped() = runBlocking {
        existingVehicle("One")
        existingVehicle("Two")
        val p = parsed("acar/vehicles.xml")
        val result = service(pro = false).import(p, ImportOptions("USD"))
        assertEquals(2, result.createdVehicles)
        assertEquals(listOf("Test Wagon", "Second Car"), result.archivedVehicles)
        assertEquals(2, db.vehicleDao().activeCount())
        val all = db.vehicleDao().getAll()
        assertEquals(4, all.size)
        assertTrue(all.filter { it.name in result.archivedVehicles }.all { it.archived })
        // The archived vehicles still carry their whole history.
        val wagon = all.first { it.name == "Test Wagon" }
        assertEquals(3, db.energyLogDao().getAll(wagon.id).size)
        assertEquals(1, db.serviceRecordDao().getForVehicle(wagon.id).size)
        assertEquals(DistanceUnit.MI, wagon.distanceUnit)
        assertEquals(VolumeUnit.US_GAL, wagon.volumeUnit)
        assertEquals(EconomyUnit.MPG_US, wagon.economyDisplay)
    }

    @Test
    fun proImportsEveryVehicleActive() = runBlocking {
        existingVehicle("One")
        existingVehicle("Two")
        val p = parsed("acar/vehicles.xml")
        val result = service(pro = true).import(p, ImportOptions("USD"))
        assertTrue(result.archivedVehicles.isEmpty())
        assertEquals(4, db.vehicleDao().activeCount())
    }

    @Test
    fun existingVehicleReceivesEntriesConvertedToItsUnits() = runBlocking {
        val id = existingVehicle("Metric car", distance = DistanceUnit.KM)
        val p = parsed("fuelio/vehicle_mi_comma.csv") // miles and UK gallons
        service().import(p, ImportOptions("EUR", mapOf(p.vehicles.single().key to VehicleTarget.Existing(id))))
        val log = db.energyLogDao().getAll(id).first()
        assertEquals(12000.5 * 1.609344, log.odometer, 1e-6)
        assertEquals(9.25 * 4.54609, log.quantity, 1e-6)
    }

    @Test
    fun skippedSourceVehiclesAreLeftOut() = runBlocking {
        val p = parsed("acar/vehicles.xml")
        val result = service(pro = true).import(p, ImportOptions("USD", skipped = setOf(p.vehicles[0].key)))
        assertEquals(1, result.createdVehicles)
        assertEquals(listOf("Second Car"), db.vehicleDao().getAll().map { it.name })
        assertFalse(result.archivedVehicles.isNotEmpty())
    }

    @Test
    fun currencyWithoutMinorUnitsKeepsWholeAmounts() = runBlocking {
        val p = parsed("fuelio/vehicle_km.csv")
        service().import(p, ImportOptions("JPY"))
        val vehicle = db.vehicleDao().getAll().single()
        assertEquals(72L, db.energyLogDao().getAll(vehicle.id).first().totalMinor)
    }
}
