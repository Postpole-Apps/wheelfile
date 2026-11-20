package com.postpole.wheelfile.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room schema v1 (docs/spec.md §4.1).
 * Money is `Long` minor units plus an ISO 4217 currency (the vehicle's). Distances and quantities are stored
 * as entered, in the vehicle's units; the economy engine normalises them to km / L / kWh / kg.
 * All timestamps are epoch milliseconds.
 */
@Entity
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: VehicleType,
    val powertrain: Powertrain,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val plate: String? = null,
    val vin: String? = null,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
    val economyDisplay: EconomyUnit,
    /** ISO 4217 code. */
    val currency: String,
    val tankCapacity: Double? = null,
    val batteryKwh: Double? = null,
    val photoPath: String? = null,
    val archived: Boolean = false,
    val soldAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A fill-up or a charge. */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vehicleId", "odometer"), Index("vehicleId", "dateTime")],
)
data class EnergyLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val stream: EnergyStream,
    val dateTime: Long,
    /** In the vehicle's distance unit. */
    val odometer: Double,
    /** In the vehicle's volume unit, or kWh for [EnergyStream.ELECTRIC]. */
    val quantity: Double,
    val pricePerUnitMinor: Long? = null,
    val totalMinor: Long? = null,
    val isFull: Boolean,
    val missedPrevious: Boolean = false,
    val fuelGrade: String? = null,
    val chargerType: ChargerType? = null,
    val socStart: Int? = null,
    val socEnd: Int? = null,
    val station: String? = null,
    val note: String? = null,
    val importSource: String? = null,
    val importKey: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vehicleId", "dateTime")],
)
data class ServiceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateTime: Long,
    val odometer: Double? = null,
    /** Service items, stored through a TypeConverter. */
    val items: List<String> = emptyList(),
    val totalMinor: Long? = null,
    val workshop: String? = null,
    val note: String? = null,
    val importSource: String? = null,
    val importKey: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vehicleId", "dateTime")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateTime: Long,
    val odometer: Double? = null,
    val category: ExpenseCategory,
    val customCategoryId: Long? = null,
    val amountMinor: Long,
    val recurring: Recurrence? = null,
    val note: String? = null,
    val importSource: String? = null,
    val importKey: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vehicleId")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val title: String,
    val kind: ReminderKind,
    val dueDate: Long? = null,
    val dueOdometer: Double? = null,
    val repeatMonths: Int? = null,
    val repeatDistance: Double? = null,
    val leadDays: Int = 0,
    val leadDistance: Double? = null,
    val presetCode: String? = null,
    val linkedDocumentId: Long? = null,
    val done: Boolean = false,
    val lastNotifiedAt: Long? = null,
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vehicleId")],
)
data class VehicleDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val type: DocumentType,
    val title: String,
    val number: String? = null,
    val issueDate: Long? = null,
    val expiryDate: Long? = null,
    /** Days before expiry to alert (default 30, 14, 7, 1). */
    val alertDays: List<Int> = listOf(30, 14, 7, 1),
    val note: String? = null,
)

/** Attachment metadata; the file lives in app-private storage. Counted for the free cap (spec §5.1). */
@Entity(indices = [Index("ownerType", "ownerId")])
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: OwnerType,
    val ownerId: Long,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
)

/** User-defined expense/service category (Pro). */
@Entity
data class CustomCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
)
