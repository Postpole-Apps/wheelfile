package com.postpole.wheelfile.data.backup

import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.importer.ImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Wheelfile CSV export (spec §2.8): a ZIP with one UTF-8 CSV per table. Dates are ISO 8601 in UTC, decimals always use a
 * dot, money is written in major units next to its currency, and every distance or volume column has an explicit unit column.
 * Free on every tier.
 */
class CsvExporter @Inject constructor(private val db: AppDatabase) {

    /** Writes the ZIP for [vehicleId], or for every vehicle when null. */
    suspend fun export(out: OutputStream, vehicleId: Long? = null) = withContext(Dispatchers.IO) {
        val vehicles = db.vehicleDao().getAll().filter { vehicleId == null || it.id == vehicleId }.sortedBy { it.id }
        val zip = ZipOutputStream(out)
        fun file(name: String, header: List<String>, rows: List<List<String>>) {
            zip.putNextEntry(ZipEntry(name))
            val text = buildString {
                append(csvLine(header))
                rows.forEach { append(csvLine(it)) }
            }
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        file(
            "vehicles.csv",
            listOf(
                "vehicle_id", "name", "type", "powertrain", "make", "model", "year", "plate", "vin", "distance_unit", "volume_unit",
                "currency", "tank_capacity", "battery_kwh", "archived",
            ),
            vehicles.map {
                listOf(
                    it.id.toString(), text(it.name), it.type.name, it.powertrain.name, text(it.make), text(it.model), num(it.year),
                    text(it.plate), text(it.vin), it.distanceUnit.name, it.volumeUnit.name, it.currency, num(it.tankCapacity),
                    num(it.batteryKwh), it.archived.toString(),
                )
            },
        )

        val fills = ArrayList<List<String>>()
        val services = ArrayList<List<String>>()
        val expenses = ArrayList<List<String>>()
        val documents = ArrayList<List<String>>()
        for (v in vehicles) {
            val digits = ImportService.fractionDigits(v.currency)
            db.energyLogDao().getAll(v.id).forEach {
                fills += listOf(
                    v.id.toString(), text(v.name), iso(it.dateTime), num(it.odometer), v.distanceUnit.name, it.stream.name,
                    num(it.quantity), if (it.stream.name == "ELECTRIC") "KWH" else v.volumeUnit.name,
                    money(it.pricePerUnitMinor, digits), money(it.totalMinor, digits), v.currency, it.isFull.toString(),
                    it.missedPrevious.toString(), text(it.fuelGrade), it.chargerType?.name.orEmpty(), num(it.socStart), num(it.socEnd),
                    text(it.station), text(it.note),
                )
            }
            db.serviceRecordDao().getForVehicle(v.id).forEach {
                services += listOf(
                    v.id.toString(), text(v.name), iso(it.dateTime), num(it.odometer), v.distanceUnit.name,
                    text(it.items.joinToString("; ")), money(it.totalMinor, digits), v.currency, text(it.workshop), text(it.note),
                )
            }
            db.expenseDao().getForVehicle(v.id).forEach {
                expenses += listOf(
                    v.id.toString(), text(v.name), iso(it.dateTime), num(it.odometer), v.distanceUnit.name, it.category.name,
                    money(it.amountMinor, digits), v.currency, it.recurring?.name.orEmpty(), text(it.note),
                )
            }
            db.vehicleDocumentDao().getAll().filter { it.vehicleId == v.id }.forEach {
                documents += listOf(
                    v.id.toString(), text(v.name), it.type.name, text(it.title), text(it.number),
                    it.issueDate?.let(::iso).orEmpty(), it.expiryDate?.let(::iso).orEmpty(), text(it.note),
                )
            }
        }
        file(
            "fillups.csv",
            listOf(
                "vehicle_id", "vehicle", "date", "odometer", "distance_unit", "stream", "quantity", "quantity_unit",
                "price_per_unit", "total", "currency", "full_tank", "missed_previous", "fuel_grade", "charger_type",
                "soc_start_percent", "soc_end_percent", "station", "note",
            ),
            fills,
        )
        file(
            "services.csv",
            listOf("vehicle_id", "vehicle", "date", "odometer", "distance_unit", "items", "total", "currency", "workshop", "note"),
            services,
        )
        file(
            "expenses.csv",
            listOf("vehicle_id", "vehicle", "date", "odometer", "distance_unit", "category", "amount", "currency", "recurring", "note"),
            expenses,
        )
        file(
            "documents.csv",
            listOf("vehicle_id", "vehicle", "type", "title", "number", "issue_date", "expiry_date", "note"),
            documents,
        )
        zip.finish()
        out.flush()
    }

    companion object {
        private val ISO = DateTimeFormatter.ISO_INSTANT

        fun iso(millis: Long): String = ISO.format(Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).withNano(0))

        /** Locale-independent number: plain digits with a dot, no exponent, no grouping. */
        fun num(value: Number?): String = when (value) {
            null -> ""
            is Double -> BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
            else -> value.toString()
        }

        fun money(minor: Long?, digits: Int): String =
            minor?.let { BigDecimal.valueOf(it).movePointLeft(digits).setScale(digits).toPlainString() }.orEmpty()

        /** Free text; a leading = + - @ is prefixed with an apostrophe so spreadsheets never run it as a formula. */
        fun text(value: String?): String {
            val v = value ?: return ""
            return if (v.isNotEmpty() && v[0] in "=+-@\t\r") "'$v" else v
        }

        fun csvLine(cells: List<String>): String =
            cells.joinToString(",", postfix = "\r\n") { c ->
                if (c.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${c.replace("\"", "\"\"")}\"" else c
            }
    }
}
