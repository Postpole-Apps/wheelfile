package com.postpole.wheelfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.data.attachments.AttachmentLimit
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.attachments.SourceOpener
import com.postpole.wheelfile.data.attachments.StageResult
import com.postpole.wheelfile.data.attachments.StagedAttachment
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Attachment rows have no foreign key (one table serves several owner types), so every delete removes them itself.
 * Orphans must not count towards the free cap of 30 (spec §5.1), and a failed save or delete must not leave a
 * half-written state (issue #12 items 2, 3 and the PR #13 follow-up).
 */
@RunWith(RobolectricTestRunner::class)
class AttachmentCascadeTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var dir: File
    private lateinit var store: AttachmentStore
    private lateinit var deleter: RecordDeleter
    private lateinit var documents: DocumentRepository
    private var pro = false
    private var vehicleId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dir = File(temp.newFolder("files"), "attachments")
        store = AttachmentStore(
            dir = dir, dao = db.attachmentDao(),
            opener = SourceOpener { ByteArrayInputStream("bytes".toByteArray()) },
            mimeOf = { "image/jpeg" }, isPro = { pro }, clock = { 1_000L },
        )
        deleter = deleter(db, store)
        documents = DocumentRepository(
            db.vehicleDocumentDao(), db.reminderDao(), db.attachmentDao(), store, RoomTransactor(db), deleter,
        )
        vehicleId = runBlocking { newVehicle("Golf") }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun newVehicle(name: String) = db.vehicleDao().insert(
        Vehicle(
            name = name, type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
            volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", createdAt = 1, updatedAt = 1,
        ),
    )

    private suspend fun attach(type: OwnerType, ownerId: Long, count: Int = 1): List<Attachment> {
        repeat(count) { check(store.add("src", type, ownerId) is StageResult.Staged) }
        return db.attachmentDao().getForOwner(type, ownerId)
    }

    private suspend fun fuelUp(vehicle: Long = vehicleId) = db.energyLogDao().insert(
        EnergyLog(vehicleId = vehicle, stream = EnergyStream.LIQUID, dateTime = 1, odometer = 100.0, quantity = 40.0, isFull = true, createdAt = 1, updatedAt = 1),
    )

    private suspend fun service(vehicle: Long = vehicleId) =
        db.serviceRecordDao().insert(ServiceRecord(vehicleId = vehicle, dateTime = 1, createdAt = 1, updatedAt = 1))

    private suspend fun expense(vehicle: Long = vehicleId) = db.expenseDao().insert(
        Expense(vehicleId = vehicle, dateTime = 1, category = ExpenseCategory.entries.first(), amountMinor = 100, createdAt = 1, updatedAt = 1),
    )

    private suspend fun document(vehicle: Long = vehicleId) = db.vehicleDocumentDao().insert(
        VehicleDocument(vehicleId = vehicle, type = DocumentType.INSURANCE, title = "Policy"),
    )

    private fun files() = dir.list().orEmpty().toSet()

    // ---- item 2: cascade ----

    @Test fun deletingAVehicleRemovesEveryAttachmentRowAndFileOfItsEntriesAndDocuments() = runBlocking {
        val other = newVehicle("Polo")
        val keep = attach(OwnerType.EXPENSE, expense(other))
        val mine = attach(OwnerType.ENERGY, fuelUp()) + attach(OwnerType.SERVICE, service(), 2) +
            attach(OwnerType.EXPENSE, expense()) + attach(OwnerType.DOCUMENT, document(), 2)
        assertEquals(7, db.attachmentDao().count())

        deleter.deleteVehicle(db.vehicleDao().get(vehicleId)!!)

        assertEquals("only the other vehicle's attachment remains", keep.map { it.id }, db.attachmentDao().getAll().map { it.id })
        assertEquals(keep.map { it.fileName }.toSet(), files())
        mine.forEach { assertFalse(store.fileFor(it.fileName).exists()) }
        assertNull(db.vehicleDao().get(vehicleId))
        assertTrue(db.energyLogDao().getAll(vehicleId).isEmpty())
    }

    @Test fun deletingAnEntryRemovesItsAttachmentsOnly() = runBlocking {
        val fuel = fuelUp()
        val svc = service()
        val exp = expense()
        attach(OwnerType.ENERGY, fuel)
        val svcFiles = attach(OwnerType.SERVICE, svc, 2)
        val expFiles = attach(OwnerType.EXPENSE, exp)

        deleter.deleteEnergyLog(db.energyLogDao().get(fuel)!!)
        assertEquals(3, db.attachmentDao().count())

        deleter.deleteService(db.serviceRecordDao().getForVehicle(vehicleId).single())
        assertEquals(listOf(expFiles.single().id), db.attachmentDao().getAll().map { it.id })
        svcFiles.forEach { assertFalse(store.fileFor(it.fileName).exists()) }

        deleter.deleteExpense(db.expenseDao().getForVehicle(vehicleId).single())
        assertEquals(0, db.attachmentDao().count())
        assertTrue(files().isEmpty())
    }

    @Test fun orphansDoNotCountTowardsTheFreeCap() = runBlocking {
        val exp = expense()
        attach(OwnerType.EXPENSE, exp, AttachmentLimit.FREE_ATTACHMENTS)
        assertEquals(StageResult.CapReached, store.stage("src"))

        deleter.deleteExpense(db.expenseDao().getForVehicle(vehicleId).single())

        assertEquals(0, db.attachmentDao().count())
        assertTrue("free again after the delete", store.stage("src") is StageResult.Staged)
    }

    @Test fun deletingADocumentRemovesRowsFilesAndTheLinkedReminderTogether() = runBlocking {
        val id = documents.save(VehicleDocument(vehicleId = vehicleId, type = DocumentType.INSURANCE, title = "Policy", expiryDate = 5_000_000_000L))
        val rows = attach(OwnerType.DOCUMENT, id, 2)
        assertEquals(1, db.reminderDao().getForDocument(id).size)

        documents.delete(documents.get(id)!!)

        assertNull(documents.get(id))
        assertEquals(0, db.attachmentDao().count())
        assertTrue(db.reminderDao().getForDocument(id).isEmpty())
        rows.forEach { assertFalse(store.fileFor(it.fileName).exists()) }
    }

    @Test fun aFailedDocumentDeleteLeavesRowsAndFilesUntouched() = runBlocking {
        val id = document()
        val rows = attach(OwnerType.DOCUMENT, id, 2)
        val doc = db.vehicleDocumentDao().get(id)!!
        val failing = RecordDeleter(
            failBeforeCommit(), db.attachmentDao(), db.vehicleDao(), db.energyLogDao(), db.serviceRecordDao(),
            db.expenseDao(), db.vehicleDocumentDao(), db.reminderDao(), store,
        )

        assertTrue(runCatching { failing.deleteDocument(doc) }.isFailure)

        assertEquals("rows rolled back", 2, db.attachmentDao().count())
        assertNotNull("document survives", db.vehicleDocumentDao().get(id))
        rows.forEach { assertTrue("file survives a rolled-back delete", store.fileFor(it.fileName).exists()) }
    }

    /** Runs the body in a real transaction, then throws before it can commit. */
    private fun failBeforeCommit() = object : Transactor {
        override suspend fun <T> run(block: suspend () -> T): T = RoomTransactor(db).run {
            block()
            error("boom before commit")
        }
    }

    // ---- item 3: transactional commit ----

    @Test fun aFailedSaveLeavesNoPartialRowsAndKeepsTheStagedFilesForARetry() = runBlocking {
        val staged = listOf(stageFile(), stageFile())

        val result = runCatching {
            RoomTransactor(db).run {
                val owner = db.expenseDao().insert(
                    Expense(vehicleId = vehicleId, dateTime = 1, category = ExpenseCategory.entries.first(), amountMinor = 1, createdAt = 1, updatedAt = 1),
                )
                store.commitAll(staged, OwnerType.EXPENSE, owner)
                error("reminder insert failed")
            }
        }

        assertTrue(result.isFailure)
        assertEquals("no owner row", 0, db.expenseDao().getForVehicle(vehicleId).size)
        assertEquals("no attachment rows", 0, db.attachmentDao().count())
        assertEquals("staged files stay for the retry", staged.map { it.fileName }.toSet(), files())
    }

    @Test fun aSuccessfulSaveWritesTheOwnerAndEveryAttachmentRow() = runBlocking {
        val staged = listOf(stageFile(), stageFile())
        val dropped = RoomTransactor(db).run {
            val owner = db.expenseDao().insert(
                Expense(vehicleId = vehicleId, dateTime = 1, category = ExpenseCategory.entries.first(), amountMinor = 1, createdAt = 1, updatedAt = 1),
            )
            store.commitAll(staged, OwnerType.EXPENSE, owner)
        }
        assertTrue(dropped.isEmpty())
        assertEquals(2, db.attachmentDao().count())
        assertEquals(1, db.expenseDao().getForVehicle(vehicleId).size)
    }

    @Test fun commitAllDropsFilesOverTheCapAndTheCallerDeletesThemAfterTheCommit() = runBlocking {
        attach(OwnerType.EXPENSE, expense(), AttachmentLimit.FREE_ATTACHMENTS - 1)
        val staged = listOf(stageFile(), stageFile())

        val dropped = store.commitAll(staged, OwnerType.SERVICE, service())

        assertEquals(AttachmentLimit.FREE_ATTACHMENTS, db.attachmentDao().count())
        assertEquals(listOf(staged[1]), dropped)
        assertTrue("still on disk until the caller discards after commit", store.fileFor(staged[1].fileName).exists())
        store.discardAll(dropped)
        assertFalse(store.fileFor(staged[1].fileName).exists())
        assertTrue(store.fileFor(staged[0].fileName).exists())
    }

    @Test fun savingADocumentWithAttachmentsIsAllOrNothing() = runBlocking {
        val id = documents.save(VehicleDocument(vehicleId = vehicleId, type = DocumentType.INSURANCE, title = "Policy"))
        val old = attach(OwnerType.DOCUMENT, id)
        val staged = listOf(stageFile())
        val failing = DocumentRepository(
            db.vehicleDocumentDao(), db.reminderDao(), db.attachmentDao(), store,
            failBeforeCommit(),
            deleter,
        )

        val failed = runCatching {
            failing.saveWithAttachments(documents.get(id)!!.copy(title = "Renamed"), staged = staged, removed = old)
        }

        assertTrue(failed.isFailure)
        assertEquals("Policy", documents.get(id)!!.title)
        assertEquals("the removed row is back", old.map { it.id }, db.attachmentDao().getAll().map { it.id })
        assertTrue("the removed file is not deleted", store.fileFor(old.single().fileName).exists())
        assertTrue("the staged file is kept for a retry", store.fileFor(staged.single().fileName).exists())

        documents.saveWithAttachments(documents.get(id)!!.copy(title = "Renamed"), staged = staged, removed = old)
        assertEquals("Renamed", documents.get(id)!!.title)
        assertEquals(listOf(staged.single().fileName), db.attachmentDao().getAll().map { it.fileName })
        assertFalse(store.fileFor(old.single().fileName).exists())
    }

    private suspend fun stageFile(): StagedAttachment = (store.stage("src") as StageResult.Staged).attachment
}
