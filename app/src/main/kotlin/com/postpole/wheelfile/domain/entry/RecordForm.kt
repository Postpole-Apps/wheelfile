package com.postpole.wheelfile.domain.entry

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.Recurrence
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

enum class RecordKind { SERVICE, EXPENSE }

/** Text-field state shared by the Service and Expense forms. */
data class RecordFormState(
    val kind: RecordKind,
    val dateTime: Long,
    val odometer: String = "",
    /** Selected service items (preset labels and custom text), in the order picked. */
    val items: List<String> = emptyList(),
    val customItem: String = "",
    /** Service cost or expense amount. */
    val cost: String = "",
    val workshop: String = "",
    val category: ExpenseCategory? = null,
    val recurring: Recurrence? = null,
    val note: String = "",
    /** "Create next reminder from this" (service only). */
    val createReminder: Boolean = false,
    val reminderMonths: String = DEFAULT_REMINDER_MONTHS,
    val reminderDistance: String = "",
) {
    companion object {
        const val DEFAULT_REMINDER_MONTHS = "12"
        const val DEFAULT_REMINDER_KM = "10000"
        const val DEFAULT_REMINDER_MI = "6000"
    }
}

enum class RecordField { ITEMS, CATEGORY, COST, ODOMETER, REMINDER_MONTHS, REMINDER_DISTANCE }

