package com.postpole.wheelfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun vehicle(name: String, archived: Boolean = false) = Vehicle(
        name = name, type = VehicleType.CAR, powertrain = Powertrain.PETROL,
        distanceUnit = DistanceUnit.KM, volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM,
        currency = "EUR", archived = archived, createdAt = 1, updatedAt = 1,
    )

    private fun log(vehicleId: Long, odo: Double, stream: EnergyStream = EnergyStream.LIQUID) = EnergyLog(
        vehicleId = vehicleId, stream = stream, dateTime = odo.toLong(), odometer = odo, quantity = 10.0,
        isFull = true, createdAt = 1, updatedAt = 1,
    )

    @Test
    fun activeVehicleCount_ignoresArchived() = runBlocking {
        db.vehicleDao().insert(vehicle("A"))
        db.vehicleDao().insert(vehicle("B", archived = true))
        assertEquals(1, db.vehicleDao().activeCount())
        assertEquals(1, db.vehicleDao().observeActiveCount().first())
    }

    @Test
    fun energyLogs_areReturnedOldestFirstPerStream() = runBlocking {
        val id = db.vehicleDao().insert(vehicle("A"))
        db.energyLogDao().insert(log(id, 300.0))
        db.energyLogDao().insert(log(id, 100.0))
        db.energyLogDao().insert(log(id, 200.0, EnergyStream.ELECTRIC))
        val liquid = db.energyLogDao().getForStream(id, EnergyStream.LIQUID)
        assertEquals(listOf(100.0, 300.0), liquid.map { it.odometer })
    }

    @Test
    fun deletingVehicle_cascadesToLogs() = runBlocking {
        val v = vehicle("A")
        val id = db.vehicleDao().insert(v)
        db.energyLogDao().insert(log(id, 100.0))
        db.vehicleDao().delete(v.copy(id = id))
        assertEquals(emptyList<EnergyLog>(), db.energyLogDao().getForStream(id, EnergyStream.LIQUID))
    }

    @Test
    fun typeConverters_roundTripLists() = runBlocking {
        val id = db.vehicleDao().insert(vehicle("A"))
        db.serviceRecordDao().insert(
            ServiceRecord(vehicleId = id, dateTime = 1, items = listOf("Oil & filter", "Tyres"), createdAt = 1, updatedAt = 1),
        )
        db.vehicleDocumentDao().insert(
            VehicleDocument(vehicleId = id, type = DocumentType.INSURANCE, title = "Policy", alertDays = listOf(30, 7)),
        )
        assertEquals(listOf("Oil & filter", "Tyres"), db.serviceRecordDao().getForVehicle(id).single().items)
        assertEquals(listOf(30, 7), db.vehicleDocumentDao().observeForVehicle(id).first().single().alertDays)
    }

    @Test
    fun attachmentCount_countsAllRows() = runBlocking {
        repeat(3) {
            db.attachmentDao().insert(
                Attachment(ownerType = OwnerType.ENERGY, ownerId = it.toLong(), fileName = "f$it", mimeType = "image/jpeg", sizeBytes = 1, createdAt = 1),
            )
        }
        assertEquals(3, db.attachmentDao().count())
    }
}
