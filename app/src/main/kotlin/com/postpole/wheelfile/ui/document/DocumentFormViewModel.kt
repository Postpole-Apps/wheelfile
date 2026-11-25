package com.postpole.wheelfile.ui.document

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.DocumentRepository
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.attachments.AttachmentLimit
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.attachments.StageResult
import com.postpole.wheelfile.data.attachments.StagedAttachment
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.domain.document.DocumentError
import com.postpole.wheelfile.domain.document.DocumentField
import com.postpole.wheelfile.domain.document.DocumentForm
import com.postpole.wheelfile.domain.document.DocumentFormState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentFormUiState(
    val vehicle: Vehicle? = null,
    /** Set when editing an existing document. */
    val editingId: Long? = null,
    val form: DocumentFormState = DocumentFormState(),
    val errors: Map<DocumentField, DocumentError> = emptyMap(),
    val showErrors: Boolean = false,
    /** Files already saved with the document that the user has not removed. */
    val existing: List<Attachment> = emptyList(),
    val attachments: List<StagedAttachment> = emptyList(),
    /** True when the next attachment would be the 31st on the free tier. */
    val attachmentCapReached: Boolean = false,
    val saving: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface DocumentFormEvent {
    data class Saved(val promptNotifications: Boolean) : DocumentFormEvent
    data object SaveFailed : DocumentFormEvent
    data object AttachmentFailed : DocumentFormEvent
    data object OpenProGate : DocumentFormEvent
}

/** Add and edit a document. Attachments count towards the free cap of 30 like every other attachment. 0 ads. */
@HiltViewModel
class DocumentFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val vehicles: VehicleDao,
    private val documents: DocumentRepository,
    private val attachmentStore: AttachmentStore,
    attachmentDao: AttachmentDao,
    private val settings: SettingsRepository,
    private val analytics: Analytics,
    billing: BillingRepository,
) : ViewModel() {
    private val vehicleId: Long = checkNotNull(savedState.get<Long>("vehicleId"))
    private val documentId: Long? = savedState.get<Long>("documentId")?.takeIf { it > 0 }
    private val isPro = billing.isPro

    private val _state = MutableStateFlow(DocumentFormUiState(editingId = documentId))
    val state: StateFlow<DocumentFormUiState> = _state.asStateFlow()

    private val _events = Channel<DocumentFormEvent>(Channel.BUFFERED)
    val events: Flow<DocumentFormEvent> = _events.receiveAsFlow()

    private var attachmentCount = 0
    private var removed = emptyList<Attachment>()
    private var saved = false

    init {
        viewModelScope.launch {
            val vehicle = vehicles.get(vehicleId)
            val doc = documentId?.let { documents.get(it) }
            val files = documentId?.let { documents.observeAttachments(it).first() }.orEmpty()
            _state.update {
                it.copy(
                    vehicle = vehicle,
                    form = doc?.let(DocumentForm::fromDocument) ?: it.form,
                    existing = files,
                    loaded = true,
                )
            }
            refreshCap()
        }
        viewModelScope.launch {
            attachmentDao.observeCount().collect {
                attachmentCount = it
                refreshCap()
            }
        }
        viewModelScope.launch { isPro.collect { refreshCap() } }
    }

    private fun refreshCap() = _state.update {
        // Files marked for removal are given back when the form is saved, so they leave the count now.
        it.copy(attachmentCapReached = !AttachmentLimit.canAdd(attachmentCount + it.attachments.size - removed.size, isPro.value))
    }

    private fun update(block: (DocumentFormState) -> DocumentFormState) {
        _state.update {
            val form = block(it.form)
            it.copy(form = form, errors = DocumentForm.validate(form))
        }
    }

    /** [defaultTitle] fills the title only while it is still blank. */
    fun setType(type: DocumentType, defaultTitle: String) = update {
        it.copy(type = type, title = if (it.title.isBlank()) defaultTitle else it.title)
    }

    fun setTitle(t: String) = update { it.copy(title = t.take(80)) }
    fun setNumber(t: String) = update { it.copy(number = t.take(40)) }
    fun setIssue(epochMillis: Long?) = update { it.copy(issueDate = epochMillis) }
    fun setExpiry(epochMillis: Long?) = update { it.copy(expiryDate = epochMillis) }
    fun setNote(t: String) = update { it.copy(note = t.take(500)) }
    fun toggleAlert(days: Int) = update { it.copy(alertDays = if (days in it.alertDays) it.alertDays - days else it.alertDays + days) }

    fun addAttachment(source: String) {
        viewModelScope.launch {
            when (val result = attachmentStore.stage(source, pending = _state.value.attachments.size - removed.size)) {
                is StageResult.Staged -> {
                    _state.update { it.copy(attachments = it.attachments + result.attachment) }
                    refreshCap()
                }
                StageResult.CapReached -> openProGate()
                StageResult.Failed -> _events.send(DocumentFormEvent.AttachmentFailed)
            }
        }
    }

    fun removeStaged(staged: StagedAttachment) {
        _state.update { it.copy(attachments = it.attachments - staged) }
        refreshCap()
        viewModelScope.launch { attachmentStore.discard(staged) }
    }

    /** Marks a saved file for removal; it is deleted when the form is saved. */
    fun removeExisting(attachment: Attachment) {
        removed = removed + attachment
        _state.update { it.copy(existing = it.existing - attachment) }
        refreshCap()
    }

    fun openProGate() {
        analytics.logEvent(AnalyticsEvents.ATTACHMENT_CAP_REACHED)
        _events.trySend(DocumentFormEvent.OpenProGate)
    }

    fun reportAttachmentFailure() {
        _events.trySend(DocumentFormEvent.AttachmentFailed)
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.vehicle == null || s.vehicle.archived) return
        val errors = DocumentForm.validate(s.form)
        _state.update { it.copy(errors = errors, showErrors = true) }
        if (errors.isNotEmpty()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val doc = DocumentForm.toDocument(s.form, vehicleId, documentId ?: 0L)
                // One transaction: a failure leaves no partial rows, and the staged files stay for a retry.
                documents.saveWithAttachments(doc, staged = s.attachments, removed = removed)
                removed = emptyList()
                saved = true
                analytics.logEvent(AnalyticsEvents.DOCUMENT_SAVED)
                var prompt = false
                if (doc.expiryDate != null && doc.alertDays.isNotEmpty() && !settings.notificationPrompted.first()) {
                    settings.setNotificationPrompted()
                    prompt = true
                }
                _events.send(DocumentFormEvent.Saved(prompt))
            } catch (e: Exception) {
                analytics.recordNonFatal(e)
                _state.update { it.copy(saving = false) }
                _events.send(DocumentFormEvent.SaveFailed)
            }
        }
    }

    override fun onCleared() {
        // Staged copies of a form that was closed without saving are not kept.
        if (!saved) _state.value.attachments.forEach { attachmentStore.discardNow(it) }
    }
}
