package com.postpole.wheelfile.importer

import androidx.room.withTransaction
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.entry.PriceMath
import com.postpole.wheelfile.domain.units.LocaleDefaults
import com.postpole.wheelfile.domain.units.UnitConversion
import com.postpole.wheelfile.domain.vehicle.VehicleLimit
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Where one source vehicle goes. */
sealed interface VehicleTarget {
    /** A new vehicle created from the file. */
    data object New : VehicleTarget

    /** An existing vehicle that receives the entries (its units are kept; values are converted). */
    data class Existing(val vehicleId: Long) : VehicleTarget
}

data class ImportOptions(
    /** ISO 4217 code; the sources carry no currency. */
    val currency: String,
    /** By [SourceVehicle.key]. Missing keys mean [VehicleTarget.New]. */
    val targets: Map<String, VehicleTarget> = emptyMap(),
    /** Source keys the user chose to leave out. */
    val skipped: Set<String> = emptySet(),
)

data class ImportResult(
    val format: ImportFormat,
    val fills: Int,
    val services: Int,
    val expenses: Int,
    /** Duplicates that were left out; they are not part of the counts above. */
    val skippedDuplicates: Int,
    val errors: List<RowError>,
    val createdVehicles: Int,
    /** Names of vehicles imported as archived because the free tier holds 2 active vehicles. */
    val archivedVehicles: List<String>,
) {
    val beta: Boolean get() = format.beta
}

/**
 * Writes a [ParsedImport] to the database in one transaction. Duplicates (same vehicle, date within one minute,
 * odometer and volume) are skipped and not counted. Vehicles beyond the free cap are created archived: data is never dropped.
 */
