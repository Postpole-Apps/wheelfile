package com.postpole.wheelfile.ui.reminder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.data.ReminderRepository
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.domain.reminder.ReminderEngine
import com.postpole.wheelfile.domain.reminder.ReminderForm
import com.postpole.wheelfile.domain.reminder.ReminderFormState
import com.postpole.wheelfile.domain.reminder.ReminderValidation
import com.postpole.wheelfile.domain.reminder.VehicleUsage
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

data class ReminderFormUiState(
    val vehicle: Vehicle? = null,
    val form: ReminderFormState = ReminderFormState(),
    val validation: ReminderValidation = ReminderValidation(emptyMap()),
    val showErrors: Boolean = false,
    val usage: VehicleUsage = VehicleUsage.UNKNOWN,
    /** Projected date the typed odometer is reached; an estimate only. */
    val estimatedDate: Long? = null,
    val saving: Boolean = false,
)

sealed interface ReminderFormEvent {
    data class Saved(val promptNotifications: Boolean) : ReminderFormEvent
    data object SaveFailed : ReminderFormEvent
}

/** S10: Date | Odometer | Whichever first, repeat and alert lead time. 0 ads. */
@HiltViewModel
class ReminderFormViewModel internal constructor(
    private val repo: ReminderRepository,
    private val vehicles: VehicleDao,
    private val settings: SettingsRepository,
    private val analytics: Analytics,
    private val vehicleId: Long,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        savedState: SavedStateHandle,
        repo: ReminderRepository,
        vehicles: VehicleDao,
        settings: SettingsRepository,
        analytics: Analytics,
    ) : this(repo, vehicles, settings, analytics, checkNotNull(savedState.get<Long>("vehicleId")), System::currentTimeMillis)

    private val _state = MutableStateFlow(ReminderFormUiState())
    val state: StateFlow<ReminderFormUiState> = _state.asStateFlow()

    private val _events = Channel<ReminderFormEvent>(Channel.BUFFERED)
    val events: Flow<ReminderFormEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val vehicle = vehicles.get(vehicleId) ?: return@launch
            val usage = repo.usage(vehicleId, clock())
            _state.update { it.copy(vehicle = vehicle, usage = usage) }
            recompute()
        }
    }

    private fun update(block: (ReminderFormState) -> ReminderFormState) {
        _state.update { it.copy(form = block(it.form)) }
        recompute()
    }

    private fun recompute() = _state.update { s ->
        val odo = com.postpole.wheelfile.domain.entry.EntryForm.parseDecimal(s.form.dueOdometer)?.toDouble()
        val current = s.usage.currentOdometer
        val left = if (odo != null && current != null) odo - current else null
        s.copy(
            validation = ReminderForm.validate(s.form),
            estimatedDate = left?.let { ReminderEngine.estimateDate(it, s.usage.dailyDistance, clock()) },
        )
    }

    fun setTitle(t: String) = update { it.copy(title = t.take(80)) }
    fun setKind(k: ReminderKind) = update { it.copy(kind = k) }
    fun setDueOdometer(t: String) = update { it.copy(dueOdometer = t.take(12)) }
    fun setRepeatMonths(t: String) = update { it.copy(repeatMonths = t.filter(Char::isDigit).take(3)) }
    fun setRepeatDistance(t: String) = update { it.copy(repeatDistance = t.take(8)) }
    fun setLeadDays(d: Int) = update { it.copy(leadDays = d) }

    /** [epochMillis] is any instant on the chosen day; the due date is stored at the start of that local day. */
    fun setDueDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()) = update {
        it.copy(dueDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli())
    }

    fun save() {
        val s = _state.value
        val vehicle = s.vehicle ?: return
        if (s.saving || vehicle.archived) return
        val validation = ReminderForm.validate(s.form)
        _state.update { it.copy(validation = validation, showErrors = true) }
        if (!validation.isValid) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repo.save(ReminderForm.toReminder(s.form, vehicle))
                analytics.logEvent(AnalyticsEvents.REMINDER_CREATED, mapOf("source" to "form", "kind" to s.form.kind.name.lowercase()))
                var prompt = false
                if (!settings.notificationPrompted.first()) {
                    settings.setNotificationPrompted()
                    prompt = true
                }
                _events.send(ReminderFormEvent.Saved(prompt))
            } catch (e: Exception) {
                analytics.recordNonFatal(e)
                _state.update { it.copy(saving = false) }
                _events.send(ReminderFormEvent.SaveFailed)
            }
        }
    }
}
