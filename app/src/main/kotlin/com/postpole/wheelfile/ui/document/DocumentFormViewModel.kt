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
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.domain.document.DocumentForm
import com.postpole.wheelfile.domain.document.DocumentFormState
import com.postpole.wheelfile.domain.document.DocumentValidation
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
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class DocumentFormUiState(
    val vehicle: Vehicle? = null,
    /** Null while a new document is being created. */
    val documentId: Long? = null,
    val form: DocumentFormState = DocumentFormState(),
    val validation: DocumentValidation = DocumentValidation(emptyMap()),
    val showErrors: Boolean = false,
    /** Files already saved on the document (editing). */
    val saved: List<Attachment> = emptyList(),
    /** Files copied into private storage whose document row is not saved yet. */
    val staged: List<StagedAttachment> = emptyList(),
    val attachmentCapReached: Boolean = false,
    val saving: Boolean = false,
) {
    val isEditing: Boolean get() = documentId != null
}

sealed interface DocumentFormEvent {
    data class Saved(val promptNotifications: Boolean) : DocumentFormEvent
    data object SaveFailed : DocumentFormEvent
    data object AttachmentFailed : DocumentFormEvent
    data object OpenProGate : DocumentFormEvent
}

/** The document form (add and edit). 0 ads. Attachments count towards the free cap of 30 like every other attachment. */
@HiltViewModel
class DocumentFormViewModel internal constructor(
    private val vehicles: VehicleDao,
    private val repo: DocumentRepository,
    private val attachmentStore: AttachmentStore,
    attachmentDao: AttachmentDao,
    private val settings: SettingsRepository,
    private val analytics: Analytics,
    private val isPro: StateFlow<Boolean>,
    private val vehicleId: Long,
    private val editId: Long?,
) : ViewModel() {

    @Inject
    constructor(
        savedState: SavedStateHandle,
        vehicles: VehicleDao,
        repo: DocumentRepository,
        attachmentStore: AttachmentStore,
        attachmentDao: AttachmentDao,
        settings: SettingsRepository,
        analytics: Analytics,
        billing: BillingRepository,
    ) : this(
        vehicles, repo, attachmentStore, attachmentDao, settings, analytics, billing.isPro,
        vehicleId = checkNotNull(savedState.get<Long>("vehicleId")),
        editId = savedState.get<Long>("documentId")?.takeIf { it > 0 },
    )

    private val _state = MutableStateFlow(DocumentFormUiState(documentId = editId))
    val state: StateFlow<DocumentFormUiState> = _state.asStateFlow()

    private val _events = Channel<DocumentFormEvent>(Channel.BUFFERED)
    val events: Flow<DocumentFormEvent> = _events.receiveAsFlow()

    private var attachmentCount = 0
    private var committed = false

    init {
        viewModelScope.launch {
            val vehicle = vehicles.get(vehicleId) ?: return@launch
            val existing = editId?.let { repo.get(it) }
            _state.update { s ->
                s.copy(vehicle = vehicle, form = existing?.let(DocumentForm::fromDocument) ?: s.form)
            }
            recompute()
        }
        if (editId != null) {
            viewModelScope.launch { repo.observeAttachments(editId).collect { list -> _state.update { it.copy(saved = list) } } }
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
        it.copy(attachmentCapReached = !AttachmentLimit.canAdd(attachmentCount + it.staged.size, isPro.value))
    }

    private fun update(block: (DocumentFormState) -> DocumentFormState) {
        _state.update { it.copy(form = block(it.form)) }
        recompute()
    }

    private fun recompute() = _state.update { it.copy(validation = DocumentForm.validate(it.form)) }

    fun setType(t: DocumentType) = update { it.copy(type = t) }
    fun setTitle(t: String) = update { it.copy(title = t.take(80)) }
    fun setNumber(t: String) = update { it.copy(number = t.take(60)) }
    fun setNote(t: String) = update { it.copy(note = t.take(500)) }
    fun toggleAlertDay(day: Int) = update { DocumentForm.toggleAlertDay(it, day) }

    fun setIssueDate(epochMillis: Long) = update { it.copy(issueDate = startOfDay(epochMillis)) }
    fun setExpiryDate(epochMillis: Long) = update { it.copy(expiryDate = startOfDay(epochMillis)) }
    fun clearExpiry() = update { it.copy(expiryDate = null) }

    private fun startOfDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** A picked photo, captured photo or PDF. At the free cap this opens the Pro gate instead of copying. */
    fun addAttachment(source: String) {
        viewModelScope.launch {
            when (val result = attachmentStore.stage(source, pending = _state.value.staged.size)) {
                is StageResult.Staged -> {
                    _state.update { it.copy(staged = it.staged + result.attachment) }
                    refreshCap()
                }
                StageResult.CapReached -> openProGate()
                StageResult.Failed -> _events.send(DocumentFormEvent.AttachmentFailed)
            }
        }
    }

    fun removeStaged(staged: StagedAttachment) {
        _state.update { it.copy(staged = it.staged - staged) }
        refreshCap()
        viewModelScope.launch { attachmentStore.discard(staged) }
    }

    fun removeSaved(attachment: Attachment) {
        viewModelScope.launch { repo.removeAttachment(attachment) }
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
        val vehicle = s.vehicle ?: return
        if (s.saving || vehicle.archived) return
        val validation = DocumentForm.validate(s.form)
        _state.update { it.copy(validation = validation, showErrors = true) }
        if (!validation.isValid) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val id = repo.save(DocumentForm.toDocument(s.form, vehicle.id, editId ?: 0L))
                s.staged.forEach { attachmentStore.commit(it, OwnerType.DOCUMENT, id) }
                committed = true
                analytics.logEvent(AnalyticsEvents.DOCUMENT_SAVED, mapOf("type" to s.form.type.name.lowercase()))
                var prompt = false
                if (s.form.expiryDate != null && !settings.notificationPrompted.first()) {
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
        if (!committed) _state.value.staged.forEach { attachmentStore.discardNow(it) }
    }
}