data class RecordValidation(val errors: Map<RecordField, FieldError>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object RecordForm {
    /** Preset categories shown on the Expense form. */
    val EXPENSE_CATEGORIES: List<ExpenseCategory> = ExpenseCategory.entries

    private val MAX_ODOMETER = BigDecimal("9999999")
    private val MAX_MONEY = BigDecimal("9999999999")
    private const val MAX_REMINDER_MONTHS = 120

    fun defaultReminderDistance(unit: DistanceUnit): String =
        if (unit == DistanceUnit.MI) RecordFormState.DEFAULT_REMINDER_MI else RecordFormState.DEFAULT_REMINDER_KM

    fun toggleItem(state: RecordFormState, item: String): RecordFormState =
        state.copy(items = if (item in state.items) state.items - item else state.items + item)

    /** Adds the typed custom item (trimmed, de-duplicated ignoring case). */
    fun addCustomItem(state: RecordFormState): RecordFormState {
        val text = state.customItem.trim().take(40)
        if (text.isEmpty()) return state.copy(customItem = "")
        val items = if (state.items.any { it.equals(text, ignoreCase = true) }) state.items else state.items + text
        return state.copy(items = items, customItem = "")
    }

    private fun money(text: String, digits: Int): Long? = EntryForm.parseDecimal(text)?.let { PriceMath.toMinor(it, digits) }

    fun validate(state: RecordFormState, vehicle: Vehicle): RecordValidation {
        val errors = linkedMapOf<RecordField, FieldError>()
        val service = state.kind == RecordKind.SERVICE

        if (service && state.items.isEmpty() && state.customItem.isBlank()) errors[RecordField.ITEMS] = FieldError.REQUIRED
        if (!service && state.category == null) errors[RecordField.CATEGORY] = FieldError.REQUIRED

        val cost = EntryForm.parseDecimal(state.cost)
        when {
            state.cost.isBlank() -> if (!service) errors[RecordField.COST] = FieldError.REQUIRED
            cost == null || (!service && cost.signum() == 0) -> errors[RecordField.COST] = FieldError.INVALID
            cost > MAX_MONEY -> errors[RecordField.COST] = FieldError.TOO_LARGE
        }

        val odometer = EntryForm.parseDecimal(state.odometer)
        when {
            state.odometer.isBlank() -> Unit
            odometer == null || odometer.signum() == 0 -> errors[RecordField.ODOMETER] = FieldError.INVALID
            odometer > MAX_ODOMETER -> errors[RecordField.ODOMETER] = FieldError.TOO_LARGE
        }

        if (service && state.createReminder) {
            val months = state.reminderMonths.toIntOrNull()
            val distance = EntryForm.parseDecimal(state.reminderDistance)
            if (state.reminderMonths.isNotBlank() && (months == null || months !in 1..MAX_REMINDER_MONTHS)) {
                errors[RecordField.REMINDER_MONTHS] = FieldError.INVALID
            }
            if (state.reminderDistance.isNotBlank() && (distance == null || distance.signum() == 0)) {
                errors[RecordField.REMINDER_DISTANCE] = FieldError.INVALID
            } else if (distance != null && state.odometer.isBlank()) {
                // A distance interval needs a starting odometer reading.
                errors[RecordField.ODOMETER] = FieldError.REQUIRED
            }
            if (state.reminderMonths.isBlank() && state.reminderDistance.isBlank()) {
                errors[RecordField.REMINDER_MONTHS] = FieldError.REQUIRED
            }
        }
        return RecordValidation(errors)
    }

    /** Builds the entity. Call only when [validate] returned no errors. */
    fun toServiceRecord(state: RecordFormState, vehicle: Vehicle, now: Long): ServiceRecord {
        val digits = EntryForm.fractionDigits(vehicle.currency)
        val items = addCustomItem(state).items
        return ServiceRecord(
            vehicleId = vehicle.id,
            dateTime = state.dateTime,
            odometer = EntryForm.parseDecimal(state.odometer)?.toDouble(),
            items = items,
            totalMinor = money(state.cost, digits),
            workshop = state.workshop.trim().ifEmpty { null },
            note = state.note.trim().ifEmpty { null },
            createdAt = now,
            updatedAt = now,
        )
    }

    fun toExpense(state: RecordFormState, vehicle: Vehicle, now: Long): Expense {
        val digits = EntryForm.fractionDigits(vehicle.currency)
        return Expense(
            vehicleId = vehicle.id,
            dateTime = state.dateTime,
            odometer = EntryForm.parseDecimal(state.odometer)?.toDouble(),
            category = state.category!!,
            amountMinor = money(state.cost, digits)!!,
            recurring = state.recurring,
            note = state.note.trim().ifEmpty { null },
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * The "Create next reminder from this" result: due [RecordFormState.reminderMonths] after the service date and/or
     * [RecordFormState.reminderDistance] after its odometer, whichever comes first. Null when the switch is off.
     */
    fun toNextReminder(state: RecordFormState, vehicle: Vehicle, zone: ZoneId = ZoneId.systemDefault()): Reminder? {
        if (state.kind != RecordKind.SERVICE || !state.createReminder) return null
        val months = state.reminderMonths.toIntOrNull()?.takeIf { it > 0 }
        val distance = EntryForm.parseDecimal(state.reminderDistance)?.toDouble()?.takeIf { it > 0.0 }
        val odometer = EntryForm.parseDecimal(state.odometer)?.toDouble()
        val dueOdometer = if (distance != null && odometer != null) odometer + distance else null
        val dueDate = months?.let {
            Instant.ofEpochMilli(state.dateTime).atZone(zone).toLocalDate().plusMonths(it.toLong())
                .atStartOfDay(zone).toInstant().toEpochMilli()
        }
        if (dueDate == null && dueOdometer == null) return null
        val title = addCustomItem(state).items.joinToString().take(60).ifEmpty { "Service" }
        return Reminder(
            vehicleId = vehicle.id,
            title = title,
            kind = when {
                dueDate != null && dueOdometer != null -> ReminderKind.FIRST_OF
                dueDate != null -> ReminderKind.DATE
                else -> ReminderKind.ODOMETER
            },
            dueDate = dueDate,
            dueOdometer = dueOdometer,
            repeatMonths = months.takeIf { dueDate != null },
            repeatDistance = distance.takeIf { dueOdometer != null },
            leadDays = DEFAULT_LEAD_DAYS,
            leadDistance = defaultLeadDistance(vehicle.distanceUnit),
        )
    }

    const val DEFAULT_LEAD_DAYS = 14

    fun defaultLeadDistance(unit: DistanceUnit): Double = if (unit == DistanceUnit.MI) 300.0 else 500.0
}
