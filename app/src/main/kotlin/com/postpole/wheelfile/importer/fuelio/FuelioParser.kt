package com.postpole.wheelfile.importer.fuelio

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.importer.Columns
import com.postpole.wheelfile.importer.Csv
import com.postpole.wheelfile.importer.CsvRow
import com.postpole.wheelfile.importer.DateOrder
import com.postpole.wheelfile.importer.DateParser
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
import com.postpole.wheelfile.importer.Section
import com.postpole.wheelfile.importer.Sections
import java.time.ZoneId

/**
 * Fuelio CSV (one vehicle per file, sections `## Vehicle`, `## Log`, `## CostCategories`, `## Costs`) and the
 * "Backup All" ZIP that holds one such CSV per vehicle (spec §4.4). The file carries no currency.
 */
object FuelioParser {
    fun looksLike(text: String): Boolean {
        val headings = Sections.split(Csv.parse(text, ','), ::heading).map { Parse.fold(it.name) }
        return "vehicle" in headings && "log" in headings
    }

    private fun heading(row: CsvRow): String? = Sections.hashHeading(row)

    fun parse(input: ImportInput, zone: ZoneId): ParsedFile {
        val csvs = input.files.filterKeys { name -> input.files.getValue(name).let { looksLike(ImportInput.decode(it)) } }
        val vehicles = ArrayList<ParsedVehicle>()
        val issues = ArrayList<RowIssue>()
        for ((name, bytes) in csvs) {
            parseCsv(name, ImportInput.decode(bytes), zone, vehicles, issues)
        }
        val format = if (input.files.size > 1) ImportFormat.FUELIO_BACKUP_ZIP else ImportFormat.FUELIO_CSV
        return ParsedFile(format, vehicles, issues)
    }

    private fun parseCsv(file: String, text: String, zone: ZoneId, vehicles: MutableList<ParsedVehicle>, issues: MutableList<RowIssue>) {
        val sections = Sections.split(Csv.parse(text, ','), ::heading).associateBy { Parse.fold(it.name) }
        val vehicleSection = sections["vehicle"]
        val vehicleCols = vehicleSection?.rows?.firstOrNull()?.let { Columns(it.cells) }
        val vehicleRow = vehicleSection?.rows?.getOrNull(1)

        fun vcell(vararg needles: String): String? =
            vehicleCols?.exact(*needles)?.let { vehicleRow?.cell(it) }

        val distance = when (vcell("distunit")?.trim()) {
            "1" -> DistanceUnit.MI
            "0" -> DistanceUnit.KM
            else -> null
        }
        val volume = when (vcell("fuelunit")?.trim()) {
            "1" -> VolumeUnit.US_GAL
            "2" -> VolumeUnit.UK_GAL
            "0" -> VolumeUnit.L
            else -> null
        }
        val key = file
        val log = sections["log"]
        val costs = sections["costs"]
        val categories = categoryNames(sections["costcategories"])

        val logColumns = log?.rows?.firstOrNull()?.let { Columns(it.cells) }
        val order = DateOrder.DAY_FIRST
        val dates = DateParser(zone, order)
        val energy = ArrayList<ParsedEnergy>()
        if (log != null && logColumns != null) {
            val iDate = logColumns.find("data", "date") ?: 0
            val iOdo = logColumns.find("odo") ?: 1
            val iFuel = logColumns.find("fuel (", "fuel") ?: 2
            val iFull = logColumns.exact("full") ?: 3
            val iPrice = logColumns.exact("price (optional)", "price") ?: logColumns.find("price") ?: 4
            val iUnitPrice = logColumns.exact("volumeprice")
            val iCity = logColumns.find("city")
            val iNotes = logColumns.find("notes")
            val iMissed = logColumns.exact("missed")
            for (row in log.rows.drop(1)) {
                val date = dates.parse(row.cell(iDate))
                val odo = Parse.number(row.cell(iOdo), threeDigitsAreGrouping = true)
                val qty = Parse.number(row.cell(iFuel))
                when {
                    date == null -> issues.add(RowIssue(file, row.line, "Unreadable date"))
                    odo == null -> issues.add(RowIssue(file, row.line, "Unreadable odometer"))
                    qty == null || qty <= 0.0 -> issues.add(RowIssue(file, row.line, "Unreadable fuel quantity"))
                    else -> energy.add(
                        ParsedEnergy(
                            line = row.line, dateTime = date, odometer = odo, quantity = qty,
                            totalMajor = Parse.number(row.cell(iPrice))?.takeIf { it > 0 },
                            pricePerUnitMajor = iUnitPrice?.let { Parse.number(row.cell(it)) }?.takeIf { it > 0 },
                            isFull = Parse.bool(row.cell(iFull)) ?: false,
                            missedPrevious = iMissed?.let { Parse.bool(row.cell(it)) } ?: false,
                            station = iCity?.let { row.cell(it) },
                            note = iNotes?.let { row.cell(it) },
                        ),
                    )
                }
            }
        }

        val services = ArrayList<ParsedService>()
        val expenses = ArrayList<ParsedExpense>()
        if (costs != null) parseCosts(file, costs, categories, dates, services, expenses, issues)

        vehicles.add(
            ParsedVehicle(
                key = key,
                name = vcell("name") ?: file.substringAfterLast('/').substringBeforeLast('.'),
                make = vcell("make"), model = vcell("model"),
                year = vcell("year")?.toIntOrNull()?.takeIf { it > 1800 },
                plate = vcell("plate"), vin = vcell("vin"),
                distanceUnit = distance, volumeUnit = volume,
                energy = energy, services = services, expenses = expenses,
            ),
        )
    }

