package com.postpole.wheelfile.domain

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Recurrence
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.entry.FieldError
import com.postpole.wheelfile.domain.entry.RecordField
import com.postpole.wheelfile.domain.entry.RecordForm
import com.postpole.wheelfile.domain.entry.RecordFormState
import com.postpole.wheelfile.domain.entry.RecordKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecordFormTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 3, 10).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun vehicle(unit: DistanceUnit = DistanceUnit.KM, currency: String = "EUR") = Vehicle(
        id = 4, name = "Van", type = VehicleType.VAN, powertrain = Powertrain.DIESEL, distanceUnit = unit,
        volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = currency, createdAt = 1, updatedAt = 1,
    )

    private fun service(change: (RecordFormState) -> RecordFormState = { it }) =
        change(RecordFormState(kind = RecordKind.SERVICE, dateTime = date, items = listOf("Brakes")))

    private fun expense(change: (RecordFormState) -> RecordFormState = { it }) =
        change(RecordFormState(kind = RecordKind.EXPENSE, dateTime = date, category = ExpenseCategory.PARKING, cost = "12.50"))

    @Test
    fun serviceNeedsAtLeastOneItemButCostAndOdometerAreOptional() {
        assertTrue(RecordForm.validate(service(), vehicle()).isValid)
        val none = RecordForm.validate(service { it.copy(items = emptyList()) }, vehicle())
        assertEquals(FieldError.REQUIRED, none.errors[RecordField.ITEMS])
        // A typed but not yet added custom item counts.
        assertTrue(RecordForm.validate(service { it.copy(items = emptyList(), customItem = "Wiper blades") }, vehicle()).isValid)
    }

    @Test
    fun expenseNeedsCategoryAndAmount() {
        assertTrue(RecordForm.validate(expense(), vehicle()).isValid)
        assertEquals(FieldError.REQUIRED, RecordForm.validate(expense { it.copy(category = null) }, vehicle()).errors[RecordField.CATEGORY])
        assertEquals(FieldError.REQUIRED, RecordForm.validate(expense { it.copy(cost = "") }, vehicle()).errors[RecordField.COST])
        assertEquals(FieldError.INVALID, RecordForm.validate(expense { it.copy(cost = "0") }, vehicle()).errors[RecordField.COST])
        assertEquals(FieldError.INVALID, RecordForm.validate(expense { it.copy(cost = "abc") }, vehicle()).errors[RecordField.COST])
    }

    @Test
    fun customItemsAreTrimmedAndDeDuplicated() {
        var s = service()
        s = RecordForm.addCustomItem(s.copy(customItem = "  Cabin filter "))
        s = RecordForm.addCustomItem(s.copy(customItem = "cabin FILTER"))
        assertEquals(listOf("Brakes", "Cabin filter"), s.items)
        assertEquals("", s.customItem)
        assertEquals(listOf("Cabin filter"), RecordForm.toggleItem(s, "Brakes").items)
    }

    @Test
    fun serviceRecordUsesMinorUnitsOfTheVehicleCurrency() {
        val record = RecordForm.toServiceRecord(
            service { it.copy(cost = "123,45", odometer = "50000", workshop = " Garage ", note = "") }, vehicle(), now = 99,
        )
        assertEquals(12345L, record.totalMinor)
        assertEquals(50000.0, record.odometer!!, 0.0)
        assertEquals("Garage", record.workshop)
        assertNull(record.note)
        assertEquals(4L, record.vehicleId)
        assertEquals(listOf("Brakes"), record.items)
        // Zero-decimal currencies keep whole units.
        assertEquals(1500L, RecordForm.toServiceRecord(service { it.copy(cost = "1500") }, vehicle(currency = "JPY"), 0).totalMinor)
    }

    @Test
    fun expenseEntityCarriesCategoryAndRecurrence() {
        val e = RecordForm.toExpense(expense { it.copy(recurring = Recurrence.YEARLY) }, vehicle(), 5)
        assertEquals(ExpenseCategory.PARKING, e.category)
        assertEquals(1250L, e.amountMinor)
        assertEquals(Recurrence.YEARLY, e.recurring)
        assertNull(e.odometer)
    }

    @Test
    fun nextReminderIsOffByDefault() {
        assertNull(RecordForm.toNextReminder(service(), vehicle(), zone))
        assertNull(RecordForm.toNextReminder(expense { it.copy(createReminder = true) }, vehicle(), zone))
    }

    @Test
    fun nextReminderFromServiceUsesWhicheverFirst() {
        val r = RecordForm.toNextReminder(
            service { it.copy(createReminder = true, reminderMonths = "12", reminderDistance = "15000", odometer = "60000") }, vehicle(), zone,
        )!!
        assertEquals(ReminderKind.FIRST_OF, r.kind)
        assertEquals(LocalDate.of(2027, 3, 10).atStartOfDay(zone).toInstant().toEpochMilli(), r.dueDate)
        assertEquals(75_000.0, r.dueOdometer!!, 0.0)
        assertEquals(12, r.repeatMonths)
        assertEquals(15_000.0, r.repeatDistance!!, 0.0)
        assertEquals("Brakes", r.title)
        assertEquals(4L, r.vehicleId)
        assertEquals(500.0, r.leadDistance!!, 0.0)
    }

    @Test
    fun nextReminderCanBeDateOnlyOrDistanceOnly() {
        val dateOnly = RecordForm.toNextReminder(service { it.copy(createReminder = true, reminderMonths = "6", reminderDistance = "") }, vehicle(), zone)!!
        assertEquals(ReminderKind.DATE, dateOnly.kind)
        assertNull(dateOnly.dueOdometer)

        val distanceOnly = RecordForm.toNextReminder(
            service { it.copy(createReminder = true, reminderMonths = "", reminderDistance = "6000", odometer = "1000") },
            vehicle(DistanceUnit.MI), zone,
        )!!
        assertEquals(ReminderKind.ODOMETER, distanceOnly.kind)
        assertEquals(7_000.0, distanceOnly.dueOdometer!!, 0.0)
        assertNull(distanceOnly.dueDate)
        assertEquals(300.0, distanceOnly.leadDistance!!, 0.0)
    }

    @Test
    fun reminderValidation() {
        fun errors(change: (RecordFormState) -> RecordFormState) =
            RecordForm.validate(service { change(it.copy(createReminder = true)) }, vehicle()).errors
        assertTrue(errors { it.copy(reminderMonths = "12") }.isEmpty())
        assertEquals(FieldError.REQUIRED, errors { it.copy(reminderMonths = "", reminderDistance = "") }[RecordField.REMINDER_MONTHS])
        assertEquals(FieldError.INVALID, errors { it.copy(reminderMonths = "0") }[RecordField.REMINDER_MONTHS])
        assertEquals(FieldError.INVALID, errors { it.copy(reminderMonths = "500") }[RecordField.REMINDER_MONTHS])
        // A distance interval needs a starting odometer reading.
        assertEquals(FieldError.REQUIRED, errors { it.copy(reminderMonths = "", reminderDistance = "5000", odometer = "") }[RecordField.ODOMETER])
        assertFalse(
            RecordForm.validate(service { it.copy(createReminder = false, reminderMonths = "0") }, vehicle())
                .errors.containsKey(RecordField.REMINDER_MONTHS),
        )
    }

    @Test
    fun defaultDistanceFollowsTheVehicleUnit() {
        assertEquals("10000", RecordForm.defaultReminderDistance(DistanceUnit.KM))
        assertEquals("6000", RecordForm.defaultReminderDistance(DistanceUnit.MI))
    }
}
