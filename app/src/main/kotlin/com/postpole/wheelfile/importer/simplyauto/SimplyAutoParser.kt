package com.postpole.wheelfile.importer.simplyauto

import com.postpole.wheelfile.importer.Columns
import com.postpole.wheelfile.importer.Csv
import com.postpole.wheelfile.importer.EntryClassifier
import com.postpole.wheelfile.importer.ImportFormat
import com.postpole.wheelfile.importer.ImportInput
import com.postpole.wheelfile.importer.ParsedEnergy
import com.postpole.wheelfile.importer.ParsedExpense
import com.postpole.wheelfile.importer.ParsedFile
import com.postpole.wheelfile.importer.ParsedService
import com.postpole.wheelfile.importer.ParsedVehicle
import com.postpole.wheelfile.importer.Parse
import com.postpole.wheelfile.importer.RowIssue
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The CSV set from the publisher's backup folder (spec §4.4): `Vehicles.csv` and `Fuel_Log.csv`, where
 * `Record Type` is 0 for fuel, 1 for service and 2 for expense. The optional `Services.csv` and `Trip_Log.csv`
 * are not read: services and expenses are already in `Fuel_Log.csv`, and trips aren't part of Wheelfile.
 * The files carry no units and no currency, so the preview asks for them.
 */
object SimplyAutoParser {
    private const val VEHICLES = "vehicles.csv"
    private const val FUEL_LOG = "fuel_log.csv"

    private fun find(input: ImportInput, lowerName: String): String? =
        input.files.keys.firstOrNull { it.substringAfterLast('/').lowercase() == lowerName }

    fun looksLike(input: ImportInput): Boolean = find(input, FUEL_LOG) != null || find(input, VEHICLES) != null

