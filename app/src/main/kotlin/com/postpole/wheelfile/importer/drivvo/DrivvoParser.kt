package com.postpole.wheelfile.importer.drivvo

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
 * BETA. Built from the published description of the format only (spec §4.4, D4): no real export was available, so
 * every assumption below is deliberately loose.
 *
 * - Sections start with `##Vehicle`, `##Refuelling`, `##Service`, `##Expense` (a single `#` and translated names
 *   such as `#Reabastecimiento` / `#Servicio` are accepted too).
 * - Columns are read **by index** (refuelling: 0 odometer, 1 date, 2 fuel type, 3 price per unit, 4 total, 5 volume,
 *   6 full tank flag, 18 notes; service: 0 odometer, 1 date, 2 cost, 3 title, 5 notes; expense: 0 odometer, 1 date,
 *   2 cost, 3 title, 6 notes). If a section starts with a header row, columns whose title is recognised override
 *   the index (header sniffing).
 * - The full-tank flag is a translated yes/no (Yes, Si, Tak, Ja, Sim ...) and dates may be D/M/YYYY or M/D/YYYY;
 *   the order is inferred per file from the dates themselves.
 * - The vehicle row is: name, model, plate, ?, year, notes.
 */
object DrivvoParser {
    private enum class Kind { VEHICLE, REFUEL, SERVICE, EXPENSE }

    private fun kindOf(name: String): Kind? {
        val n = Parse.fold(name)
        return when {
            listOf("refuel", "reabastec", "abastec", "tankvorgang", "betank", "tanken", "refill", "fuel").any { n.startsWith(it) } -> Kind.REFUEL
            listOf("service", "servicio", "servico", "wartung").any { n.startsWith(it) } -> Kind.SERVICE
            listOf("expense", "gasto", "despesa", "ausgabe", "kosten").any { n.startsWith(it) } -> Kind.EXPENSE
            listOf("vehicle", "vehiculo", "veiculo", "fahrzeug", "pojazd").any { n.startsWith(it) } -> Kind.VEHICLE
            else -> null
        }
    }

    private fun sections(text: String): List<Pair<Kind, Section>> =
        Sections.split(Csv.parse(text), Sections::hashHeading).mapNotNull { s -> kindOf(s.name)?.let { it to s } }

    /** Heading style without a space ("##Vehicle") plus a refuelling-like section distinguishes this from the other CSVs. */
    fun looksLike(text: String): Boolean {
        val found = sections(text).map { it.first }.toSet()
        return Kind.REFUEL in found || (Kind.SERVICE in found && Kind.EXPENSE in found)
    }

