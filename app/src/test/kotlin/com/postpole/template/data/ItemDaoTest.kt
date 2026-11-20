package com.postpole.template.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ItemDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ItemDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.itemDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_and_observe_returnsNewestFirst() = runBlocking {
        dao.insert(Item(title = "old", createdAt = 1))
        dao.insert(Item(title = "new", createdAt = 2))
        assertEquals(listOf("new", "old"), dao.observeAll().first().map { it.title })
    }

    @Test
    fun delete_removesItem() = runBlocking {
        val id = dao.insert(Item(title = "a", createdAt = 1))
        dao.delete(Item(id = id, title = "a", createdAt = 1))
        assertEquals(emptyList<Item>(), dao.observeAll().first())
    }
}
