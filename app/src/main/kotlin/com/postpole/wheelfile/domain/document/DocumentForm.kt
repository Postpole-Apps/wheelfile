package com.postpole.wheelfile.domain.document

import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.VehicleDocument

data class DocumentFormState(
    val type: DocumentType = DocumentType.INSURANCE,
    val title: String = "",
    val number: String = "",
    /** Epoch millis at the start of the day. */
    val issueDate: Long? = null,
    val expiryDate: Long? = null,
    val alertDays: Set<Int> = DocumentAlerts.DEFAULT_DAYS.toSet(),
    val note: String = "",
)

enum class DocumentField { TITLE, EXPIRY }

enum class DocumentError { REQUIRED, EXPIRY_BEFORE_ISSUE }

object DocumentForm {
    fun validate(state: DocumentFormState): Map<DocumentField, DocumentError> {
        val errors = linkedMapOf<DocumentField, DocumentError>()
        if (state.title.isBlank()) errors[DocumentField.TITLE] = DocumentError.REQUIRED
        val issue = state.issueDate
        val expiry = state.expiryDate
        if (issue != null && expiry != null && expiry < issue) errors[DocumentField.EXPIRY] = DocumentError.EXPIRY_BEFORE_ISSUE
        return errors
    }

    /** Builds the entity. Call only when [validate] found nothing. Alerts are kept only when there is an expiry date. */
    fun toDocument(state: DocumentFormState, vehicleId: Long, id: Long = 0): VehicleDocument = VehicleDocument(
        id = id,
        vehicleId = vehicleId,
        type = state.type,
        title = state.title.trim().take(80),
        number = state.number.trim().take(40).ifEmpty { null },
        issueDate = state.issueDate,
        expiryDate = state.expiryDate,
        alertDays = if (state.expiryDate == null) emptyList() else state.alertDays.sortedDescending(),
        note = state.note.trim().take(500).ifEmpty { null },
    )

    fun fromDocument(doc: VehicleDocument) = DocumentFormState(
        type = doc.type,
        title = doc.title,
        number = doc.number.orEmpty(),
        issueDate = doc.issueDate,
        expiryDate = doc.expiryDate,
        // A document saved without an expiry date has no alerts yet: offer the defaults once a date is added.
        alertDays = if (doc.expiryDate == null && doc.alertDays.isEmpty()) DocumentAlerts.DEFAULT_DAYS.toSet() else doc.alertDays.toSet(),
        note = doc.note.orEmpty(),
    )
}
