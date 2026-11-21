package com.postpole.wheelfile.domain.reminder

import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.entry.EntryForm
import com.postpole.wheelfile.domain.entry.FieldError
import com.postpole.wheelfile.domain.entry.RecordForm
import java.math.BigDecimal

data class ReminderFormState(
    val title: String = "",
    val kind: ReminderKind = ReminderKind.DATE,
    /** Epoch millis at the start of the due day. */
    val dueDate: Long? = null,
    val dueOdometer: String = "",
    /** Repeat every N months; blank for none. */
    val repeatMonths: String = "",
    val repeatDistance: String = "",
    /** Alert this many days before the date (also applies to the date half of whichever-first). */
    val leadDays: Int = RecordForm.DEFAULT_LEAD_DAYS,
)

enum class ReminderField { TITLE, DUE_DATE, DUE_ODOMETER, REPEAT_MONTHS, REPEAT_DISTANCE }

data class ReminderValidation(val errors: Map<ReminderField, FieldError>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object ReminderForm {
    val LEAD_DAY_CHOICES = listOf(0, 1, 7, 14, 30)
    val REPEAT_MONTH_CHOICES = listOf(6, 12, 24)
    private val MAX_ODOMETER = BigDecimal("9999999")

    fun usesDate(kind: ReminderKind) = kind != ReminderKind.ODOMETER
    fun usesOdometer(kind: ReminderKind) = kind != ReminderKind.DATE

    fun validate(state: ReminderFormState): ReminderValidation {
        val errors = linkedMapOf<ReminderField, FieldError>()
        if (state.title.isBlank()) errors[ReminderField.TITLE] = FieldError.REQUIRED
        if (usesDate(state.kind) && state.dueDate == null) errors[ReminderField.DUE_DATE] = FieldError.REQUIRED
        if (usesOdometer(state.kind)) {
            val odo = EntryForm.parseDecimal(state.dueOdometer)
            when {
                state.dueOdometer.isBlank() -> errors[ReminderField.DUE_ODOMETER] = FieldError.REQUIRED
                odo == null || odo.signum() == 0 -> errors[ReminderField.DUE_ODOMETER] = FieldError.INVALID
                odo > MAX_ODOMETER -> errors[ReminderField.DUE_ODOMETER] = FieldError.TOO_LARGE
            }
        }
        val months = state.repeatMonths.toIntOrNull()
        if (state.repeatMonths.isNotBlank() && (months == null || months !in 1..120)) errors[ReminderField.REPEAT_MONTHS] = FieldError.INVALID
        val distance = EntryForm.parseDecimal(state.repeatDistance)
        if (state.repeatDistance.isNotBlank() && (distance == null || distance.signum() == 0)) errors[ReminderField.REPEAT_DISTANCE] = FieldError.INVALID
        return ReminderValidation(errors)
    }

    /** Builds the entity. Call only when [validate] returned no errors. */
    fun toReminder(state: ReminderFormState, vehicle: Vehicle): Reminder = Reminder(
        vehicleId = vehicle.id,
        title = state.title.trim().take(80),
        kind = state.kind,
        dueDate = state.dueDate.takeIf { usesDate(state.kind) },
        dueOdometer = EntryForm.parseDecimal(state.dueOdometer)?.toDouble().takeIf { usesOdometer(state.kind) },
        repeatMonths = state.repeatMonths.toIntOrNull()?.takeIf { usesDate(state.kind) },
        repeatDistance = EntryForm.parseDecimal(state.repeatDistance)?.toDouble()?.takeIf { usesOdometer(state.kind) },
        leadDays = state.leadDays,
        leadDistance = RecordForm.defaultLeadDistance(vehicle.distanceUnit),
    )
}
