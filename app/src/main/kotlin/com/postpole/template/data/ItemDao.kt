package com.postpole.template.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items ORDER BY id ASC")
    suspend fun getAll(): List<Item>

    @Insert
    suspend fun insert(item: Item): Long

    @Insert
    suspend fun insertAll(items: List<Item>)

    @Query("DELETE FROM items")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(item: Item)
}
