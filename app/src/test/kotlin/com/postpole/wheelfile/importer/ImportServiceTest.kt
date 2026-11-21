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

@RunWith(RobolectricTestRunner::class)
class ImportServiceTest {
    private lateinit var db: AppDatabase
    private var pro = false
    private lateinit var service: ImportService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        service = ImportService(db, { pro }, { 1_700_000_000_000L })
    }

    @After
    fun tearDown() = db.close()

    private fun parsed(input: ImportInput) = ImportParser.parse(input, ImportFixtures.zone)

    private fun newTarget(v: ParsedVehicle) =
        VehicleTarget.New(v.name, v.distanceUnit ?: DistanceUnit.KM, v.volumeUnit ?: VolumeUnit.L)

    private fun existing(distance: DistanceUnit = DistanceUnit.KM, volume: VolumeUnit = VolumeUnit.L, archived: Boolean = false): Vehicle {
        val v = Vehicle(
            name = "Mine", type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = distance, volumeUnit = volume,
            economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", archived = archived, createdAt = 0, updatedAt = 0,
        )
        return v.copy(id = runBlocking { db.vehicleDao().insert(v) })
    }

    @Test
    fun importsExactCountsAndMinorUnits() = runBlocking {
        val file = parsed(ImportFixtures.csv("fuelio/vehicle.csv"))
        val v = file.vehicles.single()
        val result = service.import(file, ImportPlan(mapOf(v.key to newTarget(v)), "EUR"))

        assertEquals(3, result.fillUps)
        assertEquals(1, result.services)
        assertEquals(1, result.expenses)
        assertEquals(0, result.skippedDuplicates)
        assertEquals(2, result.unreadable.size)
        assertEquals(1, result.vehiclesCreated)
        assertEquals(0, result.archivedVehicles)

        val vehicle = db.vehicleDao().getAll().single()
        assertEquals("EUR", vehicle.currency)
        assertEquals("Test Hatch", vehicle.name)
        val logs = db.energyLogDao().getAll(vehicle.id)
        assertEquals(3, logs.size)
        assertEquals(6000L, logs[0].totalMinor)
        assertEquals(150L, logs[0].pricePerUnitMinor)
        assertEquals("fuelio", logs[0].importSource)
        assertEquals(8990L, db.serviceRecordDao().getForVehicle(vehicle.id).single().totalMinor)
        assertEquals(1200L, db.expenseDao().getForVehicle(vehicle.id).single().amountMinor)
    }

    @Test
    fun reImportOfTheSameFileImportsNothingAndReportsSkipped() = runBlocking {
        val file = parsed(ImportFixtures.csv("fuelio/vehicle.csv"))
        val v = file.vehicles.single()
        service.import(file, ImportPlan(mapOf(v.key to newTarget(v)), "EUR"))
        val vehicleId = db.vehicleDao().getAll().single().id

        val again = service.import(file, ImportPlan(mapOf(v.key to VehicleTarget.Existing(vehicleId)), "EUR"))

        assertEquals(0, again.imported)
        assertEquals(5, again.skippedDuplicates)
        assertEquals(2, again.unreadable.size)
        assertEquals(3, db.energyLogDao().getAll(vehicleId).size)
        assertEquals(1, db.serviceRecordDao().getForVehicle(vehicleId).size)
        assertEquals(1, db.expenseDao().getForVehicle(vehicleId).size)
    }

    @Test
    fun duplicatesInsideOneFileAreSkippedToo() = runBlocking {
        val file = parsed(ImportFixtures.csv("fuelio/vehicle.csv"))
        val v = file.vehicles.single()
        val doubled = file.copy(vehicles = listOf(v.copy(energy = v.energy + v.energy.first().copy(dateTime = v.energy.first().dateTime + 30_000))))
        val result = service.import(doubled, ImportPlan(mapOf(v.key to newTarget(v)), "USD"))
        assertEquals(3, result.fillUps)
        assertEquals(1, result.skippedDuplicates)
    }

    @Test
    fun overCapVehiclesAreImportedArchivedWithAllTheirData() = runBlocking {
        existing()
        existing()
        val file = parsed(ImportFixtures.simplyAuto())
        val plan = ImportPlan(file.vehicles.associate { it.key to newTarget(it) }, "USD")

        val result = service.import(file, plan)

        assertEquals(2, result.vehiclesCreated)
        assertEquals(2, result.archivedVehicles)
        assertEquals(4, result.fillUps)
        assertEquals(1, result.services)
        assertEquals(1, result.expenses)
        val all = db.vehicleDao().getAll()
        assertEquals(4, all.size)
        assertEquals(2, all.count { it.archived })
        assertEquals(2, db.vehicleDao().activeCount())
    }

    @Test
    fun withProTheNewVehiclesStayActiveAndOnlyTheOverflowIsArchivedOtherwise() = runBlocking {
        existing()
        val file = parsed(ImportFixtures.simplyAuto())
        val plan = ImportPlan(file.vehicles.associate { it.key to newTarget(it) }, "USD")
        val result = service.import(file, plan)
        // One free slot left: the first vehicle is active, the second archived.
        assertEquals(1, result.archivedVehicles)
        assertEquals(2, db.vehicleDao().activeCount())

        pro = true
        val again = parsed(ImportFixtures.csv("fuelio/vehicle_us_comma.csv"))
        val r2 = service.import(again, ImportPlan(mapOf(again.vehicles.single().key to newTarget(again.vehicles.single())), "USD"))
        assertEquals(0, r2.archivedVehicles)
        assertEquals(3, db.vehicleDao().activeCount())
    }

    @Test
    fun rowsAreConvertedToTheExistingVehiclesUnits() = runBlocking {
        val mine = existing(DistanceUnit.MI, VolumeUnit.US_GAL)
        val file = parsed(ImportFixtures.csv("fuelio/vehicle.csv")) // km and litres
        val v = file.vehicles.single()
        service.import(file, ImportPlan(mapOf(v.key to VehicleTarget.Existing(mine.id)), "EUR"))
        val first = db.energyLogDao().getAll(mine.id).first()
        assertEquals(6213.712, first.odometer, 0.001) // 10 000 km
        assertEquals(10.567, first.quantity, 0.001) // 40 L
    }

    @Test
    fun skippedVehiclesAreNotImported() = runBlocking {
        val file = parsed(ImportFixtures.simplyAuto())
        val plan = ImportPlan(mapOf(file.vehicles[0].key to newTarget(file.vehicles[0])), "USD")
        val result = service.import(file, plan)
        assertEquals(1, result.vehiclesCreated)
        assertEquals(3, result.fillUps)
        assertTrue(db.vehicleDao().getAll().none { it.name.contains("Van") })
        assertFalse(result.beta)
    }
}