    private fun categoryNames(section: Section?): Map<String, String> {
        val header = section?.rows?.firstOrNull() ?: return emptyMap()
        val cols = Columns(header.cells)
        val iId = cols.find("id") ?: 0
        val iName = cols.find("name", "title") ?: 1
        return section.rows.drop(1).mapNotNull { r ->
            val id = r.cell(iId) ?: return@mapNotNull null
            val name = r.cell(iName) ?: return@mapNotNull null
            id to name
        }.toMap()
    }

    private fun parseCosts(
        file: String,
        costs: Section,
        categories: Map<String, String>,
        dates: DateParser,
        services: MutableList<ParsedService>,
        expenses: MutableList<ParsedExpense>,
        issues: MutableList<RowIssue>,
    ) {
        val header = costs.rows.firstOrNull() ?: return
        val cols = Columns(header.cells)
        val iTitle = cols.find("costtitle", "title") ?: 0
        val iDate = cols.find("date") ?: 1
        val iOdo = cols.find("odo") ?: 2
        val iType = cols.exact("costtypeid")
        val iNotes = cols.find("notes")
        val iCost = cols.exact("cost") ?: 5
        val iTemplate = cols.exact("istemplate")
        val iIncome = cols.exact("isincome")
        for (row in costs.rows.drop(1)) {
            if (iTemplate?.let { Parse.bool(row.cell(it)) } == true) continue // reminder templates, not history
            val date = dates.parse(row.cell(iDate))
            val amount = Parse.number(row.cell(iCost))
            val title = row.cell(iTitle) ?: iType?.let { row.cell(it) }?.let { categories[it] } ?: "Cost"
            when {
                date == null -> issues.add(RowIssue(file, row.line, "Unreadable date"))
                amount == null -> issues.add(RowIssue(file, row.line, "Unreadable cost"))
                iIncome?.let { Parse.bool(row.cell(it)) } == true -> issues.add(RowIssue(file, row.line, "Income entries aren't supported"))
                else -> {
                    val odo = Parse.number(row.cell(iOdo), threeDigitsAreGrouping = true)?.takeIf { it > 0 }
                    val note = iNotes?.let { row.cell(it) }
                    val categoryName = iType?.let { row.cell(it) }?.let { categories[it] }
                    when (val kind = EntryClassifier.classify("$title ${categoryName ?: ""}")) {
                        EntryClassifier.Kind.Service -> services.add(ParsedService(row.line, date, odo, title, amount, note))
                        is EntryClassifier.Kind.Expense -> expenses.add(ParsedExpense(row.line, date, odo, kind.category, title, amount, note))
                    }
                }
            }
        }
    }
}
