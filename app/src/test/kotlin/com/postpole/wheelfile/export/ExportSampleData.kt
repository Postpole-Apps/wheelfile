package com.postpole.wheelfile.export

import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.ChargerType
import com.postpole.wheelfile.data.db.CustomCategory
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Recurrence
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit

/** Synthetic data touching every table and every nullable column (neutral EU/US examples). */
object ExportSampleData {
    const val T0 = 1_704_443_400_000L // 2024-01-05T08:30:00Z

    fun vehicle(id: Long, name: String, archived: Boolean = false, currency: String = "EUR") = Vehicle(
        id = id, name = name, type = VehicleType.CAR, powertrain = Powertrain.PETROL, make = "Example", model = "Hatch",
        year = 2015, plate = "AB-123-CD", vin = "VIN$id", distanceUnit = DistanceUnit.KM, volumeUnit = VolumeUnit.L,
        economyDisplay = EconomyUnit.L_PER_100KM, currency = currency, tankCapacity = 45.5, batteryKwh = null,
        archived = archived, soldAt = null, createdAt = T0, updatedAt = T0,
    )

    fun snapshot(vehicles: List<Vehicle> = listOf(vehicle(1, "Daily \"Hatch\", blue"), vehicle(2, "=Van", currency = "JPY"))): DataSnapshot {
        val first = vehicles.first().id
        return DataSnapshot(
            vehicles = vehicles,
            energyLogs = listOf(
                EnergyLog(
                    id = 1, vehicleId = first, stream = EnergyStream.LIQUID, dateTime = T0, odometer = 10000.5, quantity = 40.25,
                    pricePerUnitMinor = 155, totalMinor = 6239, isFull = true, missedPrevious = false, fuelGrade = "95",
                    station = "Main St, Springfield", note = "first\nsecond", importSource = "fuelio", importKey = "k1",
                    createdAt = T0, updatedAt = T0,
                ),
                EnergyLog(
                    id = 2, vehicleId = first, stream = EnergyStream.ELECTRIC, dateTime = T0 + 86_400_000, odometer = 10500.0,
                    quantity = 30.0, isFull = false, missedPrevious = true, chargerType = ChargerType.DC_FAST, socStart = 20,
                    socEnd = 80, createdAt = T0, updatedAt = T0,
                ),
            ),
            serviceRecords = listOf(
                ServiceRecord(
                    id = 1, vehicleId = first, dateTime = T0, odometer = 10100.0, items = listOf("Oil change", "Air filter"),
                    totalMinor = 12000, workshop = "Garage", note = null, createdAt = T0, updatedAt = T0,
                ),
            ),
            expenses = listOf(
                Expense(
                    id = 1, vehicleId = first, dateTime = T0, category = ExpenseCategory.INSURANCE, amountMinor = 45000,
                    recurring = Recurrence.YEARLY, note = "policy", createdAt = T0, updatedAt = T0,
                ),
            ),
            reminders = listOf(
                Reminder(
                    id = 1, vehicleId = first, title = "Service", kind = ReminderKind.DATE, dueDate = T0 + 30 * 86_400_000L,
                    repeatMonths = 12, leadDays = 14,
                ),
            ),
            documents = listOf(
                VehicleDocument(
                    id = 1, vehicleId = first, type = DocumentType.INSURANCE, title = "Policy", number = "P-1",
                    issueDate = T0, expiryDate = T0 + 365 * 86_400_000L, alertDays = listOf(30, 7), note = "n",
                ),
            ),
            attachments = listOf(
                Attachment(id = 1, ownerType = OwnerType.SERVICE, ownerId = 1, fileName = "a1.pdf", mimeType = "application/pdf", sizeBytes = 8, createdAt = T0),
                Attachment(id = 2, ownerType = OwnerType.DOCUMENT, ownerId = 1, fileName = "a2.jpg", mimeType = "image/jpeg", sizeBytes = 6, createdAt = T0),
            ),
            customCategories = listOf(CustomCategory(id = 1, name = "Detailing", kind = "EXPENSE")),
        )
    }
}
