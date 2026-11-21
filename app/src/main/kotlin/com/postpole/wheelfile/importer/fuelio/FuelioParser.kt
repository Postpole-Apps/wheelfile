package com.postpole.wheelfile.importer.fuelio

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.importer.CostClassifier
import com.postpole.wheelfile.importer.Csv
import com.postpole.wheelfile.importer.CsvSection
import com.postpole.wheelfile.importer.DateParser
import com.postpole.wheelfile.importer.DecimalStyle
import com.postpole.wheelfile.importer.ImportFile
import com.postpole.wheelfile.importer.NumberParser
import com.postpole.wheelfile.importer.RowError
import com.postpole.wheelfile.importer.Sections
import com.postpole.wheelfile.importer.SlashOrder
import com.postpole.wheelfile.importer.SourceExpense
import com.postpole.wheelfile.importer.SourceFill
import com.postpole.wheelfile.importer.SourceService
import com.postpole.wheelfile.importer.SourceVehicle
import com.postpole.wheelfile.importer.cell
import java.time.ZoneId

/**
 * Fuelio CSV (one vehicle per file, sections `## Vehicle`, `## Log`, `## CostCategories`, `## Costs`; spec §4.4).
 * A "Backup All" ZIP is a set of such CSVs and is handled by calling this once per CSV.
 * Columns are found by header name (labels carry units, e.g. "Odo (km)"), so newer files with extra columns still parse.
 */
