package com.postpole.wheelfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.attachments.SourceOpener
import com.postpole.wheelfile.data.attachments.StageResult
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File

/**
 * On-device check of issue #12 items 2 and 3 with the real SQLite engine: the vehicle delete cascades to attachment
 * rows and files, and a save that fails after the attachment rows were written leaves nothing behind.
 * (The same cases run under Robolectric in `AttachmentCascadeTest`.)
 */
@RunWith(AndroidJUnit4::class)
class AttachmentIntegrityTest {
    private lateinit var db: AppDatabase
    private lateinit var dir: File
    private lateinit var store: AttachmentStore
    private lateinit var deleter: RecordDeleter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dir = File(context.cacheDir, "attachments-test-${System.nanoTime()}")
        store = AttachmentStore(
            dir = dir, dao = db.attachmentDao(),
            opener = SourceOpener { ByteArrayInputStream("bytes".toByteArray()) },
            mimeOf = { "image/jpeg" }, isPro = { false }, clock = { 1L },
        )
        deleter = RecordDeleter(
            RoomTransactor(db), db.attachmentDao(), db.vehicleDao(), db.energyLogDao(), db.serviceRecordDao(),
            db.expenseDao(), db.vehicleDocumentDao(), db.reminderDao(), store,
        )
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun vehicle() = db.vehicleDao().insert(
        Vehicle(
            name = "Golf", type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
            volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", createdAt = 1, updatedAt = 1,
        ),
    )

    private fun expense(vehicleId: Long) =
        Expense(vehicleId = vehicleId, dateTime = 1, category = ExpenseCategory.entries.first(), amountMinor = 1, createdAt = 1, updatedAt = 1)

    @Test fun deletingAVehicleRemovesItsAttachmentRowsAndFiles() = runBlocking {
        val vehicleId = vehicle()
        val owner = db.expenseDao().insert(expense(vehicleId))
        repeat(3) { assertTrue(store.add("src", OwnerType.EXPENSE, owner) is StageResult.Staged) }
        val names = db.attachmentDao().getAll().map { it.fileName }
        assertEquals(3, names.size)

        deleter.deleteVehicle(db.vehicleDao().get(vehicleId)!!)

        assertNull(db.vehicleDao().get(vehicleId))
        assertEquals(0, db.attachmentDao().count())
        names.forEach { assertFalse(store.fileFor(it).exists()) }
    }

    @Test fun aSaveThatFailsAfterTheAttachmentRowsLeavesNoPartialState() = runBlocking {
        val vehicleId = vehicle()
        val staged = (1..2).map { (store.stage("src") as StageResult.Staged).attachment }

        val result = runCatching {
            RoomTransactor(db).run {
                val owner = db.expenseDao().insert(expense(vehicleId))
                store.commitAll(staged, OwnerType.EXPENSE, owner)
                error("next reminder could not be saved")
            }
        }

        assertTrue(result.isFailure)
        assertEquals(0, db.expenseDao().getForVehicle(vehicleId).size)
        assertEquals(0, db.attachmentDao().count())
        staged.forEach { assertTrue("staged file kept for a retry", store.fileFor(it.fileName).exists()) }
    }
}
