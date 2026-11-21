package com.postpole.wheelfile.export

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var dir: File
    private var pro = false

    private fun service(database: AppDatabase = db, attachments: File = dir) =
        BackupService(database, attachments, { pro }, { 1_700_000_000_000L }, "1.0.0-test")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dir = File(tmp.root, "files/attachments").also { it.mkdirs() }
    }

    @After
    fun tearDown() = db.close()

    private fun seed(data: DataSnapshot = ExportSampleData.snapshot()) = runBlocking {
        val dao = db.backupDao()
        dao.insertVehicles(data.vehicles)
        dao.insertEnergyLogs(data.energyLogs)
        dao.insertServiceRecords(data.serviceRecords)
        dao.insertExpenses(data.expenses)
        dao.insertDocuments(data.documents)
        dao.insertReminders(data.reminders)
        dao.insertAttachments(data.attachments)
        dao.insertCustomCategories(data.customCategories)
        File(dir, "a1.pdf").writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        File(dir, "a2.jpg").writeBytes(byteArrayOf(9, 8, 7, 6, 5, 4))
    }

    /** SHA-256 over every table's rows in id order. */
    private fun dbHash(database: AppDatabase = db): String = runBlocking {
        val json = BackupCodec.encode(database.withTransaction { database.backupDao().snapshot() })
        MessageDigest.getInstance("SHA-256").digest(json.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun filesOf(d: File = dir) = d.listFiles().orEmpty().sortedBy { it.name }.associate { it.name to it.readBytes().toList() }

    private fun backup(): ByteArray = runBlocking { ByteArrayOutputStream().also { service().write(it) }.toByteArray() }

    @Test
    fun backupWipeRestoreGivesIdenticalDatabaseAndAttachments() = runBlocking {
        seed()
        val hashBefore = dbHash()
        val filesBefore = filesOf()
        val bytes = backup()

        // Wipe everything.
        db.backupDao().deleteVehicles()
        db.backupDao().deleteAttachments()
        db.backupDao().deleteCustomCategories()
        dir.listFiles()!!.forEach { it.delete() }
        assertTrue(db.vehicleDao().getAll().isEmpty())
        assertTrue(filesOf().isEmpty())

        val summary = service().restore(ByteArrayInputStream(bytes))

        assertEquals(hashBefore, dbHash())
        assertEquals(filesBefore, filesOf())
        assertEquals(2, summary.vehicles)
        assertEquals(4, summary.entries) // 2 energy logs, 1 service, 1 expense
        assertEquals(2, summary.attachments)
    }

    @Test
    fun restoreIntoAFreshInstallGivesTheSameData() = runBlocking {
        seed()
        val hashBefore = dbHash()
        val bytes = backup()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val other = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val otherDir = File(tmp.root, "other/files/attachments")
        try {
            service(other, otherDir).restore(ByteArrayInputStream(bytes))
            assertEquals(hashBefore, dbHash(other))
            assertEquals(filesOf(dir), filesOf(otherDir))
        } finally {
            other.close()
        }
    }

    @Test
    fun manifestHasSchemaVersionAppVersionAndCreatedAt() = runBlocking {
        seed()
        val manifest = BackupManifest.parse(
            java.util.zip.ZipInputStream(ByteArrayInputStream(backup())).let { zip ->
                var json = ""
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name == "manifest.json") json = String(zip.readBytes())
                }
                json
            },
        )
        assertEquals("wheelfile-backup", manifest.format)
        assertEquals(AppDatabase.VERSION, manifest.schemaVersion)
        assertEquals("1.0.0-test", manifest.appVersion)
        assertEquals(1_700_000_000_000L, manifest.createdAt)
        assertEquals(2, manifest.vehicleCount)
        assertEquals(2, manifest.attachmentCount)
    }

    @Test
    fun restoreIsNeverCappedOnTheFreeTier() = runBlocking {
        val many = ExportSampleData.snapshot(
            listOf(ExportSampleData.vehicle(1, "A"), ExportSampleData.vehicle(2, "B"), ExportSampleData.vehicle(3, "C"), ExportSampleData.vehicle(4, "D", archived = true)),
        )
        seed(many)
        val bytes = backup()
        db.backupDao().deleteVehicles()

        val summary = service().restore(ByteArrayInputStream(bytes))

        assertEquals(4, summary.vehicles)
        assertEquals(1, summary.archivedForCap)
        val vehicles = db.vehicleDao().getAll().sortedBy { it.id }
        assertEquals(listOf(false, false, true, true), vehicles.map { it.archived })
        assertEquals(2, db.energyLogDao().getAll(1).size) // data of the vehicles stays attached
    }

    @Test
    fun proRestoresEveryVehicleActive() = runBlocking {
        pro = true
        seed(ExportSampleData.snapshot(listOf(ExportSampleData.vehicle(1, "A"), ExportSampleData.vehicle(2, "B"), ExportSampleData.vehicle(3, "C"))))
        val bytes = backup()
        db.backupDao().deleteVehicles()
        val summary = service().restore(ByteArrayInputStream(bytes))
        assertEquals(0, summary.archivedForCap)
        assertEquals(3, db.vehicleDao().activeCount())
    }

    @Test
    fun anInvalidFileLeavesTheCurrentDataUntouched() = runBlocking {
        seed()
        val before = dbHash()
        val filesBefore = filesOf()
        val notABackup = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("hello.txt"))
                z.write("hi".toByteArray())
                z.closeEntry()
            }
        }.toByteArray()
        for (bad in listOf(notABackup, "plain text".toByteArray(), ByteArray(0))) {
            try {
                service().restore(ByteArrayInputStream(bad))
                throw AssertionError("expected BackupException")
            } catch (e: BackupException) {
                assertFalse(e.message.isNullOrBlank())
            }
        }
        assertEquals(before, dbHash())
        assertEquals(filesBefore, filesOf())
        assertFalse(File(dir.parentFile, "attachments_restore").exists())
    }

    @Test
    fun aBackupFromANewerSchemaIsRejected() = runBlocking {
        seed()
        val newer = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("manifest.json"))
                z.write(BackupManifest("wheelfile-backup", 1, AppDatabase.VERSION + 1, "9.9", 1, 0, 0).toJson().toByteArray())
                z.closeEntry()
                z.putNextEntry(ZipEntry("data.json"))
                z.write("{}".toByteArray())
                z.closeEntry()
            }
        }.toByteArray()
        val before = dbHash()
        try {
            service().restore(ByteArrayInputStream(newer))
            throw AssertionError("expected BackupException")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("newer"))
        }
        assertEquals(before, dbHash())
    }

    @Test
    fun attachmentEntriesCannotEscapeTheAttachmentsFolder() = runBlocking {
        seed()
        val bytes = backup()
        val tampered = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                    while (true) {
                        val e = zin.nextEntry ?: break
                        z.putNextEntry(ZipEntry(e.name))
                        z.write(zin.readBytes())
                        z.closeEntry()
                    }
                }
                z.putNextEntry(ZipEntry("attachments/../../evil.txt"))
                z.write("x".toByteArray())
                z.closeEntry()
            }
        }.toByteArray()
        service().restore(ByteArrayInputStream(tampered))
        assertFalse(File(tmp.root, "evil.txt").exists())
        assertFalse(File(tmp.root, "files/evil.txt").exists())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), File(dir, "a1.pdf").readBytes())
    }
}
