package com.postpole.wheelfile.domain.document

import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.domain.entry.FieldError

/** Form state of the document form. Dates are epoch millis at the start of the chosen local day. */
data class DocumentFormState(
    val type: DocumentType = DocumentType.INSURANCE,
    val title: String = "",
    val number: String = "",
    val issueDate: Long? = null,
    val expiryDate: Long? = null,
    val alertDays: List<Int> = DocumentAlerts.DEFAULT_DAYS,
    val note: String = "",
)

enum class DocumentField { TITLE, EXPIRY }

data class DocumentValidation(val errors: Map<DocumentField, FieldError>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object DocumentForm {
    /** Alert days the user can switch on or off; the default set is on. */
    val ALERT_CHOICES: List<Int> = listOf(60, 30, 14, 7, 1)

    fun validate(state: DocumentFormState): DocumentValidation {
        val errors = linkedMapOf<DocumentField, FieldError>()
        if (state.title.isBlank()) errors[DocumentField.TITLE] = FieldError.REQUIRED
        val issue = state.issueDate
        val expiry = state.expiryDate
        if (issue != null && expiry != null && expiry < issue) errors[DocumentField.EXPIRY] = FieldError.INVALID
        return DocumentValidation(errors)
    }

    fun toggleAlertDay(state: DocumentFormState, day: Int): DocumentFormState {
        val days = if (day in state.alertDays) state.alertDays - day else state.alertDays + day
        return state.copy(alertDays = DocumentAlerts.normalise(days))
    }

    fun fromDocument(doc: VehicleDocument) = DocumentFormState(
        type = doc.type,
        title = doc.title,
        number = doc.number.orEmpty(),
        issueDate = doc.issueDate,
        expiryDate = doc.expiryDate,
        alertDays = doc.alertDays,
        note = doc.note.orEmpty(),
    )

    /** Builds the entity. Call only when [validate] returned no errors. [id] is 0 for a new document. */
    fun toDocument(state: DocumentFormState, vehicleId: Long, id: Long = 0): VehicleDocument = VehicleDocument(
        id = id,
        vehicleId = vehicleId,
        type = state.type,
        title = state.title.trim().take(80),
        number = state.number.trim().ifEmpty { null }?.take(60),
        issueDate = state.issueDate,
        expiryDate = state.expiryDate,
        alertDays = DocumentAlerts.normalise(state.alertDays),
        note = state.note.trim().ifEmpty { null }?.take(500),
    )
}
