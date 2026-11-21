package com.postpole.wheelfile.data

import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderDao
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleDocumentDao
import com.postpole.wheelfile.domain.document.DocumentAlerts
import com.postpole.wheelfile.domain.document.PresetPlan
import com.postpole.wheelfile.domain.reminder.ReminderEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class DocumentItem(val document: VehicleDocument, val attachmentCount: Int)

/**
 * Documents of a vehicle. A document with an expiry date has one linked reminder (spec §2.5), so the expiry alerts at
 * 30 / 14 / 7 / 1 days run on the same daily check as every other reminder. Attachments are the same rows the free cap
 * of 30 counts, whichever entry or document they belong to.
 */
@Singleton
class DocumentRepository @Inject constructor(
    private val documents: VehicleDocumentDao,
    private val reminders: ReminderDao,
    private val attachments: AttachmentDao,
    private val store: AttachmentStore,
) {
    fun observeItems(vehicleId: Long): Flow<List<DocumentItem>> =
        combine(documents.observeForVehicle(vehicleId), attachments.observeForType(OwnerType.DOCUMENT)) { docs, files ->
            val counts = files.groupingBy { it.ownerId }.eachCount()
            docs.map { DocumentItem(it, counts[it.id] ?: 0) }
        }

    fun observe(id: Long): Flow<VehicleDocument?> = documents.observe(id)

    fun observeAttachments(documentId: Long): Flow<List<Attachment>> = attachments.observeForOwner(OwnerType.DOCUMENT, documentId)

    fun observeReceiptCount(vehicleId: Long): Flow<Int> = attachments.observeReceiptCount(vehicleId)

    suspend fun get(id: Long): VehicleDocument? = documents.get(id)

    /** Inserts or updates [document] and keeps its expiry reminder in step. Returns the document id. */
    suspend fun save(document: VehicleDocument, presetCode: String? = null): Long {
        val id = if (document.id == 0L) documents.insert(document) else document.id.also { documents.update(document) }
        syncReminder(document.copy(id = id), presetCode)
        return id
    }

    /** Deletes the document, its attachment files and its reminder. */
    suspend fun delete(document: VehicleDocument) {
        attachments.getForOwner(OwnerType.DOCUMENT, document.id).forEach { store.delete(it) }
        reminders.getForDocument(document.id).forEach { reminders.delete(it) }
        documents.delete(document)
    }

    /**
     * Creates the documents for the chosen presets. A preset whose type and title already exist for the vehicle is skipped.
     * Returns how many were created.
     */
    suspend fun applyPlans(vehicle: Vehicle, plans: List<PresetPlan>, zone: ZoneId = ZoneId.systemDefault()): Int {
        val existing = documents.getAll().filter { it.vehicleId == vehicle.id }
        var created = 0
        for (plan in plans) {
            val p = plan.preset
            if (existing.any { it.type == p.type && it.title.equals(p.title, ignoreCase = true) }) continue
            save(
                VehicleDocument(
                    vehicleId = vehicle.id,
                    type = p.type,
                    title = p.title,
                    expiryDate = plan.expiry?.let { ReminderEngine.dayStart(it, zone) },
                    alertDays = plan.alertDays,
                ),
                presetCode = p.code,
            )
            created++
        }
        return created
    }

    private suspend fun syncReminder(doc: VehicleDocument, presetCode: String?) {
        val existing = reminders.getForDocument(doc.id)
        val expiry = doc.expiryDate
        if (expiry == null || doc.alertDays.isEmpty()) {
            existing.forEach { reminders.delete(it) }
            return
        }
        val current = existing.firstOrNull()
        if (current == null) {
            reminders.insert(
                Reminder(
                    vehicleId = doc.vehicleId, title = doc.title, kind = ReminderKind.DATE, dueDate = expiry,
                    leadDays = DocumentAlerts.leadDays(doc.alertDays), presetCode = presetCode, linkedDocumentId = doc.id,
                ),
            )
            return
        }
        val dateChanged = current.dueDate != expiry
        reminders.update(
            current.copy(
                vehicleId = doc.vehicleId,
                title = doc.title,
                dueDate = expiry,
                leadDays = DocumentAlerts.leadDays(doc.alertDays),
                // A new expiry date is a renewal: alert again for it.
                done = if (dateChanged) false else current.done,
                lastNotifiedAt = if (dateChanged) null else current.lastNotifiedAt,
            ),
        )
    }
}
