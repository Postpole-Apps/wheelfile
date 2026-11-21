package com.postpole.wheelfile.export

import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.BackupDao
import com.postpole.wheelfile.data.db.CustomCategory
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VolumeUnit
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Every table of the database, read in one go. */
data class DataSnapshot(
    val vehicles: List<Vehicle>,
    val energyLogs: List<EnergyLog>,
    val serviceRecords: List<ServiceRecord>,
    val expenses: List<Expense>,
    val reminders: List<Reminder>,
    val documents: List<VehicleDocument>,
    val attachments: List<Attachment>,
    val customCategories: List<CustomCategory>,
)

suspend fun BackupDao.snapshot() = DataSnapshot(
    vehicles(), energyLogs(), serviceRecords(), expenses(), reminders(), documents(), attachments(), customCategories(),
)

/**
 * The documented Wheelfile CSV export (spec §2.8): a ZIP with one UTF-8 CSV per table, ISO 8601 dates, dot decimals,
 * money in major units with an explicit `currency` column, and explicit unit columns. Free on every tier.
 */
object CsvExporter {
    val FILES = listOf("vehicles.csv", "energy_logs.csv", "service_records.csv", "expenses.csv", "reminders.csv", "documents.csv")

    /** [vehicleIds] limits the export to some vehicles; null exports all of them. */
    fun export(data: DataSnapshot, zone: ZoneId = ZoneId.systemDefault(), vehicleIds: Set<Long>? = null): ByteArray {
        fun <T> List<T>.forVehicles(id: (T) -> Long) = if (vehicleIds == null) this else filter { id(it) in vehicleIds }
        val vehicles = data.vehicles.forVehicles { it.id }
        val byId = vehicles.associateBy { it.id }
        val tables = linkedMapOf(
            "vehicles.csv" to vehiclesCsv(vehicles, zone),
            "energy_logs.csv" to energyCsv(data.energyLogs.forVehicles { it.vehicleId }, byId, zone),
            "service_records.csv" to serviceCsv(data.serviceRecords.forVehicles { it.vehicleId }, byId, zone),
            "expenses.csv" to expenseCsv(data.expenses.forVehicles { it.vehicleId }, byId, zone),
            "reminders.csv" to reminderCsv(data.reminders.forVehicles { it.vehicleId }, byId, zone),
            "documents.csv" to documentCsv(data.documents.forVehicles { it.vehicleId }, byId, zone),
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in tables) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(README.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun vehiclesCsv(rows: List<Vehicle>, zone: ZoneId) = table(
        listOf(
            "id", "name", "type", "powertrain", "make", "model", "year", "plate", "vin", "distance_unit", "volume_unit",
            "currency", "tank_capacity", "battery_kwh", "archived", "sold_at", "created_at",
        ),
        rows.map {
            listOf(
                it.id, text(it.name), it.type, it.powertrain, text(it.make), text(it.model), it.year, text(it.plate), text(it.vin),
                it.distanceUnit, it.volumeUnit, it.currency, num(it.tankCapacity), num(it.batteryKwh), it.archived,
                dateTime(it.soldAt, zone), dateTime(it.createdAt, zone),
            )
        },
    )

    private fun energyCsv(rows: List<EnergyLog>, vehicles: Map<Long, Vehicle>, zone: ZoneId) = table(
        listOf(
            "id", "vehicle_id", "vehicle_name", "stream", "date_time", "odometer", "distance_unit", "quantity", "quantity_unit",
            "price_per_unit", "total", "currency", "is_full", "missed_previous", "fuel_grade", "charger_type", "soc_start",
            "soc_end", "station", "note",
        ),
        rows.map { r ->
            val v = vehicles[r.vehicleId]
            val digits = digits(v?.currency)
            listOf(
                r.id, r.vehicleId, text(v?.name), r.stream, dateTime(r.dateTime, zone), num(r.odometer), v?.distanceUnit, num(r.quantity),
                when (r.stream) {
                    EnergyStream.ELECTRIC -> "kWh"
                    EnergyStream.GAS_KG -> "kg"
                    EnergyStream.LIQUID -> (v?.volumeUnit ?: VolumeUnit.L).name
                },
                money(r.pricePerUnitMinor, digits), money(r.totalMinor, digits), v?.currency, r.isFull, r.missedPrevious,
                text(r.fuelGrade), r.chargerType, r.socStart, r.socEnd, text(r.station), text(r.note),
            )
        },
    )

    private fun serviceCsv(rows: List<ServiceRecord>, vehicles: Map<Long, Vehicle>, zone: ZoneId) = table(
        listOf("id", "vehicle_id", "vehicle_name", "date_time", "odometer", "distance_unit", "items", "total", "currency", "workshop", "note"),
        rows.map { r ->
            val v = vehicles[r.vehicleId]
            listOf(
                r.id, r.vehicleId, text(v?.name), dateTime(r.dateTime, zone), num(r.odometer), v?.distanceUnit,
                text(r.items.joinToString(" | ")), money(r.totalMinor, digits(v?.currency)), v?.currency, text(r.workshop), text(r.note),
            )
        },
    )

    private fun expenseCsv(rows: List<Expense>, vehicles: Map<Long, Vehicle>, zone: ZoneId) = table(
        listOf("id", "vehicle_id", "vehicle_name", "date_time", "odometer", "distance_unit", "category", "amount", "currency", "recurring", "note"),
        rows.map { r ->
            val v = vehicles[r.vehicleId]
            listOf(
                r.id, r.vehicleId, text(v?.name), dateTime(r.dateTime, zone), num(r.odometer), v?.distanceUnit, r.category,
                money(r.amountMinor, digits(v?.currency)), v?.currency, r.recurring, text(r.note),
            )
        },
    )

    private fun reminderCsv(rows: List<Reminder>, vehicles: Map<Long, Vehicle>, zone: ZoneId) = table(
        listOf(
            "id", "vehicle_id", "vehicle_name", "title", "kind", "due_date", "due_odometer", "distance_unit", "repeat_months",
            "repeat_distance", "lead_days", "lead_distance", "done",
        ),
        rows.map { r ->
            val v = vehicles[r.vehicleId]
            listOf(
                r.id, r.vehicleId, text(v?.name), text(r.title), r.kind, date(r.dueDate, zone), num(r.dueOdometer), v?.distanceUnit,
                r.repeatMonths, num(r.repeatDistance), r.leadDays, num(r.leadDistance), r.done,
            )
        },
    )

    private fun documentCsv(rows: List<VehicleDocument>, vehicles: Map<Long, Vehicle>, zone: ZoneId) = table(
        listOf("id", "vehicle_id", "vehicle_name", "type", "title", "number", "issue_date", "expiry_date", "note"),
        rows.map { r ->
            listOf(
                r.id, r.vehicleId, text(vehicles[r.vehicleId]?.name), r.type, text(r.title), text(r.number),
                date(r.issueDate, zone), date(r.expiryDate, zone), text(r.note),
            )
        },
    )

    // ---- formatting ----

    private fun table(header: List<String>, rows: List<List<Any?>>): String =
        (listOf(header) + rows).joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",") { cell(it) } }

