package com.postpole.wheelfile.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.ProPromptCoordinator
import com.postpole.wheelfile.ads.SaveKind
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.ReminderRepository
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.Transactor
import com.postpole.wheelfile.data.attachments.AttachmentLimit
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.attachments.StageResult
import com.postpole.wheelfile.data.attachments.StagedAttachment
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.ExpenseDao
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Recurrence
import com.postpole.wheelfile.data.db.ServiceRecordDao
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.domain.entry.RecordForm
import com.postpole.wheelfile.domain.entry.RecordFormState
import com.postpole.wheelfile.domain.entry.RecordKind
import com.postpole.wheelfile.domain.entry.RecordValidation
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

data class RecordUiState(
    val vehicle: Vehicle? = null,
    val form: RecordFormState,
    val validation: RecordValidation = RecordValidation(emptyMap()),
    val showErrors: Boolean = false,
    val attachments: List<StagedAttachment> = emptyList(),
    /** True when the next attachment would be the 31st on the free tier: the picker buttons open the Pro gate. */
    val attachmentCapReached: Boolean = false,
    val saving: Boolean = false,
) {
    val isService: Boolean get() = form.kind == RecordKind.SERVICE
}

sealed interface RecordEvent {
    /** [promptNotifications] is true when the first reminder was just saved (ask for the notification permission). */
    data class Saved(val promptNotifications: Boolean) : RecordEvent
    data object SaveFailed : RecordEvent
    data object AttachmentFailed : RecordEvent
    data object OpenProGate : RecordEvent
}

