package com.postpole.wheelfile.data

import androidx.room.withTransaction

/**
 * Runs [block] as one database transaction: if it throws, every row written inside is rolled back. Files are not part
 * of a transaction, so callers write files before it (and discard them if it fails) or delete them only after it commits.
 */
interface Transactor {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactor(private val db: AppDatabase) : Transactor {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
