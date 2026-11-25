package com.postpole.wheelfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.attachments.SourceOpener
import com.postpole.wheelfile.data.attachments.StageResult
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.document.RegionPresets
import com.postpole.wheelfile.domain.reminder.QuietHours
import com.postpole.wheelfile.domain.reminder.ReminderState
import com.postpole.wheelfile.reminders.ReminderChecker
import com.postpole.wheelfile.reminders.ReminderNotifier
import kotlinx.coroutines.flow.first
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
import java.time.LocalDate
import java.time.ZoneId

/** Documents with their expiry reminder, alerts through the reminder check, and the shared attachment cap. */
@RunWith(RobolectricTestRunner::class)
class DocumentRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val zone = ZoneId.of("UTC")
    private lateinit var db: AppDatabase
    private lateinit var store: AttachmentStore
    private lateinit var repo: DocumentRepository
    private var pro = false
    private var vehicleId = 0L

    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        store = AttachmentStore(
            dir = File(temp.newFolder("files"), "attachments"),
            dao = db.attachmentDao(),
            opener = SourceOpener { ByteArrayInputStream("bytes".toByteArray()) },
            mimeOf = { "application/pdf" },
            isPro = { pro },
            clock = { 1_000L },
        )
        repo = DocumentRepository(db.vehicleDocumentDao(), db.reminderDao(), db.attachmentDao(), store, RoomTransactor(db), deleter(db, store))
        vehicleId = runBlocking {
            db.vehicleDao().insert(
                Vehicle(
                    name = "Golf", type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
                    volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", createdAt = 1, updatedAt = 1,
                ),
            )
        }
    }

    @After
    fun tearDown() = db.close()

    private fun doc(expiry: Long? = date(2026, 7, 15), alerts: List<Int> = listOf(30, 14, 7, 1), title: String = "Insurance") =
        VehicleDocument(vehicleId = vehicleId, type = DocumentType.INSURANCE, title = title, expiryDate = expiry, alertDays = alerts)

    private fun reminders(documentId: Long) = runBlocking { db.reminderDao().getForDocument(documentId) }

    // ---- the linked reminder ----

    @Test fun savingADocumentWithAnExpiryCreatesOneLinkedReminder() = runBlocking {
        val id = repo.save(doc())
        val r = reminders(id).single()
        assertEquals(id, r.linkedDocumentId)
        assertEquals(ReminderKind.DATE, r.kind)
        assertEquals(date(2026, 7, 15), r.dueDate)
        assertEquals(30, r.leadDays) // the earliest alert
        assertEquals("Insurance", r.title)
    }

    @Test fun noExpiryOrNoAlertsMeansNoReminder() = runBlocking {
        assertTrue(reminders(repo.save(doc(expiry = null))).isEmpty())
        assertTrue(reminders(repo.save(doc(alerts = emptyList()))).isEmpty())
    }

    @Test fun removingTheExpiryDateRemovesTheReminder() = runBlocking {
        val id = repo.save(doc())
        repo.save(doc(expiry = null).copy(id = id))
        assertTrue(reminders(id).isEmpty())
    }

    @Test fun aNewExpiryDateIsARenewalSoItAlertsAgain() = runBlocking {
        val id = repo.save(doc())
        val first = reminders(id).single()
        db.reminderDao().update(first.copy(lastNotifiedAt = 5L, done = true))

        // Same date, new title: the reminder keeps its history.
        repo.save(doc(title = "Policy").copy(id = id))
        val same = reminders(id).single()
        assertEquals("Policy", same.title)
        assertEquals(5L, same.lastNotifiedAt)
        assertTrue(same.done)

        // New expiry date: alert again.
        repo.save(doc(expiry = date(2027, 7, 15), title = "Policy").copy(id = id))
        val renewed = reminders(id).single()
        assertEquals(date(2027, 7, 15), renewed.dueDate)
        assertNull(renewed.lastNotifiedAt)
        assertFalse(renewed.done)
        assertEquals(1, reminders(id).size)
    }

    // ---- alerts at 30 / 14 / 7 / 1 through the reminder check ----

    @Test fun theDailyCheckNotifiesOncePerAlertDay() = runBlocking {
        val id = repo.save(doc())
        val posted = mutableListOf<Long>()
        var now = at(2026, 6, 14)
        val checker = ReminderChecker(
            reminders = db.reminderDao(),
            vehicles = db.vehicleDao(),
            notifier = ReminderNotifier { reminder: Reminder, _, _: ReminderState -> posted += now; reminder.id > 0 },
            quietHours = { QuietHours(enabled = false) },
            clock = { now },
            zone = { zone },
            documentAlerts = { db.vehicleDocumentDao().getAll().associate { it.id to it.alertDays } },
        )
        fun run(y: Int, m: Int, d: Int): Int {
            now = at(y, m, d)
            return runBlocking { checker.run().posted }
        }
        assertEquals(0, run(2026, 6, 14)) // 31 days left
        assertEquals(1, run(2026, 6, 15)) // 30
        assertEquals(0, run(2026, 6, 16))
        assertEquals(1, run(2026, 7, 1)) // 14
        assertEquals(0, run(2026, 7, 2))
        assertEquals(1, run(2026, 7, 8)) // 7
        assertEquals(1, run(2026, 7, 14)) // 1
        assertEquals(0, run(2026, 7, 15))
        assertEquals(1, run(2026, 7, 16)) // expired
        assertEquals(0, run(2026, 7, 17))
        assertEquals(5, posted.size)
        assertEquals(id, reminders(id).single().linkedDocumentId)
    }

    // ---- attachments ----

    @Test fun deletingADocumentRemovesItsFilesRowsAndReminder() = runBlocking {
        val id = repo.save(doc())
        assertTrue(store.add("content://doc/1", OwnerType.DOCUMENT, id) is StageResult.Staged)
        val attachment = db.attachmentDao().getForOwner(OwnerType.DOCUMENT, id).single()
        val file = store.fileFor(attachment.fileName)
        assertTrue(file.exists())

        repo.delete(repo.get(id)!!)
        assertNull(repo.get(id))
        assertFalse(file.exists())
        assertEquals(0, db.attachmentDao().count())
        assertTrue(reminders(id).isEmpty())
    }

    @Test fun documentAttachmentsCountTowardsTheFreeCapOfThirty() = runBlocking {
        val id = repo.save(doc())
        // 29 receipts on entries already.
        repeat(29) {
            db.attachmentDao().insert(Attachment(ownerType = OwnerType.ENERGY, ownerId = 1, fileName = "r$it.jpg", mimeType = "image/jpeg", sizeBytes = 1, createdAt = 1))
        }
        assertTrue(store.add("content://doc/1", OwnerType.DOCUMENT, id) is StageResult.Staged) // the 30th
        assertEquals(StageResult.CapReached, store.add("content://doc/2", OwnerType.DOCUMENT, id)) // the 31st: paywall
        assertEquals(30, db.attachmentDao().count())
        // Pro has no cap.
        pro = true
        assertTrue(store.add("content://doc/3", OwnerType.DOCUMENT, id) is StageResult.Staged)
    }

    @Test fun receiptCountOnlyCountsEntriesOfThisVehicle() = runBlocking {
        val other = db.vehicleDao().insert(db.vehicleDao().get(vehicleId)!!.copy(id = 0, name = "Other"))
        val mine = db.energyLogDao().insert(com.postpole.wheelfile.data.db.EnergyLog(
            vehicleId = vehicleId, stream = com.postpole.wheelfile.data.db.EnergyStream.LIQUID, dateTime = 1, odometer = 1.0, quantity = 1.0, isFull = true, createdAt = 1, updatedAt = 1,
        ))
        val theirs = db.energyLogDao().insert(com.postpole.wheelfile.data.db.EnergyLog(
            vehicleId = other, stream = com.postpole.wheelfile.data.db.EnergyStream.LIQUID, dateTime = 1, odometer = 1.0, quantity = 1.0, isFull = true, createdAt = 1, updatedAt = 1,
        ))
        fun file(owner: Long, type: OwnerType) = Attachment(ownerType = type, ownerId = owner, fileName = "f$owner$type", mimeType = "image/jpeg", sizeBytes = 1, createdAt = 1)
        db.attachmentDao().insert(file(mine, OwnerType.ENERGY))
        db.attachmentDao().insert(file(theirs, OwnerType.ENERGY))
        db.attachmentDao().insert(file(99, OwnerType.DOCUMENT)) // documents are not receipts
        assertEquals(1, repo.observeReceiptCount(vehicleId).first())
    }

    // ---- presets ----

    @Test fun applyingPresetsCreatesDocumentsWithDatesAndSkipsDuplicates() = runBlocking {
        val vehicle = db.vehicleDao().get(vehicleId)!!.copy(year = 2023)
        val presets = listOf(checkNotNull(RegionPresets.find("DE_HU")), checkNotNull(RegionPresets.find("ALL_INSURANCE")))
        val plans = RegionPresets.plans(presets, vehicle.year, LocalDate.of(2026, 6, 15))
        assertEquals(2, repo.applyPlans(vehicle, plans, zone))

        val docs = db.vehicleDocumentDao().observeForVehicle(vehicleId).first()
        val hu = docs.first { it.title == "HU (TÜV)" }
        assertEquals(DocumentType.INSPECTION, hu.type)
        assertEquals(date(2028, 1, 1), hu.expiryDate)
        assertNotNull(reminders(hu.id).single().presetCode)
        // Insurance has no date yet: a document without alerts, to be edited.
        val insurance = docs.first { it.type == DocumentType.INSURANCE }
        assertNull(insurance.expiryDate)
        assertTrue(reminders(insurance.id).isEmpty())

        // Offering the same presets again adds nothing.
        assertEquals(0, repo.applyPlans(vehicle, plans, zone))
        assertEquals(2, db.vehicleDocumentDao().getAll().size)
    }
}
