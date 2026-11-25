package com.postpole.wheelfile.data

import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyLogDao
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseDao
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.ReminderDao
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.ServiceRecordDao
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleDocumentDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that deletes vehicles, entries and documents. Attachment rows are not foreign-keyed (one table serves
 * several owner types), so every delete removes the owner's attachment rows in the same transaction, and the files
 * only after that transaction has committed: a failure can never leave a half-deleted record, and orphans never count
 * towards the free cap of 30.
 */
@Singleton
class RecordDeleter @Inject constructor(
    private val transactor: Transactor,
    private val attachments: AttachmentDao,
    private val vehicles: VehicleDao,
    private val energyLogs: EnergyLogDao,
    private val services: ServiceRecordDao,
    private val expenses: ExpenseDao,
    private val documents: VehicleDocumentDao,
    private val reminders: ReminderDao,
    private val store: AttachmentStore,
) {
    /** Deletes the vehicle; foreign keys cascade to its entries, documents and reminders, and this to their attachments. */
    suspend fun deleteVehicle(vehicle: Vehicle) = withFiles {
        val rows = attachments.getForVehicle(vehicle.id)
        removeRows(rows)
        vehicles.delete(vehicle)
        rows
    }

    suspend fun deleteEnergyLog(log: EnergyLog) = withFiles {
        val rows = attachments.getForOwner(OwnerType.ENERGY, log.id)
        removeRows(rows)
        energyLogs.delete(log)
        rows
    }

    suspend fun deleteService(record: ServiceRecord) = withFiles {
        val rows = attachments.getForOwner(OwnerType.SERVICE, record.id)
        removeRows(rows)
        services.delete(record)
        rows
    }

    suspend fun deleteExpense(expense: Expense) = withFiles {
        val rows = attachments.getForOwner(OwnerType.EXPENSE, expense.id)
        removeRows(rows)
        expenses.delete(expense)
        rows
    }

    /** Deletes the document, its attachment rows and its linked expiry reminder as one unit. */
    suspend fun deleteDocument(document: VehicleDocument) = withFiles {
        val rows = attachments.getForOwner(OwnerType.DOCUMENT, document.id)
        removeRows(rows)
        reminders.getForDocument(document.id).forEach { reminders.delete(it) }
        documents.delete(document)
        rows
    }

    /** Runs [body] in a transaction, then deletes the files of the rows it returns (only reached on commit). */
    private suspend fun withFiles(body: suspend () -> List<Attachment>) {
        val rows = transactor.run(body)
        store.deleteFiles(rows.map { it.fileName })
    }

    private suspend fun removeRows(rows: List<Attachment>) {
        // Chunked to stay under SQLite's bound-variable limit.
        rows.map { it.id }.chunked(CHUNK).forEach { attachments.deleteByIds(it) }
    }

    private companion object {
        const val CHUNK = 500
    }
}
