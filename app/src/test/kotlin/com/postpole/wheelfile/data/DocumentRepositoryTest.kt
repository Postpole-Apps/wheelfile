package com.postpole.wheelfile.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.data.attachments.AttachmentStore
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
import com.postpole.wheelfile.domain.document.DocumentAlerts
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Documents own one reminder when they have an expiry date, and the daily check alerts at 30/14/7/1 days. */
@RunWith(RobolectricTestRunner::class)
class DocumentRepositoryTest {
    private val zone = ZoneId.of("UTC")
    private lateinit var db: AppDatabase
    private lateinit var repo: DocumentRepository
    private lateinit var dir: File
    private var now = at("2026-12-01", 10)
    private val posted = mutableListOf<Long>()

    private fun at(d: String, hour: Int = 10) = LocalDate.parse(d).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun day(d: String) = LocalDate.parse(d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dir = File(context.cacheDir, "doc-test-${System.nanoTime()}")
        val store = AttachmentStore(
            dir = dir, dao = db.attachmentDao(), opener = { ByteArrayInputStream(ByteArray(10)) },
            mimeOf = { "application/pdf" }, isPro = { false }, clock = { now },
        )
        repo = DocumentRepository(db.vehicleDocumentDao(), db.reminderDao(), db.attachmentDao(), store)
        runBlocking {
            db.vehicleDao().insert(
                Vehicle(
                    id = 1, name = "Golf", type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
                    volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", createdAt = 1, updatedAt = 1,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun checker() = ReminderChecker(
        reminders = db.reminderDao(),
        vehicles = db.vehicleDao(),
        notifier = ReminderNotifier { reminder: Reminder, _, _: ReminderState -> posted += reminder.id; true },
        quietHours = { QuietHours(enabled = false) },
        clock = { now },
        zone = { zone },
        documents = { db.vehicleDocumentDao().get(it) },
    )

    private fun doc(expiry: String?, title: String = "Policy") = VehicleDocument(
        vehicleId = 1, type = DocumentType.INSURANCE, title = title, expiryDate = expiry?.let(::day),
    )

    @Test fun savingADocumentWithAnExpiryCreatesOneLinkedReminder() = runBlocking {
        val id = repo.save(doc("2026-12-31"), presetCode = "insurance_renewal")
        val reminder = db.reminderDao().getForDocument(id).single()
        assertEquals(id, reminder.linkedDocumentId)
        assertEquals(ReminderKind.DATE, reminder.kind)
        assertEquals(day("2026-12-31"), reminder.dueDate)
        assertEquals(30, reminder.leadDays)
        assertEquals("insurance_renewal", reminder.presetCode)
        assertEquals("Policy", reminder.title)
    }

    @Test fun aDocumentWithoutAnExpiryHasNoReminder() = runBlocking {
        val id = repo.save(doc(null))
        assertTrue(db.reminderDao().getForDocument(id).isEmpty())
    }

    @Test fun changingTheExpiryMovesTheReminderAndRestartsTheAlerts() = runBlocking {
        val id = repo.save(doc("2026-12-31"))
        val first = db.reminderDao().getForDocument(id).single()
        db.reminderDao().update(first.copy(lastNotifiedAt = now, done = true))
        repo.save(repo.get(id)!!.copy(expiryDate = day("2027-12-31"), title = "Policy 2027"))
        val moved = db.reminderDao().getForDocument(id).single()
        assertEquals(first.id, moved.id)
        assertEquals(day("2027-12-31"), moved.dueDate)
        assertEquals("Policy 2027", moved.title)
        assertNull(moved.lastNotifiedAt)
        assertFalse(moved.done)
    }

    @Test fun removingTheExpiryRemovesTheReminder() = runBlocking {
        val id = repo.save(doc("2026-12-31"))
        repo.save(repo.get(id)!!.copy(expiryDate = null))
        assertTrue(db.reminderDao().getForDocument(id).isEmpty())
    }

    @Test fun shorterAlertListLowersTheReminderLeadTime() = runBlocking {
        val id = repo.save(doc("2026-12-31").copy(alertDays = listOf(7, 1)))
        assertEquals(7, db.reminderDao().getForDocument(id).single().leadDays)
    }

    @Test fun deletingADocumentRemovesItsReminderAndAttachmentFiles() = runBlocking {
        val id = repo.save(doc("2026-12-31"))
        val file = File(dir, "scan.pdf").apply { dir.mkdirs(); writeText("x") }
        db.attachmentDao().insert(
            com.postpole.wheelfile.data.db.Attachment(
                ownerType = OwnerType.DOCUMENT, ownerId = id, fileName = file.name, mimeType = "application/pdf", sizeBytes = 1, createdAt = 1,
            ),
        )
        repo.delete(repo.get(id)!!)
        assertNull(repo.get(id))
        assertTrue(db.reminderDao().getForDocument(id).isEmpty())
        assertEquals(0, db.attachmentDao().count())
        assertFalse(file.exists())
    }

    @Test fun observeItemsCountsFilesPerDocument() = runBlocking {
        val a = repo.save(doc(null, "A"))
        val b = repo.save(doc(null, "B"))
        listOf(a, a, b).forEach {
            db.attachmentDao().insert(
                com.postpole.wheelfile.data.db.Attachment(
                    ownerType = OwnerType.DOCUMENT, ownerId = it, fileName = "f$it", mimeType = "image/jpeg", sizeBytes = 1, createdAt = 1,
                ),
            )
        }
        val items = repo.observeItems(1).first().associate { it.document.title to it.attachmentCount }
        assertEquals(mapOf("A" to 2, "B" to 1), items)
    }

    @Test fun documentAttachmentsCountTowardsTheFreeCap() = runBlocking {
        val id = repo.save(doc(null))
        repeat(30) {
            db.attachmentDao().insert(
                com.postpole.wheelfile.data.db.Attachment(
                    ownerType = if (it % 2 == 0) OwnerType.DOCUMENT else OwnerType.SERVICE, ownerId = id, fileName = "f$it",
                    mimeType = "image/jpeg", sizeBytes = 1, createdAt = 1,
                ),
            )
        }
        assertEquals(30, db.attachmentDao().count())
        val store = AttachmentStore(
            dir = dir, dao = db.attachmentDao(), opener = { ByteArrayInputStream(ByteArray(1)) },
            mimeOf = { "image/jpeg" }, isPro = { false }, clock = { now },
        )
        assertTrue(store.stage("content://x") is com.postpole.wheelfile.data.attachments.StageResult.CapReached)
    }

    // ---- the daily check ----

    @Test fun theDailyCheckAlertsAtEachAlertDayThroughTheReminderWorker() = runBlocking {
        val id = repo.save(doc("2026-12-31"))
        val reminderId = db.reminderDao().getForDocument(id).single().id
        val fired = mutableListOf<String>()
        var date = LocalDate.parse("2026-11-25")
        while (!date.isAfter(LocalDate.parse("2027-01-03"))) {
            now = at(date.toString())
            val before = posted.size
            checker().run()
            if (posted.size > before) fired += date.toString()
            date = date.plusDays(1)
        }
        assertEquals(listOf("2026-12-01", "2026-12-17", "2026-12-24", "2026-12-30", "2027-01-01"), fired)
        assertTrue(posted.all { it == reminderId })
    }

    @Test fun anAlertDayTheUserSwitchedOffDoesNotFire() = runBlocking {
        repo.save(doc("2026-12-31").copy(alertDays = listOf(7)))
        now = at("2026-12-01")
        checker().run()
        assertTrue(posted.isEmpty())
        now = at("2026-12-24")
        checker().run()
        assertEquals(1, posted.size)
    }

    @Test fun aReminderForADeletedDocumentIsSkipped() = runBlocking {
        val id = repo.save(doc("2026-12-31"))
        db.vehicleDocumentDao().delete(db.vehicleDocumentDao().get(id)!!)
        now = at("2026-12-24")
        checker().run()
        assertTrue(posted.isEmpty())
        assertNotNull(db.reminderDao().getForDocument(id).firstOrNull())
    }

    @Test fun defaultAlertDaysAreStoredOnNewDocuments() = runBlocking {
        val id = repo.save(doc("2026-12-31"))
        assertEquals(DocumentAlerts.DEFAULT_DAYS, db.vehicleDocumentDao().get(id)!!.alertDays)
    }
}
