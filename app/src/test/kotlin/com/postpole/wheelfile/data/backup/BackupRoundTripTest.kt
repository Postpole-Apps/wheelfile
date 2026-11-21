package com.postpole.wheelfile.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.ChargerType
import com.postpole.wheelfile.data.db.CustomCategory
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Recurrence
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    private lateinit var db: AppDatabase
    private lateinit var root: File
    private lateinit var attachments: File

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        root = File.createTempFile("backup-test", "").apply {
            delete()
            mkdirs()
        }
        attachments = File(root, "attachments").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun manager(pro: Boolean = true) = BackupManager(db, attachments, File(root, "tmp").apply { mkdirs() }, { pro }, { 5_000L }, "1.0.0")

    private fun vehicle(name: String, archived: Boolean = false) = Vehicle(
        name = name, type = VehicleType.CAR, powertrain = Powertrain.PHEV, make = "Acme", year = 2020, plate = "P-1",
        distanceUnit = DistanceUnit.MI, volumeUnit = VolumeUnit.US_GAL, economyDisplay = EconomyUnit.MPG_US,
        currency = "USD", tankCapacity = 12.5, batteryKwh = 10.0, archived = archived, soldAt = null, createdAt = 10, updatedAt = 20,
    )

    /** Fills every table, including nulls, lists, enums and doubles, and writes two attachment files. */
    private fun seed(vehicles: Int = 2): Unit = runBlocking {
        val ids = (1..vehicles).map { db.vehicleDao().insert(vehicle("Car $it")) }
        val v = ids.first()
        db.energyLogDao().insert(
            EnergyLog(
                vehicleId = v, stream = EnergyStream.LIQUID, dateTime = 1_700_000_000_000, odometer = 1234.5, quantity = 9.875,
                pricePerUnitMinor = 389, totalMinor = 3841, isFull = true, fuelGrade = "95", station = "Main, \"North\"\nline",
                note = null, importSource = "FUELIO", importKey = "k1", createdAt = 1, updatedAt = 2,
            ),
        )
        db.energyLogDao().insert(
            EnergyLog(
                vehicleId = v, stream = EnergyStream.ELECTRIC, dateTime = 1_700_100_000_000, odometer = 1300.0, quantity = 12.0,
                isFull = false, missedPrevious = true, chargerType = ChargerType.DC_FAST, socStart = 20, socEnd = 80,
                createdAt = 1, updatedAt = 2,
            ),
        )
        val service = db.serviceRecordDao().insert(
            ServiceRecord(
                vehicleId = v, dateTime = 1_700_200_000_000, odometer = 1350.0, items = listOf("Oil", "Filter, air"),
                totalMinor = 12000, workshop = "Garage", createdAt = 1, updatedAt = 2,
            ),
        )
        db.expenseDao().insert(
            Expense(
                vehicleId = v, dateTime = 1_700_300_000_000, category = ExpenseCategory.INSURANCE, amountMinor = 45000,
                recurring = Recurrence.YEARLY, createdAt = 1, updatedAt = 2,
            ),
        )
        val doc = db.vehicleDocumentDao().insert(
            VehicleDocument(
                vehicleId = v, type = DocumentType.INSURANCE, title = "Policy", number = "N-1", expiryDate = 1_800_000_000_000,
                alertDays = listOf(30, 7),
            ),
        )
        db.reminderDao().insert(
            Reminder(vehicleId = v, title = "MOT", kind = ReminderKind.DATE, dueDate = 1_800_000_000_000, linkedDocumentId = doc, done = true),
        )
        db.customCategoryDao().insert(CustomCategory(name = "Tolls extra", kind = "expense"))
        File(attachments, "receipt-1.jpg").writeBytes(ByteArray(2048) { (it * 7).toByte() })
        File(attachments, "policy.pdf").writeBytes("%PDF-1.4 synthetic".toByteArray())
        db.attachmentDao().insert(Attachment(ownerType = OwnerType.SERVICE, ownerId = service, fileName = "receipt-1.jpg", mimeType = "image/jpeg", sizeBytes = 2048, createdAt = 3))
        db.attachmentDao().insert(Attachment(ownerType = OwnerType.DOCUMENT, ownerId = doc, fileName = "policy.pdf", mimeType = "application/pdf", sizeBytes = 18, createdAt = 4))
    }

    private fun backupBytes(m: BackupManager): ByteArray = ByteArrayOutputStream().also { out -> runBlocking { m.backup(out) } }.toByteArray()

    private fun wipe() {
        db.clearAllTables()
        attachments.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun backupWipeRestoreGivesIdenticalDatabaseHashAndAttachments() = runBlocking {
        seed()
        val m = manager()
        val hashBefore = m.databaseHash()
        val filesBefore = attachments.listFiles()!!.associate { it.name to it.readBytes() }
        val bytes = backupBytes(m)

        wipe()
        assertEquals(0, db.vehicleDao().getAll().size)
        assertNotEquals(hashBefore, m.databaseHash())
        assertTrue(attachments.listFiles()!!.isEmpty())

        val outcome = m.restore(ByteArrayInputStream(bytes))
        assertEquals(RestoreOutcome.Restored(vehicles = 2, archivedForCap = 0), outcome)
        assertEquals(hashBefore, m.databaseHash())
        val filesAfter = attachments.listFiles()!!.associate { it.name to it.readBytes() }
        assertEquals(filesBefore.keys, filesAfter.keys)
        filesBefore.forEach { (name, content) -> assertArrayEquals(name, content, filesAfter[name]) }
        // Links between tables survive: the attachment still points at its service record.
        val att = db.attachmentDao().getAll()
        assertEquals(2, att.size)
        assertTrue(db.reminderDao().getAll().single().done)
    }

    @Test
    fun backupFileHasManifestDataAndAttachments() {
        seed()
        val names = mutableListOf<String>()
        var manifest = ""
        ZipInputStream(ByteArrayInputStream(backupBytes(manager()))).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                names += e.name
                if (e.name == "manifest.json") manifest = String(z.readBytes())
            }
        }
        assertTrue(names.containsAll(listOf("manifest.json", "data.json", "attachments/receipt-1.jpg", "attachments/policy.pdf")))
        val json = org.json.JSONObject(manifest)
        assertEquals("wheelfile-backup", json.getString("format"))
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("1.0.0", json.getString("appVersion"))
        assertEquals(5_000L, json.getLong("createdAt"))
    }

    @Test
    fun restoreIsNeverCappedExtraActiveVehiclesAreArchivedOnFree() = runBlocking {
        seed(vehicles = 3)
        val bytes = backupBytes(manager(pro = true))
        wipe()
        val outcome = manager(pro = false).restore(ByteArrayInputStream(bytes))
        assertEquals(RestoreOutcome.Restored(vehicles = 3, archivedForCap = 1), outcome)
        val all = db.vehicleDao().getAll()
        assertEquals(3, all.size) // nothing dropped
        assertEquals(2, all.count { !it.archived })
        assertEquals(2, db.energyLogDao().getAll(all.first { it.name == "Car 1" }.id).size)
        assertEquals(2, db.attachmentDao().getAll().size)
    }

    @Test
    fun restoreReplacesWhatIsInTheApp() = runBlocking {
        seed(vehicles = 1)
        val m = manager()
        val bytes = backupBytes(m)
        db.vehicleDao().insert(vehicle("Added later"))
        File(attachments, "stray.bin").writeBytes(byteArrayOf(1))
        m.restore(ByteArrayInputStream(bytes))
        assertEquals(listOf("Car 1"), db.vehicleDao().getAll().map { it.name })
        assertFalse(File(attachments, "stray.bin").exists())
    }

    @Test
    fun invalidFilesChangeNothing() = runBlocking {
        seed()
        val m = manager()
        val hash = m.databaseHash()
        assertEquals(RestoreOutcome.Invalid, m.restore(ByteArrayInputStream("not a zip".toByteArray())))
        assertEquals(RestoreOutcome.Invalid, m.restore(ByteArrayInputStream(zipOf("hello.txt" to "x".toByteArray()))))
        val wrongFormat = zipOf(
            "manifest.json" to """{"format":"other","schemaVersion":1}""".toByteArray(),
            "data.json" to "{}".toByteArray(),
        )
        assertEquals(RestoreOutcome.Invalid, m.restore(ByteArrayInputStream(wrongFormat)))
        val newer = zipOf(
            "manifest.json" to """{"format":"wheelfile-backup","schemaVersion":99}""".toByteArray(),
            "data.json" to "{}".toByteArray(),
        )
        assertEquals(RestoreOutcome.Invalid, m.restore(ByteArrayInputStream(newer)))
        val brokenData = zipOf(
            "manifest.json" to """{"format":"wheelfile-backup","schemaVersion":1}""".toByteArray(),
            "data.json" to """{"vehicles":[{"id":1}]}""".toByteArray(),
        )
        assertEquals(RestoreOutcome.Invalid, m.restore(ByteArrayInputStream(brokenData)))
        assertEquals(hash, m.databaseHash())
        assertEquals(2, attachments.listFiles()!!.size)
    }

    @Test
    fun attachmentPathsCannotEscapeTheAttachmentFolder() = runBlocking {
        val m = manager()
        val bytes = zipOf(
            "manifest.json" to """{"format":"wheelfile-backup","schemaVersion":1}""".toByteArray(),
            "data.json" to "{}".toByteArray(),
            "attachments/../../evil.txt" to "x".toByteArray(),
        )
        assertEquals(RestoreOutcome.Restored(0, 0), m.restore(ByteArrayInputStream(bytes)))
        assertFalse(File(root, "evil.txt").exists())
        assertFalse(File(root.parentFile, "evil.txt").exists())
        assertEquals(listOf("evil.txt"), attachments.listFiles()!!.map { it.name }) // flattened to a bare name inside the folder
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
