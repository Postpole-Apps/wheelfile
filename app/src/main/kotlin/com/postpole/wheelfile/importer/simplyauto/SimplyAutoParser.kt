package com.postpole.wheelfile.importer.simplyauto

import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.importer.CostClassifier
import com.postpole.wheelfile.importer.Csv
import com.postpole.wheelfile.importer.CsvSection
import com.postpole.wheelfile.importer.DecimalStyle
import com.postpole.wheelfile.importer.ImportFile
import com.postpole.wheelfile.importer.NumberParser
import com.postpole.wheelfile.importer.RowError
import com.postpole.wheelfile.importer.SourceExpense
import com.postpole.wheelfile.importer.SourceFill
import com.postpole.wheelfile.importer.SourceService
import com.postpole.wheelfile.importer.SourceVehicle
import com.postpole.wheelfile.importer.cell
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

/**
 * Simply Auto's backup CSVs (spec §4.4): `Vehicles.csv`, `Fuel_Log.csv` (record type 0 fuel / 1 service / 2 expense),
 * optional `Services.csv`; `Trip_Log.csv` is ignored. The files carry no units, so units stay unset (device defaults).
 */
object SimplyAutoParser {
    private fun section(file: ImportFile): CsvSection {
        val text = file.text()
        val rows = Csv.parse(text, Csv.sniffDelimiter(text)).filter { !it.isBlank }
        return CsvSection(file.baseName, rows.firstOrNull(), rows.drop(1))
    }

    private fun headers(file: ImportFile): Set<String> =
        section(file).header?.cells?.map(CsvSection::normaliseHeader)?.toSet().orEmpty()

    fun isFuelLog(file: ImportFile): Boolean = headers(file).let { "partialtank" in it && "recordtype" in it }

    fun isVehicles(file: ImportFile): Boolean =
        headers(file).let { "vehicleid" in it && ("lic" in it || "make" in it) && "partialtank" !in it }

    fun isServices(file: ImportFile): Boolean =
        headers(file).let { "vehicleid" in it && "partialtank" !in it && ("make" !in it) && ("day" in it || "date" in it) }