@Singleton
class ImportService(
    private val db: AppDatabase,
    private val isPro: () -> Boolean,
    private val clock: () -> Long,
    private val locale: () -> Locale,
) {
    @Inject
    constructor(db: AppDatabase, billing: BillingRepository) :
        this(db, { billing.isPro.value }, System::currentTimeMillis, { Locale.getDefault() })

    suspend fun import(parsed: ParsedImport, options: ImportOptions): ImportResult = db.withTransaction {
        val now = clock()
        var active = db.vehicleDao().activeCount()
        var created = 0
        var fills = 0
        var services = 0
        var expenses = 0
        var duplicates = 0
        val archived = ArrayList<String>()
        val fractionDigits = fractionDigits(options.currency)
        val defaults = LocaleDefaults.forLocale(locale())

        for (source in parsed.vehicles) {
            if (source.key in options.skipped) continue
            val target = options.targets[source.key] ?: VehicleTarget.New
            val vehicle: Vehicle = when (target) {
                is VehicleTarget.Existing -> db.vehicleDao().get(target.vehicleId) ?: continue
                VehicleTarget.New -> {
                    val distance = source.distanceUnit ?: defaults.distance
                    val volume = source.volumeUnit ?: defaults.volume
                    val overCap = !isPro() && !VehicleLimit.canAddActive(active, isPro = false)
                    val draft = Vehicle(
                        name = source.name.ifBlank { "Vehicle" }, type = VehicleType.CAR, powertrain = Powertrain.PETROL,
                        make = source.make, model = source.model, year = source.year, plate = source.plate, vin = source.vin,
                        distanceUnit = distance, volumeUnit = volume,
                        economyDisplay = LocaleDefaults.economyFor(distance, volume, locale().country, Powertrain.PETROL),
                        currency = options.currency, archived = overCap, createdAt = now, updatedAt = now,
                    )
                    created++
                    if (overCap) archived += draft.name else active++
                    draft.copy(id = db.vehicleDao().insert(draft))
                }
            }

            val fromDistance = source.distanceUnit ?: vehicle.distanceUnit
            val fromVolume = source.volumeUnit ?: vehicle.volumeUnit
            val distanceFactor = distanceFactor(fromDistance, vehicle.distanceUnit)
            val volumeFactor = volumeFactor(fromVolume, vehicle.volumeUnit)
            val digits = fractionDigits(vehicle.currency)
            val source_ = parsed.format.name

            val existingFills = DuplicateIndex(
                db.energyLogDao().getAll(vehicle.id).map { Key(it.dateTime, it.odometer, it.quantity) },
            )
            for (f in source.fills) {
                val odometer = f.odometer * distanceFactor
                val quantity = f.quantity * volumeFactor
                if (!existingFills.add(Key(f.dateTime, odometer, quantity))) {
                    duplicates++
                    continue
                }
                db.energyLogDao().insert(
                    EnergyLog(
                        vehicleId = vehicle.id, stream = EnergyStream.LIQUID, dateTime = f.dateTime,
                        odometer = odometer, quantity = quantity,
                        pricePerUnitMinor = f.pricePerUnit?.let { PriceMath.unitPriceToStored(BigDecimal.valueOf(it / volumeFactor), digits) },
                        totalMinor = f.total?.let { minor(it, digits) },
                        isFull = f.isFull, missedPrevious = f.missedPrevious,
                        fuelGrade = f.fuelGrade, station = f.station, note = f.note,
                        importSource = source_, importKey = "${f.dateTime / 60_000}|${f.odometer}|${f.quantity}",
                        createdAt = now, updatedAt = now,
                    ),
                )
                fills++
            }

            val existingServices = DuplicateIndex(
                db.serviceRecordDao().getForVehicle(vehicle.id).map { Key(it.dateTime, it.odometer ?: -1.0, (it.totalMinor ?: -1L).toDouble()) },
            )
            for (s in source.services) {
                val odometer = s.odometer?.times(distanceFactor)
                val total = s.total?.let { minor(it, digits) }
                if (!existingServices.add(Key(s.dateTime, odometer ?: -1.0, (total ?: -1L).toDouble()))) {
                    duplicates++
                    continue
                }
                db.serviceRecordDao().insert(
                    ServiceRecord(
                        vehicleId = vehicle.id, dateTime = s.dateTime, odometer = odometer, items = listOf(s.title),
                        totalMinor = total, note = s.note,
                        importSource = source_, importKey = "${s.dateTime / 60_000}|${s.odometer}|${s.total}",
                        createdAt = now, updatedAt = now,
                    ),
                )
                services++
            }

            val existingExpenses = DuplicateIndex(
                db.expenseDao().getForVehicle(vehicle.id).map { Key(it.dateTime, it.odometer ?: -1.0, it.amountMinor.toDouble()) },
            )
            for (e in source.expenses) {
                val odometer = e.odometer?.times(distanceFactor)
                val amount = minor(e.amount, digits)
                if (!existingExpenses.add(Key(e.dateTime, odometer ?: -1.0, amount.toDouble()))) {
                    duplicates++
                    continue
                }
                db.expenseDao().insert(
                    Expense(
                        vehicleId = vehicle.id, dateTime = e.dateTime, odometer = odometer, category = e.category,
                        amountMinor = amount, note = listOfNotNull(e.title.takeIf { it.isNotBlank() }, e.note).joinToString(": "),
                        importSource = source_, importKey = "${e.dateTime / 60_000}|${e.odometer}|${e.amount}",
                        createdAt = now, updatedAt = now,
                    ),
                )
                expenses++
            }
        }
        ImportResult(parsed.format, fills, services, expenses, duplicates, parsed.errors, created, archived)
    }

    private data class Key(val time: Long, val odometer: Double, val amount: Double)

    /** Adds [key] and returns true, or returns false when a match (time within a minute, same odometer and amount) exists. */
    private class DuplicateIndex(initial: List<Key>) {
        private val byMinute = HashMap<Long, MutableList<Key>>()

        init {
            initial.forEach { put(it) }
        }

        private fun put(k: Key) {
            byMinute.getOrPut(k.time / 60_000) { ArrayList() }.add(k)
        }

        fun add(key: Key): Boolean {
            val minute = key.time / 60_000
            for (m in (minute - 1)..(minute + 1)) {
                val hit = byMinute[m]?.any {
                    abs(it.time - key.time) <= 60_000 && abs(it.odometer - key.odometer) < 0.01 && abs(it.amount - key.amount) < 0.001
                }
                if (hit == true) return false
            }
            put(key)
            return true
        }
    }

    companion object {
        fun fractionDigits(code: String): Int =
            runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).let { if (it < 0) 2 else it }

        fun minor(amount: Double, digits: Int): Long =
            BigDecimal.valueOf(amount).setScale(digits, RoundingMode.HALF_UP).movePointRight(digits).toLong()

        private fun distanceFactor(from: DistanceUnit, to: DistanceUnit): Double =
            if (from == to) 1.0 else UnitConversion.fromKm(UnitConversion.toKm(1.0, from), to)

        /** Multiplier that turns a quantity in [from] into [to]. Mass units (kg) are left unchanged. */
        private fun volumeFactor(from: VolumeUnit, to: VolumeUnit): Double {
            if (from == to || from == VolumeUnit.KG || to == VolumeUnit.KG) return 1.0
            fun litres(u: VolumeUnit) = when (u) {
                VolumeUnit.L -> 1.0
                VolumeUnit.US_GAL -> com.postpole.wheelfile.domain.units.UnitConstants.LITRES_PER_US_GALLON
                VolumeUnit.UK_GAL -> com.postpole.wheelfile.domain.units.UnitConstants.LITRES_PER_UK_GALLON
                VolumeUnit.KG -> 1.0
            }
            return litres(from) / litres(to)
        }
    }
}
