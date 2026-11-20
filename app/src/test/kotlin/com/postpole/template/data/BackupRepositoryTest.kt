package com.postpole.template.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: BackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = BackupRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun export(): String = ByteArrayOutputStream().also { runBlocking { repo.exportJson(it, now = 1) } }
        .toString(Charsets.UTF_8)

    private suspend fun importText(text: String) = repo.importJson(ByteArrayInputStream(text.toByteArray()))

    @Test
    fun roundTrip_restoresAllItemsExactly() = runBlocking {
        val dao = db.itemDao()
        dao.insert(Item(title = "plain", createdAt = 10))
        dao.insert(Item(title = "unicode ₹ 日本語 \"quoted\"\nnewline", createdAt = 20))
        val before = dao.getAll()
        val json = export()

        dao.deleteAll()
        dao.insert(Item(title = "other data that must be replaced", createdAt = 99))
        val count = importText(json)

        assertEquals(2, count)
        assertEquals(before, dao.getAll())
    }

    @Test
    fun roundTrip_emptyDatabase() = runBlocking {
        val json = export()
        db.itemDao().insert(Item(title = "x", createdAt = 1))
        assertEquals(0, importText(json))
        assertEquals(emptyList<Item>(), db.itemDao().getAll())
    }

    @Test
    fun export_isVersionedAndNamed() {
        val json = export()
        assertEquals(true, json.contains("\"format\": \"postpole-backup\""))
        assertEquals(true, json.contains("\"version\": 1"))
    }

    @Test
    fun invalidImport_leavesExistingDataUntouched() = runBlocking {
        db.itemDao().insert(Item(title = "keep", createdAt = 1))
        listOf(
            "not json",
            """{"format":"other","version":1,"items":[]}""",
            """{"format":"postpole-backup","version":999,"items":[]}""",
            """{"format":"postpole-backup","version":1,"items":[{"id":1,"title":"a"}]}""",
            """{"format":"postpole-backup","version":1,"items":[{"id":1,"title":"a","createdAt":1},{"id":1,"title":"b","createdAt":2}]}""",
        ).forEach { bad ->
            assertThrows(InvalidBackupException::class.java) { runBlocking { importText(bad) } }
        }
        assertEquals(listOf("keep"), db.itemDao().getAll().map { it.title })
    }

    @Test
    fun csv_hasHeaderEscapesAndNeutralisesFormulas() = runBlocking {
        val dao = db.itemDao()
        dao.insert(Item(title = "a,b", createdAt = 1))
        dao.insert(Item(title = "say \"hi\"", createdAt = 2))
        dao.insert(Item(title = "=SUM(A1)", createdAt = 3))
        val out = ByteArrayOutputStream()
        assertEquals(3, repo.exportItemsCsv(out))
        assertEquals(
            "id,title,createdAt\r\n1,\"a,b\",1\r\n2,\"say \"\"hi\"\"\",2\r\n3,'=SUM(A1),3\r\n",
            out.toString(Charsets.UTF_8),
        )
    }
}