    fun parse(input: ImportInput, zone: ZoneId): ParsedFile {
        val file = input.files.keys.first()
        val all = sections(input.text(file))
        val issues = ArrayList<RowIssue>()

        // One date order for the whole file.
        val dateSamples = all.asSequence().filter { it.first != Kind.VEHICLE }.flatMap { (_, s) ->
            val dataRows = dataRows(s)
            val iDate = dateColumn(s)
            dataRows.asSequence().map { it.cell(iDate) }
        }
        val dates = DateParser(zone, DateParser.inferOrder(dateSamples, DateOrder.DAY_FIRST))

        val energy = ArrayList<ParsedEnergy>()
        val services = ArrayList<ParsedService>()
        val expenses = ArrayList<ParsedExpense>()

        for ((kind, section) in all) {
            val header = headerOf(section)
            val cols = header?.let { Columns(it.cells) }
            fun idx(default: Int, vararg needles: String): Int = cols?.find(*needles) ?: default
            val rows = dataRows(section)
            when (kind) {
                Kind.VEHICLE -> Unit
                Kind.REFUEL -> {
                    val iOdo = idx(0, "odometer", "odometro", "odômetro", "kilometer", "kilometrage")
                    val iDate = idx(1, "date", "data", "fecha", "datum")
                    val iType = idx(2, "fuel type", "combustivel", "combustible", "kraftstoff")
                    val iPrice = idx(3, "price", "preco", "precio", "preis")
                    val iTotal = idx(4, "total")
                    val iVolume = idx(5, "volume", "litros", "liters", "quantity", "quantidade", "cantidad", "menge")
                    val iFull = idx(6, "full", "cheio", "lleno", "voll", "tank")
                    val iNotes = idx(18, "note", "obs", "notas", "notiz")
                    for (row in rows) {
                        val date = dates.parse(row.cell(iDate))
                        val odo = Parse.number(row.cell(iOdo), threeDigitsAreGrouping = true)
                        val qty = Parse.number(row.cell(iVolume))
                        when {
                            date == null -> issues.add(RowIssue(file, row.line, "Unreadable date"))
                            odo == null -> issues.add(RowIssue(file, row.line, "Unreadable odometer"))
                            qty == null || qty <= 0.0 -> issues.add(RowIssue(file, row.line, "Unreadable volume"))
                            else -> energy.add(
                                ParsedEnergy(
                                    line = row.line, dateTime = date, odometer = odo, quantity = qty,
                                    totalMajor = Parse.number(row.cell(iTotal))?.takeIf { it > 0 },
                                    pricePerUnitMajor = Parse.number(row.cell(iPrice))?.takeIf { it > 0 },
                                    isFull = Parse.bool(row.cell(iFull)) ?: false,
                                    missedPrevious = false,
                                    fuelGrade = row.cell(iType),
                                    note = row.cell(iNotes),
                                ),
                            )
                        }
                    }
                }
                Kind.SERVICE, Kind.EXPENSE -> {
                    val isService = kind == Kind.SERVICE
                    val iOdo = idx(0, "odometer", "odometro", "odômetro", "kilometer")
                    val iDate = idx(1, "date", "data", "fecha", "datum")
                    val iCost = idx(2, "cost", "valor", "costo", "preis", "total")
                    val iTitle = idx(3, "title", "name", "titulo", "título", "nome", "nombre", "type", "tipo")
                    val iNotes = idx(if (isService) 5 else 6, "note", "obs", "notas", "notiz")
                    for (row in rows) {
                        val date = dates.parse(row.cell(iDate))
                        val cost = Parse.number(row.cell(iCost))
                        when {
                            date == null -> issues.add(RowIssue(file, row.line, "Unreadable date"))
                            cost == null -> issues.add(RowIssue(file, row.line, "Unreadable cost"))
                            else -> {
                                val odo = Parse.number(row.cell(iOdo), threeDigitsAreGrouping = true)?.takeIf { it > 0 }
                                val title = row.cell(iTitle) ?: if (isService) "Service" else "Expense"
                                val note = row.cell(iNotes)
                                val c = if (isService) EntryClassifier.Kind.Service else EntryClassifier.classify(title)
                                when (c) {
                                    EntryClassifier.Kind.Service -> services.add(ParsedService(row.line, date, odo, title, cost, note))
                                    is EntryClassifier.Kind.Expense -> expenses.add(ParsedExpense(row.line, date, odo, c.category, title, cost, note))
                                }
                            }
                        }
                    }
                }
            }
        }

        val vehicleRow = all.firstOrNull { it.first == Kind.VEHICLE }?.second?.let(::vehicleRow)
        val vehicle = ParsedVehicle(
            key = file,
            name = vehicleRow?.cell(0) ?: "Imported vehicle",
            model = vehicleRow?.cell(1),
            plate = vehicleRow?.cell(2),
            year = vehicleRow?.cell(4)?.toIntOrNull()?.takeIf { it > 1800 },
            energy = energy, services = services, expenses = expenses,
        )
        return ParsedFile(ImportFormat.DRIVVO_CSV, listOf(vehicle), issues)
    }

    /** First row of a section if it is a header (no readable date and no number in the first two cells). */
    private fun headerOf(section: Section): CsvRow? {
        val first = section.rows.firstOrNull() ?: return null
        val hasNumber = first.cells.take(2).any { Parse.number(it) != null }
        return if (hasNumber) null else first
    }

    private fun dataRows(section: Section): List<CsvRow> =
        if (headerOf(section) != null) section.rows.drop(1) else section.rows

    /** The vehicle row is "name, model, plate, ?, year, notes"; a header row before it is skipped when present. */
    private fun vehicleRow(section: Section): CsvRow? {
        val rows = section.rows
        return if (rows.size >= 2 && rows[0].cell(4)?.toIntOrNull() == null) rows[1] else rows.firstOrNull()
    }

    private fun dateColumn(section: Section): Int =
        headerOf(section)?.let { Columns(it.cells).find("date", "data", "fecha", "datum") } ?: 1
}