/** Service and Expense forms share this view model, the field components and the attachments. No pump-session rule. */
@HiltViewModel
class RecordFormViewModel internal constructor(
    private val vehicles: VehicleDao,
    private val services: ServiceRecordDao,
    private val expenses: ExpenseDao,
    private val reminders: ReminderRepository,
    private val attachmentStore: AttachmentStore,
    attachmentDao: AttachmentDao,
    private val transactor: Transactor,
    private val settings: SettingsRepository,
    private val adsPolicy: AdsPolicy,
    private val analytics: Analytics,
    private val isPro: StateFlow<Boolean>,
    private val vehicleId: Long,
    kind: RecordKind,
    private val proPrompts: ProPromptCoordinator? = null,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        savedState: SavedStateHandle,
        vehicles: VehicleDao,
        services: ServiceRecordDao,
        expenses: ExpenseDao,
        reminders: ReminderRepository,
        attachmentStore: AttachmentStore,
        attachmentDao: AttachmentDao,
        transactor: Transactor,
        settings: SettingsRepository,
        adsPolicy: AdsPolicy,
        analytics: Analytics,
        billing: BillingRepository,
        proPrompts: ProPromptCoordinator,
    ) : this(
        vehicles, services, expenses, reminders, attachmentStore, attachmentDao, transactor, settings, adsPolicy, analytics,
        isPro = billing.isPro,
        vehicleId = checkNotNull(savedState.get<Long>("vehicleId")),
        kind = if (savedState.get<String>("kind") == "expense") RecordKind.EXPENSE else RecordKind.SERVICE,
        proPrompts = proPrompts,
        clock = System::currentTimeMillis,
    )

    private val _state = MutableStateFlow(RecordUiState(form = RecordFormState(kind = kind, dateTime = clock())))
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    private val _events = Channel<RecordEvent>(Channel.BUFFERED)
    val events: Flow<RecordEvent> = _events.receiveAsFlow()

    private var attachmentCount = 0
    private var saved = false

    init {
        viewModelScope.launch {
            val vehicle = vehicles.get(vehicleId) ?: return@launch
            _state.update {
                it.copy(vehicle = vehicle, form = it.form.copy(reminderDistance = RecordForm.defaultReminderDistance(vehicle.distanceUnit)))
            }
            recompute()
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
        it.copy(attachmentCapReached = !AttachmentLimit.canAdd(attachmentCount + it.attachments.size, isPro.value))
    }

    private fun update(block: (RecordFormState) -> RecordFormState) {
        _state.update { it.copy(form = block(it.form)) }
        recompute()
    }

    private fun recompute() = _state.update { s ->
        val v = s.vehicle ?: return@update s
        s.copy(validation = RecordForm.validate(s.form, v))
    }

    fun setDate(epochMillis: Long) = update { it.copy(dateTime = epochMillis) }
    fun setOdometer(t: String) = update { it.copy(odometer = t.take(12)) }
    fun setCost(t: String) = update { it.copy(cost = t.take(12)) }
    fun setWorkshop(t: String) = update { it.copy(workshop = t.take(80)) }
    fun setNote(t: String) = update { it.copy(note = t.take(500)) }
    fun toggleItem(item: String) = update { RecordForm.toggleItem(it, item) }
    fun setCustomItem(t: String) = update { it.copy(customItem = t.take(40)) }
    fun addCustomItem() = update { RecordForm.addCustomItem(it) }
    fun setCategory(c: ExpenseCategory) = update { it.copy(category = c) }
    fun setRecurring(r: Recurrence?) = update { it.copy(recurring = r) }
    fun setCreateReminder(v: Boolean) = update { it.copy(createReminder = v) }
    fun setReminderMonths(t: String) = update { it.copy(reminderMonths = t.filter(Char::isDigit).take(3)) }
    fun setReminderDistance(t: String) = update { it.copy(reminderDistance = t.take(8)) }

    /** A picked photo, captured photo or PDF. At the free cap this opens the Pro gate instead of copying. */
    fun addAttachment(source: String) {
        viewModelScope.launch {
            when (val result = attachmentStore.stage(source, pending = _state.value.attachments.size)) {
                is StageResult.Staged -> {
                    _state.update { it.copy(attachments = it.attachments + result.attachment) }
                    refreshCap()
                }
                StageResult.CapReached -> openProGate()
                StageResult.Failed -> _events.send(RecordEvent.AttachmentFailed)
            }
        }
    }

    fun removeAttachment(staged: StagedAttachment) {
        _state.update { it.copy(attachments = it.attachments - staged) }
        refreshCap()
        viewModelScope.launch { attachmentStore.discard(staged) }
    }

    fun openProGate() {
        analytics.logEvent(AnalyticsEvents.ATTACHMENT_CAP_REACHED)
        _events.trySend(RecordEvent.OpenProGate)
    }

    fun reportAttachmentFailure() {
        _events.trySend(RecordEvent.AttachmentFailed)
    }

    fun save() {
        val s = _state.value
        val vehicle = s.vehicle ?: return
        if (s.saving || vehicle.archived) return
        val validation = RecordForm.validate(s.form, vehicle)
        _state.update { it.copy(validation = validation, showErrors = true) }
        if (!validation.isValid) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val now = clock()
                val service = s.isService
                val reminder = RecordForm.toNextReminder(s.form, vehicle)
                val owner = if (service) OwnerType.SERVICE else OwnerType.EXPENSE
                // Record, attachment rows and next reminder commit together: a failure leaves no partial rows, and
                // the staged files stay in place so the user can retry.
                val dropped = transactor.run {
                    val ownerId = if (service) {
                        services.insert(RecordForm.toServiceRecord(s.form, vehicle, now))
                    } else {
                        expenses.insert(RecordForm.toExpense(s.form, vehicle, now))
                    }
                    val over = attachmentStore.commitAll(s.attachments, owner, ownerId)
                    if (reminder != null) reminders.save(reminder)
                    over
                }
                saved = true
                attachmentStore.discardAll(dropped) // files over the free cap, only after the commit
                var prompt = false
                if (reminder != null) {
                    analytics.logEvent(AnalyticsEvents.REMINDER_CREATED, mapOf("source" to "service"))
                    if (!settings.notificationPrompted.first()) {
                        settings.setNotificationPrompted()
                        prompt = true
                    }
                }
                // Service and expense saves are not a pump session: they never block the interstitial rule.
                val saveKind = if (service) SaveKind.SERVICE else SaveKind.EXPENSE
                adsPolicy.onSaved(saveKind)
                // The Nth saved entry earns the entries_milestone sheet (shown on Vehicle detail > Overview).
                proPrompts?.onEntrySaved(saveKind)
                if (service) proPrompts?.onServiceSaved(now)
                analytics.logEvent(if (service) AnalyticsEvents.SERVICE_SAVED else AnalyticsEvents.EXPENSE_SAVED)
                _events.send(RecordEvent.Saved(prompt))
            } catch (e: Exception) {
                analytics.recordNonFatal(e)
                _state.update { it.copy(saving = false) }
                _events.send(RecordEvent.SaveFailed)
            }
        }
    }

    override fun onCleared() {
        // Staged copies of a form that was closed without saving are not kept.
        if (!saved) {
            _state.value.attachments.forEach { attachmentStore.discardNow(it) }
        }
    }
}