    fun parse(files: List<ImportFile>, zone: ZoneId): Pair<List<SourceVehicle>, List<RowError>> {
        val vehicleFile = files.firstOrNull(::isVehicles)
        val fuelFile = files.firstOrNull(::isFuelLog)
        val serviceFile = files.firstOrNull { it !== vehicleFile && it !== fuelFile && isServices(it) }
        val errors = ArrayList<RowError>()

        data class Acc(
            val vehicle: SourceVehicle,
            val fills: ArrayList<SourceFill> = ArrayList(),
            val services: ArrayList<SourceService> = ArrayList(),
            val expenses: ArrayList<SourceExpense> = ArrayList(),
        )
        val byId = LinkedHashMap<String, Acc>()

        if (vehicleFile != null) {
            val s = section(vehicleFile)
            for (row in s.rows) {
                val id = row.cell(s.col("Vehicle ID")) ?: continue
                val make = row.cell(s.col("Make"))
                val model = row.cell(s.col("Model"))
                val year = row.cell(s.col("Year"))?.toIntOrNull()
                val name = listOfNotNull(make, model).joinToString(" ").ifBlank { "Vehicle $id" }
                byId[id] = Acc(
                    SourceVehicle(
                        key = id, name = name, make = make, model = model, year = year,
                        plate = row.cell(s.colStartingWith("Lic")), vin = row.cell(s.col("VIN")),
                    ),
                )
            }
        }
        fun acc(id: String) = byId.getOrPut(id) { Acc(SourceVehicle(key = id, name = "Vehicle $id")) }

        fun date(row: com.postpole.wheelfile.importer.CsvRow, s: CsvSection): Long? {
            val d = row.cell(s.col("Day"))?.toIntOrNull()
            val m = row.cell(s.col("Month"))?.toIntOrNull()
            val y = row.cell(s.col("Year"))?.toIntOrNull()
            if (d == null || m == null || y == null) return null
            return try {
                LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeException) {
                null
            }
        }

        if (fuelFile != null) {
            val s = section(fuelFile)
            val style = DecimalStyle.sniff(sequence { for (r in s.rows) yieldAll(r.cells) })
            val cId = s.col("Vehicle ID")
            val cOdo = s.col("Odometer")
            val cQty = s.col("Qty", "Quantity")
            val cPartial = s.col("Partial Tank")
            val cMissed = s.col("Missed Fill Up")
            val cTotal = s.col("Total Cost")
            val cOctane = s.col("Octane")
            val cBrand = s.col("Filling Station")
            val cNotes = s.col("Notes")
            val cType = s.col("Record Type")
            val cDesc = s.col("Record Desc")
            for (row in s.rows) {
                val a = acc(row.cell(cId) ?: "1")
                val date = date(row, s)
                val odo = NumberParser.parse(row.cell(cOdo), style)
                val total = NumberParser.parse(row.cell(cTotal), style)
                val type = row.cell(cType) ?: "0"
                val title = row.cell(cDesc)
                when {
                    date == null -> errors += RowError(fuelFile.baseName, row.line, "Unreadable date")
                    type == "1" -> a.services += SourceService(
                        row.line, date, odo?.takeIf { it > 0 }, title ?: "Service", total, row.cell(cNotes),
                    )
                    type == "2" -> if (total == null) {
                        errors += RowError(fuelFile.baseName, row.line, "Unreadable cost")
                    } else {
                        val category = (CostClassifier.classify(title.orEmpty()) as? CostClassifier.Kind.Expense)?.category
                            ?: ExpenseCategory.OTHER
                        a.expenses += SourceExpense(row.line, date, odo?.takeIf { it > 0 }, title ?: "Expense", total, category, row.cell(cNotes))
                    }
                    else -> {
                        val qty = NumberParser.parse(row.cell(cQty), style)
                        when {
                            odo == null -> errors += RowError(fuelFile.baseName, row.line, "Unreadable odometer")
                            qty == null || qty <= 0.0 -> errors += RowError(fuelFile.baseName, row.line, "Unreadable quantity")
                            else -> a.fills += SourceFill(
                                line = row.line, dateTime = date, odometer = odo, quantity = qty,
                                pricePerUnit = null, total = total?.takeIf { it > 0 },
                                isFull = row.cell(cPartial) != "1", missedPrevious = row.cell(cMissed) == "1",
                                station = row.cell(cBrand), note = row.cell(cNotes), fuelGrade = row.cell(cOctane),
                            )
                        }
                    }
                }
            }
        }

        if (serviceFile != null) {
            val s = section(serviceFile)
            val style = DecimalStyle.sniff(sequence { for (r in s.rows) yieldAll(r.cells) })
            val cId = s.col("Vehicle ID")
            val cOdo = s.col("Odometer")
            val cCost = s.col("Total Cost", "Cost")
            val cDesc = s.col("Service", "Service Type", "Description", "Record Desc")
            val cNotes = s.col("Notes")
            for (row in s.rows) {
                val a = acc(row.cell(cId) ?: "1")
                val date = date(row, s)
                if (date == null) {
                    errors += RowError(serviceFile.baseName, row.line, "Unreadable date")
                    continue
                }
                a.services += SourceService(
                    row.line, date, NumberParser.parse(row.cell(cOdo), style)?.takeIf { it > 0 },
                    row.cell(cDesc) ?: "Service", NumberParser.parse(row.cell(cCost), style), row.cell(cNotes),
                )
            }
        }

        val vehicles = byId.values.map {
            it.vehicle.copy(fills = it.fills, services = it.services, expenses = it.expenses)
        }
        return vehicles to errors
    }
}