object FuelioParser {
    private val sectionLine = Regex("^\\s*\"?##\\s*(log|costs)\\b", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    fun looksLike(text: String): Boolean = sectionLine.containsMatchIn(text)

    fun parse(file: ImportFile, zone: ZoneId): Pair<SourceVehicle?, List<RowError>> {
        val text = file.text()
        val sections = Sections.split(text, Csv.sniffDelimiter(text))
        fun section(name: String) = sections.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val errors = ArrayList<RowError>()
        val vehicleSection = section("Vehicle")
        val log = section("Log")
        val costs = section("Costs")
        if (log == null && costs == null) return null to errors

        val numberSamples = sequence {
            for (s in listOfNotNull(log, costs)) for (r in s.rows) yieldAll(r.cells)
        }
        val style = DecimalStyle.sniff(numberSamples)
        val dates = DateParser(
            DateParser.sniffOrder(
                sequence { for (s in listOfNotNull(log, costs)) for (r in s.rows) yieldAll(r.cells) },
                SlashOrder.DMY,
            ),
            zone,
        )

        var name = file.baseName.substringBeforeLast('.').ifBlank { "Vehicle" }
        var make: String? = null
        var model: String? = null
        var year: Int? = null
        var plate: String? = null
        var vin: String? = null
        var distance: DistanceUnit? = null
        var volume: VolumeUnit? = null
        vehicleSection?.rows?.firstOrNull { !it.isBlank }?.let { row ->
            val s = vehicleSection
            row.cell(s.col("Name"))?.let { name = it }
            make = row.cell(s.col("Make"))
            model = row.cell(s.col("Model"))
            year = row.cell(s.col("Year"))?.toIntOrNull()
            plate = row.cell(s.col("Plate"))
            vin = row.cell(s.col("VIN"))
            distance = when (row.cell(s.col("DistUnit"))) {
                "0" -> DistanceUnit.KM
                "1" -> DistanceUnit.MI
                else -> null
            }
            volume = when (row.cell(s.col("FuelUnit"))) {
                "0" -> VolumeUnit.L
                "1" -> VolumeUnit.US_GAL
                "2" -> VolumeUnit.UK_GAL
                else -> null
            }
        }

        val fills = ArrayList<SourceFill>()
        if (log != null) {
            val cDate = log.col("Data", "Date") ?: log.colStartingWith("data", "date")
            val cOdo = log.colStartingWith("Odo")
            val cQty = log.col("Fuel") // "Fuel (litres)"; FuelType is a different header
            val cFull = log.col("Full")
            val cTotal = log.colStartingWith("Price")
            val cUnitPrice = log.col("VolumePrice")
            val cCity = log.col("City")
            val cNotes = log.col("Notes")
            val cMissed = log.col("Missed")
            if (cDate == null || cOdo == null || cQty == null) {
                errors += RowError(file.baseName, log.header?.line ?: 1, "The fill-up table is missing its date, odometer or fuel column")
            } else {
                for (row in log.rows) {
                    if (row.isBlank) continue
                    val date = dates.parse(row.cell(cDate))
                    val odo = NumberParser.parse(row.cell(cOdo), style)
                    val qty = NumberParser.parse(row.cell(cQty), style)
                    when {
                        date == null -> errors += RowError(file.baseName, row.line, "Unreadable date")
                        odo == null -> errors += RowError(file.baseName, row.line, "Unreadable odometer")
                        qty == null || qty <= 0.0 -> errors += RowError(file.baseName, row.line, "Unreadable fuel amount")
                        else -> fills += SourceFill(
                            line = row.line, dateTime = date, odometer = odo, quantity = qty,
                            pricePerUnit = NumberParser.parse(row.cell(cUnitPrice), style)?.takeIf { it > 0 },
                            total = NumberParser.parse(row.cell(cTotal), style)?.takeIf { it > 0 },
                            isFull = row.cell(cFull) == "1",
                            missedPrevious = row.cell(cMissed) == "1",
                            station = row.cell(cCity), note = row.cell(cNotes),
                        )
                    }
                }
            }
        }

        val categoryNames = section("CostCategories")?.rows
            ?.filter { !it.isBlank }
            ?.map { it.cells.firstOrNull()?.trim().orEmpty() }
            .orEmpty()

        val services = ArrayList<SourceService>()
        val expenses = ArrayList<SourceExpense>()
        if (costs != null) {
            val cTitle = costs.col("CostTitle")
            val cDate = costs.col("Date")
            val cOdo = costs.colStartingWith("Odo")
            val cType = costs.col("CostTypeID")
            val cNotes = costs.col("Notes")
            val cCost = costs.col("Cost")
            val cTemplate = costs.col("isTemplate")
            val cIncome = costs.col("isIncome")
            if (cDate == null || cCost == null) {
                errors += RowError(file.baseName, costs.header?.line ?: 1, "The costs table is missing its date or cost column")
            } else {
                for (row in costs.rows) {
                    if (row.isBlank) continue
                    if (row.cell(cTemplate) == "1") continue // repeating-cost templates are not entries
                    val date = dates.parse(row.cell(cDate))
                    val amount = NumberParser.parse(row.cell(cCost), style)
                    when {
                        row.cell(cIncome) == "1" -> errors += RowError(file.baseName, row.line, "Income entries are not supported")
                        date == null -> errors += RowError(file.baseName, row.line, "Unreadable date")
                        amount == null -> errors += RowError(file.baseName, row.line, "Unreadable cost")
                        else -> {
                            val title = row.cell(cTitle) ?: "Cost"
                            val categoryName = row.cell(cType)?.toIntOrNull()?.let { categoryNames.getOrNull(it - 1) }.orEmpty()
                            val odo = NumberParser.parse(row.cell(cOdo), style)?.takeIf { it > 0 }
                            when (val kind = CostClassifier.classify("$title $categoryName")) {
                                CostClassifier.Kind.Service ->
                                    services += SourceService(row.line, date, odo, title, amount, row.cell(cNotes))
                                is CostClassifier.Kind.Expense ->
                                    expenses += SourceExpense(row.line, date, odo, title, amount, kind.category, row.cell(cNotes))
                            }
                        }
                    }
                }
            }
        }

        val vehicle = SourceVehicle(
            key = file.name, name = name, make = make, model = model, year = year, plate = plate, vin = vin,
            distanceUnit = distance, volumeUnit = volume, fills = fills, services = services, expenses = expenses,
        )
        return vehicle to errors
    }
}
