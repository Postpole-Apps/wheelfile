package com.postpole.wheelfile.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM Vehicle ORDER BY archived ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Vehicle>>

    @Query("SELECT * FROM Vehicle ORDER BY archived ASC, name COLLATE NOCASE ASC")
    suspend fun getAll(): List<Vehicle>

    @Query("SELECT * FROM Vehicle WHERE id = :id")
    fun observe(id: Long): Flow<Vehicle?>

    @Query("SELECT * FROM Vehicle WHERE id = :id")
    suspend fun get(id: Long): Vehicle?

    /** Free-tier cap input (spec §4.1): active vehicles only. */
    @Query("SELECT COUNT(*) FROM Vehicle WHERE archived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM Vehicle WHERE archived = 0")
    suspend fun activeCount(): Int

    @Insert
    suspend fun insert(vehicle: Vehicle): Long

    @Update
    suspend fun update(vehicle: Vehicle)

    @Delete
    suspend fun delete(vehicle: Vehicle)
}

@Dao
interface EnergyLogDao {
    @Query("SELECT * FROM EnergyLog WHERE vehicleId = :vehicleId ORDER BY odometer DESC, dateTime DESC, id DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<EnergyLog>>

    /** Oldest first, the order the economy engine expects (odometer, then date). */
    @Query("SELECT * FROM EnergyLog WHERE vehicleId = :vehicleId AND stream = :stream ORDER BY odometer ASC, dateTime ASC, id ASC")
    suspend fun getForStream(vehicleId: Long, stream: EnergyStream): List<EnergyLog>

    @Query("SELECT * FROM EnergyLog WHERE vehicleId = :vehicleId ORDER BY odometer ASC, dateTime ASC, id ASC")
    suspend fun getAll(vehicleId: Long): List<EnergyLog>

    @Query("SELECT * FROM EnergyLog WHERE id = :id")
    suspend fun get(id: Long): EnergyLog?

    @Query("SELECT COUNT(*) FROM EnergyLog WHERE vehicleId = :vehicleId AND importKey = :importKey")
    suspend fun countByImportKey(vehicleId: Long, importKey: String): Int

    @Insert
    suspend fun insert(log: EnergyLog): Long

    @Insert
    suspend fun insertAll(logs: List<EnergyLog>)

    @Update
    suspend fun update(log: EnergyLog)

    @Delete
    suspend fun delete(log: EnergyLog)
}

@Dao
interface ServiceRecordDao {
    @Query("SELECT * FROM ServiceRecord WHERE vehicleId = :vehicleId ORDER BY dateTime DESC, id DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<ServiceRecord>>

    @Query("SELECT * FROM ServiceRecord WHERE vehicleId = :vehicleId ORDER BY dateTime ASC, id ASC")
    suspend fun getForVehicle(vehicleId: Long): List<ServiceRecord>

    @Insert
    suspend fun insert(record: ServiceRecord): Long

    @Update
    suspend fun update(record: ServiceRecord)

    @Delete
    suspend fun delete(record: ServiceRecord)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM Expense WHERE vehicleId = :vehicleId ORDER BY dateTime DESC, id DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM Expense WHERE vehicleId = :vehicleId ORDER BY dateTime ASC, id ASC")
    suspend fun getForVehicle(vehicleId: Long): List<Expense>

    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM Reminder WHERE vehicleId = :vehicleId ORDER BY done ASC, dueDate ASC, id ASC")
    fun observeForVehicle(vehicleId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM Reminder WHERE done = 0")
    suspend fun getPending(): List<Reminder>

    @Query("SELECT * FROM Reminder ORDER BY done ASC, dueDate ASC, id ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM Reminder WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    /** The expiry reminder of a document (at most one). */
    @Query("SELECT * FROM Reminder WHERE linkedDocumentId = :documentId")
    suspend fun getForDocument(documentId: Long): List<Reminder>

    /** Every odometer reading across fill-ups, charges, services and expenses. Feeds the usage projection. */
    @Query(
        "SELECT vehicleId, dateTime, odometer FROM EnergyLog " +
            "UNION ALL SELECT vehicleId, dateTime, odometer FROM ServiceRecord WHERE odometer IS NOT NULL " +
            "UNION ALL SELECT vehicleId, dateTime, odometer FROM Expense WHERE odometer IS NOT NULL",
    )
    fun observeReadings(): Flow<List<com.postpole.wheelfile.domain.reminder.OdometerReading>>

    @Query(
        "SELECT vehicleId, dateTime, odometer FROM EnergyLog " +
            "UNION ALL SELECT vehicleId, dateTime, odometer FROM ServiceRecord WHERE odometer IS NOT NULL " +
            "UNION ALL SELECT vehicleId, dateTime, odometer FROM Expense WHERE odometer IS NOT NULL",
    )
    suspend fun getReadings(): List<com.postpole.wheelfile.domain.reminder.OdometerReading>

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)
}

@Dao
interface VehicleDocumentDao {
    @Query("SELECT * FROM VehicleDocument WHERE vehicleId = :vehicleId ORDER BY expiryDate ASC, id ASC")
    fun observeForVehicle(vehicleId: Long): Flow<List<VehicleDocument>>

    @Query("SELECT * FROM VehicleDocument WHERE id = :id")
    fun observe(id: Long): Flow<VehicleDocument?>

    @Query("SELECT * FROM VehicleDocument WHERE id = :id")
    suspend fun get(id: Long): VehicleDocument?

    @Query("SELECT * FROM VehicleDocument")
    suspend fun getAll(): List<VehicleDocument>

    @Insert
    suspend fun insert(document: VehicleDocument): Long

    @Update
    suspend fun update(document: VehicleDocument)

    @Delete
    suspend fun delete(document: VehicleDocument)
}

@Dao
interface AttachmentDao {
    /** Free-tier cap input (spec §5.1): total attachment rows. */
    @Query("SELECT COUNT(*) FROM Attachment")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM Attachment")
    suspend fun count(): Int

    @Query("SELECT * FROM Attachment ORDER BY id ASC")
    suspend fun getAll(): List<Attachment>

    @Query("SELECT * FROM Attachment WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt ASC, id ASC")
    fun observeForOwner(ownerType: OwnerType, ownerId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM Attachment WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt ASC, id ASC")
    suspend fun getForOwner(ownerType: OwnerType, ownerId: Long): List<Attachment>

    @Query("SELECT * FROM Attachment WHERE ownerType = :ownerType")
    fun observeForType(ownerType: OwnerType): Flow<List<Attachment>>

    /** Receipts attached to this vehicle's fill-ups, charges, services and expenses (the Documents tab "Receipts" card). */
    @Query(
        "SELECT COUNT(*) FROM Attachment WHERE " +
            "(ownerType = 'ENERGY' AND ownerId IN (SELECT id FROM EnergyLog WHERE vehicleId = :vehicleId)) OR " +
            "(ownerType = 'SERVICE' AND ownerId IN (SELECT id FROM ServiceRecord WHERE vehicleId = :vehicleId)) OR " +
            "(ownerType = 'EXPENSE' AND ownerId IN (SELECT id FROM Expense WHERE vehicleId = :vehicleId))",
    )
    fun observeReceiptCount(vehicleId: Long): Flow<Int>

    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Delete
    suspend fun delete(attachment: Attachment)
}

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM CustomCategory ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CustomCategory>>

    @Insert
    suspend fun insert(category: CustomCategory): Long

    @Delete
    suspend fun delete(category: CustomCategory)
}
