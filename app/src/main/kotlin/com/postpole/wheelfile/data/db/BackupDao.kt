package com.postpole.wheelfile.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Whole-table reads, wipes and id-preserving inserts for backup, restore and export. Not for screens. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM Vehicle ORDER BY id") suspend fun vehicles(): List<Vehicle>
    @Query("SELECT * FROM EnergyLog ORDER BY id") suspend fun energyLogs(): List<EnergyLog>
    @Query("SELECT * FROM ServiceRecord ORDER BY id") suspend fun serviceRecords(): List<ServiceRecord>
    @Query("SELECT * FROM Expense ORDER BY id") suspend fun expenses(): List<Expense>
    @Query("SELECT * FROM Reminder ORDER BY id") suspend fun reminders(): List<Reminder>
    @Query("SELECT * FROM VehicleDocument ORDER BY id") suspend fun documents(): List<VehicleDocument>
    @Query("SELECT * FROM Attachment ORDER BY id") suspend fun attachments(): List<Attachment>
    @Query("SELECT * FROM CustomCategory ORDER BY id") suspend fun customCategories(): List<CustomCategory>

    // Deleting vehicles cascades to their logs, services, expenses, reminders and documents.
    @Query("DELETE FROM Vehicle") suspend fun deleteVehicles()
    @Query("DELETE FROM Attachment") suspend fun deleteAttachments()
    @Query("DELETE FROM CustomCategory") suspend fun deleteCustomCategories()

    @Insert suspend fun insertVehicles(rows: List<Vehicle>)
    @Insert suspend fun insertEnergyLogs(rows: List<EnergyLog>)
    @Insert suspend fun insertServiceRecords(rows: List<ServiceRecord>)
    @Insert suspend fun insertExpenses(rows: List<Expense>)
    @Insert suspend fun insertReminders(rows: List<Reminder>)
    @Insert suspend fun insertDocuments(rows: List<VehicleDocument>)
    @Insert suspend fun insertAttachments(rows: List<Attachment>)
    @Insert suspend fun insertCustomCategories(rows: List<CustomCategory>)
}