    private fun cell(value: Any?): String {
        val s = value?.toString() ?: return ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** Free text. A leading = + - @ would run as a formula when the file is opened in a spreadsheet, so it gets a quote prefix. */
    private fun text(value: String?): String? =
        value?.let { if (it.isNotEmpty() && it[0] in "=+-@\t\r") "'$it" else it }

    private fun num(d: Double?): String? = d?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }

    private fun digits(currency: String?): Int =
        try {
            currency?.let { Currency.getInstance(it).defaultFractionDigits.coerceAtLeast(0) } ?: 2
        } catch (e: IllegalArgumentException) {
            2
        }

    private fun money(minor: Long?, digits: Int): String? = minor?.let { BigDecimal.valueOf(it).movePointLeft(digits).toPlainString() }

    private fun dateTime(ms: Long?, zone: ZoneId): String? =
        ms?.let { Instant.ofEpochMilli(it).atZone(zone).toOffsetDateTime().withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) }

    private fun date(ms: Long?, zone: ZoneId): String? =
        ms?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }

    private const val README = "Wheelfile CSV export\r\n" +
        "One CSV file per table. UTF-8, comma separated, dates in ISO 8601 (date_time with UTC offset, other dates as yyyy-MM-dd),\r\n" +
        "decimals with a dot, money in major units of the vehicle's currency (see the currency column).\r\n" +
        "Distances use the vehicle's distance_unit (KM or MI); fuel quantities use quantity_unit (L, US_GAL, UK_GAL, KG, kWh).\r\n"
}
