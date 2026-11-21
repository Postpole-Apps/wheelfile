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
import com.postpole.wheelfile.domain.units.LocaleDefaults
import com.postpole.wheelfile.domain.units.UnitConversion
import com.postpole.wheelfile.domain.vehicle.VehicleLimit
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong

/** What to do with one vehicle found in the file. */
sealed interface VehicleTarget {
    /** Don't import this vehicle. */
    data object Skip : VehicleTarget

    /** Create a vehicle. Units are the file's, or the user's pick when the file doesn't carry them. */
    data class New(val name: String, val distance: DistanceUnit, val volume: VolumeUnit) : VehicleTarget

    /** Add the rows to a vehicle that already exists (its units and currency are kept). */
    data class Existing(val vehicleId: Long) : VehicleTarget
}

/** [targets] is keyed by [ParsedVehicle.key]; [currency] is an ISO 4217 code (files don't carry one). */
data class ImportPlan(val targets: Map<String, VehicleTarget>, val currency: String)

/**
 * Writes a [ParsedFile] to the database in one transaction (spec §2.7). Rows that match an existing entry
 * (same vehicle, time within a minute, same odometer and quantity) are skipped and not counted. New vehicles beyond
 * the free cap are created archived, so the cap never drops data.
 */
class ImportService(
    private val db: AppDatabase,
    private val isPro: () -> Boolean,
    private val clock: () -> Long,
) {
    @Inject
    constructor(db: AppDatabase, billing: BillingRepository) : this(db, { billing.isPro.value }, System::currentTimeMillis)

    suspend fun import(file: ParsedFile, plan: ImportPlan): ImportResult = db.withTransaction {
        val now = clock()
        var fillUps = 0
        var services = 0
        var expenses = 0
        var skipped = 0
        var created = 0
        var archived = 0
        var active = db.vehicleDao().activeCount()
        val source = file.format.sourceTag

        for (parsed in file.vehicles) {
            val target = plan.targets[parsed.key] ?: VehicleTarget.Skip
            val vehicle: Vehicle = when (target) {
                VehicleTarget.Skip -> continue
                is VehicleTarget.Existing -> db.vehicleDao().get(target.vehicleId) ?: continue
                is VehicleTarget.New -> {
                    val archive = !VehicleLimit.canAddActive(active, isPro())
                    val powertrain = Powertrain.PETROL
                    val draft = Vehicle(
                        name = target.name.ifBlank { parsed.name },
                        type = VehicleType.CAR, powertrain = powertrain,
                        make = parsed.make, model = parsed.model, year = parsed.year,
                        plate = parsed.plate, vin = parsed.vin,
                        distanceUnit = target.distance, volumeUnit = target.volume,
                        economyDisplay = LocaleDefaults.economyFor(target.distance, target.volume, Locale.getDefault().country, powertrain),
                        currency = plan.currency, archived = archive,
                        createdAt = now, updatedAt = now,
                    )
                    created++
                    if (archive) archived++ else active++
                    draft.copy(id = db.vehicleDao().insert(draft))
                }
            }
            val digits = fractionDigits(vehicle.currency)
            val distance = Converter(parsed.distanceUnit, vehicle.distanceUnit, parsed.volumeUnit, vehicle.volumeUnit)

            // ---- fill-ups and charges ----
            val seen = DuplicateIndex()
            db.energyLogDao().getAll(vehicle.id).forEach { seen.add(it.odometer, it.quantity, it.dateTime) }
            val newLogs = ArrayList<EnergyLog>()
            for (row in parsed.energy) {
                val odo = distance.odometer(row.odometer)
                val qty = distance.volume(row.quantity)
                if (seen.contains(odo, qty, row.dateTime)) {
                    skipped++
                    continue
                }
                seen.add(odo, qty, row.dateTime)
                val total = row.totalMajor?.let { Parse.minor(it, digits) }
                    ?: row.pricePerUnitMajor?.let { Parse.minor(it * row.quantity, digits) }
                val perUnit = row.pricePerUnitMajor?.let { Parse.minor(it, digits) }
                    ?: row.totalMajor?.takeIf { row.quantity > 0 }?.let { Parse.minor(it / row.quantity, digits) }
                newLogs.add(
                    EnergyLog(
                        vehicleId = vehicle.id, stream = EnergyStream.LIQUID, dateTime = row.dateTime,
                        odometer = odo, quantity = qty,
                        pricePerUnitMinor = perUnit, totalMinor = total,
                        isFull = row.isFull, missedPrevious = row.missedPrevious,
                        fuelGrade = row.fuelGrade, station = row.station, note = row.note,
                        importSource = source, importKey = "${row.dateTime / 60_000}|$odo|$qty",
                        createdAt = now, updatedAt = now,
                    ),
                )
            }
            db.energyLogDao().insertAll(newLogs)
            fillUps += newLogs.size

            // ---- services ----
            val existingServices = db.serviceRecordDao().getForVehicle(vehicle.id)
            val seenServices = HashSet<String>(existingServices.map { serviceKey(it.dateTime, it.odometer, it.items.firstOrNull(), it.totalMinor) })
            for (row in parsed.services) {
                val total = row.totalMajor?.let { Parse.minor(it, digits) }
                val odo = row.odometer?.let { distance.odometer(it) }
                val key = serviceKey(row.dateTime, odo, row.title, total)
                if (!seenServices.add(key) || existingServices.any { near(it.dateTime, row.dateTime) && it.odometer == odo && it.items.firstOrNull() == row.title && it.totalMinor == total }) {
                    skipped++
                    continue
                }
                db.serviceRecordDao().insert(
                    ServiceRecord(
                        vehicleId = vehicle.id, dateTime = row.dateTime, odometer = odo, items = listOf(row.title),
                        totalMinor = total, note = row.note, importSource = source, importKey = key,
                        createdAt = now, updatedAt = now,
                    ),
                )
                services++
            }

            // ---- expenses ----
            val existingExpenses = db.expenseDao().getForVehicle(vehicle.id)
            val seenExpenses = HashSet<String>(existingExpenses.map { serviceKey(it.dateTime, it.odometer, it.category.name, it.amountMinor) })
            for (row in parsed.expenses) {
                val amount = Parse.minor(row.amountMajor, digits)
                val odo = row.odometer?.let { distance.odometer(it) }
                val key = serviceKey(row.dateTime, odo, row.category.name, amount)
                if (!seenExpenses.add(key) || existingExpenses.any { near(it.dateTime, row.dateTime) && it.odometer == odo && it.category == row.category && it.amountMinor == amount }) {
                    skipped++
                    continue
                }
                db.expenseDao().insert(
                    Expense(
                        vehicleId = vehicle.id, dateTime = row.dateTime, odometer = odo, category = row.category,
                        amountMinor = amount, note = listOfNotNull(row.title.takeIf { it.isNotBlank() }, row.note).joinToString(" - ").ifBlank { null },
                        importSource = source, importKey = key, createdAt = now, updatedAt = now,
                    ),
                )
                expenses++
            }
        }
        ImportResult(fillUps, services, expenses, skipped, file.issues, created, archived, file.beta)
    }

    private fun serviceKey(date: Long, odo: Double?, title: String?, total: Long?) = "${date / 60_000}|$odo|$title|$total"

    private fun near(a: Long, b: Long) = abs(a - b) <= 60_000

    private fun fractionDigits(currency: String): Int =
        try {
            Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
        } catch (e: IllegalArgumentException) {
            2
        }

    /** Converts file units to the target vehicle's units. Unknown source units mean "same as the target". */
    private class Converter(
        private val fromDistance: DistanceUnit?,
        private val toDistance: DistanceUnit,
        private val fromVolume: VolumeUnit?,
        private val toVolume: VolumeUnit,
    ) {
        fun odometer(value: Double): Double {
            if (fromDistance == null || fromDistance == toDistance) return value
            return round3(UnitConversion.fromKm(UnitConversion.toKm(value, fromDistance), toDistance))
        }

        fun volume(value: Double): Double {
            if (fromVolume == null || fromVolume == toVolume || fromVolume == VolumeUnit.KG || toVolume == VolumeUnit.KG) return value
            return round3(UnitConversion.fromLitres(UnitConversion.toBaseQuantity(value, fromVolume), toVolume))
        }

        private fun round3(v: Double) = (v * 1000).roundToLong() / 1000.0
    }

    /** Finds entries with the same odometer and quantity whose time is within a minute. */
    private class DuplicateIndex {
        private val byValue = HashMap<Pair<Long, Long>, MutableList<Long>>()

        private fun key(odo: Double, qty: Double) = (odo * 1000).roundToLong() to (qty * 1000).roundToLong()

        fun add(odo: Double, qty: Double, time: Long) {
            byValue.getOrPut(key(odo, qty)) { ArrayList() }.add(time)
        }

        fun contains(odo: Double, qty: Double, time: Long): Boolean =
            byValue[key(odo, qty)]?.any { abs(it - time) <= 60_000 } == true
    }
}
