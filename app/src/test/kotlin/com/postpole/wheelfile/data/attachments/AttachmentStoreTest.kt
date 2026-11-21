package com.postpole.wheelfile.data.attachments

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.db.OwnerType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class)
class AttachmentStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var dir: File
    private var pro = false

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dir = File(temp.newFolder("files"), "attachments")
    }

    @After
    fun tearDown() = db.close()

    private fun store() = AttachmentStore(
        dir = dir,
        dao = db.attachmentDao(),
        opener = SourceOpener { path -> File(path).takeIf { it.exists() }?.let { FileInputStream(it) } },
        mimeOf = { if (it.endsWith(".pdf")) "application/pdf" else "image/jpeg" },
        isPro = { pro },
        clock = { 1_000L },
    )

    private fun source(name: String = "receipt.jpg", text: String = "bytes") = temp.newFile(name).apply { writeText(text) }

    @Test
    fun copiesIntoPrivateDirectoryAndSurvivesDeletingTheSource() = runBlocking {
        val original = source(text = "receipt-bytes")
        val result = store().add(original.absolutePath, OwnerType.SERVICE, ownerId = 7)
        assertTrue(result is StageResult.Staged)
        val staged = (result as StageResult.Staged).attachment

        assertTrue(original.delete())
        val copy = store().fileFor(staged.fileName)
        assertTrue(copy.exists())
        assertEquals("receipt-bytes", copy.readText())
        assertEquals(dir.absolutePath, copy.parentFile!!.absolutePath)
        val rows = db.attachmentDao().getAll()
        assertEquals(1, rows.size)
        assertEquals(OwnerType.SERVICE, rows[0].ownerType)
        assertEquals(7L, rows[0].ownerId)
        assertEquals("image/jpeg", rows[0].mimeType)
        assertEquals("receipt-bytes".length.toLong(), rows[0].sizeBytes)
    }

    @Test
    fun pdfKeepsItsExtension() = runBlocking {
        val result = store().stage(source("policy.pdf").absolutePath) as StageResult.Staged
        assertTrue(result.attachment.fileName.endsWith(".pdf"))
        assertEquals("application/pdf", result.attachment.mimeType)
    }

    @Test
    fun the30thAttachmentIsAllowedAndThe31stIsBlockedForFree() = runBlocking {
        val store = store()
        val src = source().absolutePath
        repeat(29) { assertTrue(store.add(src, OwnerType.EXPENSE, 1) is StageResult.Staged) }
        // The 30th still fits.
        assertTrue(store.add(src, OwnerType.EXPENSE, 1) is StageResult.Staged)
        assertEquals(30, db.attachmentDao().count())

        val filesBefore = dir.list()!!.size
        assertEquals(StageResult.CapReached, store.add(src, OwnerType.EXPENSE, 1))
        assertEquals(30, db.attachmentDao().count())
        assertEquals("nothing is copied at the cap", filesBefore, dir.list()!!.size)
    }

    @Test
    fun stagedFilesCountTowardsTheCap() = runBlocking {
        val store = store()
        val src = source().absolutePath
        repeat(29) { store.add(src, OwnerType.DOCUMENT, 1) }
        assertTrue(store.stage(src, pending = 0) is StageResult.Staged)
        assertEquals(StageResult.CapReached, store.stage(src, pending = 1))
    }

    @Test
    fun proIsUnlimited() = runBlocking {
        pro = true
        val store = store()
        val src = source().absolutePath
        repeat(35) { assertTrue(store.add(src, OwnerType.SERVICE, 1) is StageResult.Staged) }
        assertEquals(35, db.attachmentDao().count())
    }

    @Test
    fun capCountsEveryOwnerType() = runBlocking {
        val store = store()
        val src = source().absolutePath
        repeat(10) { store.add(src, OwnerType.SERVICE, 1) }
        repeat(10) { store.add(src, OwnerType.EXPENSE, 2) }
        repeat(10) { store.add(src, OwnerType.DOCUMENT, 3) }
        assertEquals(StageResult.CapReached, store.add(src, OwnerType.ENERGY, 4))
    }

    @Test
    fun unreadableSourceFailsWithoutLeavingAFile() = runBlocking {
        val result = store().stage(File(temp.root, "missing.jpg").absolutePath)
        assertEquals(StageResult.Failed, result)
        assertEquals(0, db.attachmentDao().count())
        assertTrue(dir.list().orEmpty().isEmpty())
    }

    @Test
    fun discardAndDeleteRemoveThePrivateFile() = runBlocking {
        val store = store()
        val staged = (store.stage(source().absolutePath) as StageResult.Staged).attachment
        assertTrue(store.fileFor(staged.fileName).exists())
        store.discard(staged)
        assertFalse(store.fileFor(staged.fileName).exists())

        store.add(source("b.jpg").absolutePath, OwnerType.SERVICE, 1)
        val row = db.attachmentDao().getAll().single()
        store.delete(row)
        assertEquals(0, db.attachmentDao().count())
        assertFalse(store.fileFor(row.fileName).exists())
    }

    @Test
    fun limitHelper() {
        assertTrue(AttachmentLimit.canAdd(29, isPro = false))
        assertFalse(AttachmentLimit.canAdd(30, isPro = false))
        assertTrue(AttachmentLimit.canAdd(500, isPro = true))
    }
}