    fun parse(input: ImportInput, zone: ZoneId): ParsedFile {
        val issues = ArrayList<RowIssue>()
        val vehicleFile = find(input, VEHICLES)
        val logFile = find(input, FUEL_LOG)

        class Draft(val id: String, var vehicle: ParsedVehicle)
        val drafts = LinkedHashMap<String, Draft>()

        if (vehicleFile != null) {
            val rows = Csv.parse(input.text(vehicleFile), ',').filter { !it.isBlank }
            val cols = rows.firstOrNull()?.let { Columns(it.cells) }
            if (cols != null) {
                val iId = cols.find("vehicle id") ?: cols.find("row id") ?: 0
                val iMake = cols.exact("make")
                val iModel = cols.exact("model")
                val iYear = cols.exact("year")
                val iPlate = cols.find("lic")
                val iVin = cols.exact("vin")
                for (row in rows.drop(1)) {
                    val id = row.cell(iId)
                    if (id == null) {
                        issues.add(RowIssue(vehicleFile, row.line, "Missing vehicle ID"))
                        continue
                    }
                    val make = iMake?.let { row.cell(it) }
                    val model = iModel?.let { row.cell(it) }
                    drafts[id] = Draft(
                        id,
                        ParsedVehicle(
                            key = id,
                            name = listOfNotNull(make, model).joinToString(" ").ifBlank { "Imported vehicle $id" },
                            make = make, model = model,
                            year = iYear?.let { row.cell(it) }?.toIntOrNull()?.takeIf { it > 1800 },
                            plate = iPlate?.let { row.cell(it) }, vin = iVin?.let { row.cell(it) },
                        ),
                    )
                }
            }
        }

        if (logFile != null) {
            val rows = Csv.parse(input.text(logFile), ',').filter { !it.isBlank }
            val cols = rows.firstOrNull()?.let { Columns(it.cells) }
            if (cols != null) {
                val iVehicle = cols.find("vehicle id") ?: 1
                val iOdo = cols.find("odometer") ?: 2
                val iQty = cols.find("qty", "quantity") ?: 3
                val iPartial = cols.find("partial")
                val iMissed = cols.find("missed")
                val iCost = cols.find("total cost") ?: 6
                val iOctane = cols.find("octane")
                val iStation = cols.find("filling station", "station")
                val iBrand = cols.find("fuel brand")
                val iNotes = cols.find("notes")
                val iDay = cols.exact("day")
                val iMonth = cols.exact("month")
                val iYear = cols.exact("year")
                val iType = cols.find("record type")
                val iDesc = cols.find("record desc")
                val zoneNoon = LocalTime.NOON
                val energy = HashMap<String, MutableList<ParsedEnergy>>()
                val services = HashMap<String, MutableList<ParsedService>>()
                val expenses = HashMap<String, MutableList<ParsedExpense>>()

                for (row in rows.drop(1)) {
                    val id = row.cell(iVehicle)
                    val day = iDay?.let { row.cell(it) }?.toIntOrNull()
                    val month = iMonth?.let { row.cell(it) }?.toIntOrNull()
                    val year = iYear?.let { row.cell(it) }?.toIntOrNull()
                    val date = try {
                        if (day == null || month == null || year == null) null
                        else LocalDateTime.of(year, month, day, zoneNoon.hour, 0).atZone(zone).toInstant().toEpochMilli()
                    } catch (e: java.time.DateTimeException) {
                        null
                    }
                    val odo = Parse.number(row.cell(iOdo), threeDigitsAreGrouping = true)
                    val cost = Parse.number(row.cell(iCost))
                    val type = iType?.let { row.cell(it) }?.toIntOrNull() ?: 0
                    val note = iNotes?.let { row.cell(it) }
                    when {
                        id == null -> issues.add(RowIssue(logFile, row.line, "Missing vehicle ID"))
                        id !in drafts -> {
                            if (vehicleFile == null) {
                                drafts[id] = Draft(id, ParsedVehicle(key = id, name = "Imported vehicle $id"))
                            }
                        }
                    }
                    if (id == null) continue
                    if (id !in drafts) {
                        issues.add(RowIssue(logFile, row.line, "Unknown vehicle ID $id"))
                        continue
                    }
                    if (date == null) {
                        issues.add(RowIssue(logFile, row.line, "Unreadable date"))
                        continue
                    }
                    when (type) {
                        0 -> {
                            val qty = Parse.number(row.cell(iQty))
                            if (odo == null) {
                                issues.add(RowIssue(logFile, row.line, "Unreadable odometer"))
                            } else if (qty == null || qty <= 0.0) {
                                issues.add(RowIssue(logFile, row.line, "Unreadable fuel quantity"))
                            } else {
                                energy.getOrPut(id) { ArrayList() }.add(
                                    ParsedEnergy(
                                        line = row.line, dateTime = date, odometer = odo, quantity = qty,
                                        totalMajor = cost?.takeIf { it > 0 }, pricePerUnitMajor = null,
                                        isFull = !(iPartial?.let { Parse.bool(row.cell(it)) } ?: false),
                                        missedPrevious = iMissed?.let { Parse.bool(row.cell(it)) } ?: false,
                                        fuelGrade = iOctane?.let { row.cell(it) }?.takeIf { it != "0" } ?: iBrand?.let { row.cell(it) },
                                        station = iStation?.let { row.cell(it) },
                                        note = note,
                                    ),
                                )
                            }
                        }
                        1, 2 -> {
                            val title = iDesc?.let { row.cell(it) } ?: if (type == 1) "Service" else "Expense"
                            if (cost == null) {
                                issues.add(RowIssue(logFile, row.line, "Unreadable cost"))
                            } else {
                                val kind = if (type == 1) EntryClassifier.Kind.Service else EntryClassifier.classify(title)
                                when (kind) {
                                    EntryClassifier.Kind.Service ->
                                        services.getOrPut(id) { ArrayList() }.add(ParsedService(row.line, date, odo?.takeIf { it > 0 }, title, cost, note))
                                    is EntryClassifier.Kind.Expense ->
                                        expenses.getOrPut(id) { ArrayList() }.add(ParsedExpense(row.line, date, odo?.takeIf { it > 0 }, kind.category, title, cost, note))
                                }
                            }
                        }
                        else -> issues.add(RowIssue(logFile, row.line, "Unknown record type $type"))
                    }
                }
                for ((id, d) in drafts) {
                    d.vehicle = d.vehicle.copy(
                        energy = energy[id].orEmpty(),
                        services = services[id].orEmpty(),
                        expenses = expenses[id].orEmpty(),
                    )
                }
            }
        }
        return ParsedFile(ImportFormat.SIMPLY_AUTO, drafts.values.map { it.vehicle }, issues)
    }
}
