package com.postpole.wheelfile.data

import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderDao
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleDocumentDao
import com.postpole.wheelfile.domain.document.DocumentAlerts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** A document with the number of files attached to it, for the list cards. */
data class DocumentItem(val document: VehicleDocument, val attachmentCount: Int)

/**
 * Documents of a vehicle. A document with an expiry date owns one reminder (`linkedDocumentId`), so expiry alerts run
 * through the same daily worker, quiet hours and notification channel as every other reminder (docs/spec.md §2.5).
 */
@Singleton
class DocumentRepository @Inject constructor(
    private val documents: VehicleDocumentDao,
    private val reminders: ReminderDao,
    private val attachments: AttachmentDao,
    private val store: AttachmentStore,
) {
    fun observeItems(vehicleId: Long): Flow<List<DocumentItem>> =
        combine(documents.observeForVehicle(vehicleId), attachments.observeForOwnerType(OwnerType.DOCUMENT)) { docs, files ->
            val counts = files.groupingBy { it.ownerId }.eachCount()
            docs.map { DocumentItem(it, counts[it.id] ?: 0) }
        }

    fun observe(id: Long): Flow<VehicleDocument?> = documents.observe(id)

    fun observeAttachments(documentId: Long): Flow<List<Attachment>> = attachments.observeForOwner(OwnerType.DOCUMENT, documentId)

    fun observeReceiptCount(vehicleId: Long): Flow<Int> = attachments.observeReceiptCount(vehicleId)

    suspend fun get(id: Long): VehicleDocument? = documents.get(id)

    /** Inserts or updates [document] (id 0 inserts), then brings its expiry reminder in line. Returns the document id. */
    suspend fun save(document: VehicleDocument, presetCode: String? = null): Long {
        val id = if (document.id == 0L) documents.insert(document) else document.id.also { documents.update(document) }
        syncReminder(document.copy(id = id), presetCode)
        return id
    }

    /** Deletes the document, its reminder and its attachment files. */
    suspend fun delete(document: VehicleDocument) {
        attachments.getForOwner(OwnerType.DOCUMENT, document.id).forEach { store.delete(it) }
        reminders.deleteForDocument(document.id)
        documents.delete(document)
    }

    suspend fun removeAttachment(attachment: Attachment) = store.delete(attachment)

    private suspend fun syncReminder(document: VehicleDocument, presetCode: String?) {
        val existing = reminders.getForDocument(document.id)
        val expiry = document.expiryDate
        if (expiry == null) {
            if (existing.isNotEmpty()) reminders.deleteForDocument(document.id)
            return
        }
        val current = existing.firstOrNull()
        if (current == null) {
            reminders.insert(
                Reminder(
                    vehicleId = document.vehicleId,
                    title = document.title,
                    kind = ReminderKind.DATE,
                    dueDate = expiry,
                    leadDays = DocumentAlerts.leadDays(document.alertDays),
                    presetCode = presetCode,
                    linkedDocumentId = document.id,
                ),
            )
        } else {
            val moved = current.dueDate != expiry
            reminders.update(
                current.copy(
                    title = document.title,
                    dueDate = expiry,
                    leadDays = DocumentAlerts.leadDays(document.alertDays),
                    // A new expiry date starts the alerts over.
                    done = if (moved) false else current.done,
                    lastNotifiedAt = if (moved) null else current.lastNotifiedAt,
                ),
            )
        }
    }
}
