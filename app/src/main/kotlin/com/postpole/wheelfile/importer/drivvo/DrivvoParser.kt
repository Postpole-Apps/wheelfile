package com.postpole.wheelfile.importer.drivvo

import com.postpole.wheelfile.importer.CostClassifier
import com.postpole.wheelfile.importer.Csv
import com.postpole.wheelfile.importer.CsvRow
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
 * BETA. Built from the published column layout only (spec §4.4, D4): sections `##Vehicle`, `##Refuelling`
 * (or a localised title such as `#Reabastecimiento`), `##Service`, `##Expense`. Columns are read by index
 * (refuelling: 0 odometer, 1 date, 2 fuel type, 3 price per unit, 4 total, 5 volume, 6 full-tank flag, 18 notes;
 * service: 0 odometer, 1 date, 2 cost, 3 title, 5 notes; expense: 0 odometer, 1 date, 2 cost, 3 title, 6 notes),
 * but a header whose name is recognised wins over the index. No real export was available to test against.
 */
object DrivvoParser {
    private val REFUEL = listOf("refuel", "reabast", "abastec", "tanken", "betank", "ravitaill", "carburant", "fuel", "tankowanie")
    private val SERVICE = listOf("service", "servic", "wartung", "entretien", "manuten", "serwis")
    private val EXPENSE = listOf("expense", "gasto", "despesa", "ausgabe", "depense", "wydatek", "koszt")
    private val VEHICLE = listOf("vehicle", "vehic", "vehículo", "veiculo", "fahrzeug", "pojazd", "voiture")

    private val YES = setOf(
        "yes", "y", "true", "1", "si", "sí", "s", "sim", "ja", "j", "tak", "oui", "da", "ano", "evet", "ok", "x", "full", "lleno", "cheio",
    )

    private val sectionLine = Regex("^\\s*\"?#{1,2}\\s*([^\",;\\n]+)", RegexOption.MULTILINE)

    private fun kindOf(sectionName: String): String? {
        val n = sectionName.lowercase().trim()
        return when {
            REFUEL.any { n.startsWith(it) || n.contains(it) } -> "refuel"
            SERVICE.any { n.startsWith(it) } -> "service"
            EXPENSE.any { n.startsWith(it) } -> "expense"
            VEHICLE.any { n.startsWith(it) } -> "vehicle"
            else -> null
        }
    }

    /** True when the text has `#`-prefixed sections that look like this layout and none of the other CSV layouts. */
    fun looksLike(text: String): Boolean {
        val names = sectionLine.findAll(text).map { it.groupValues[1].trim() }.toList()
        if (names.isEmpty()) return false
        val kinds = names.mapNotNull(::kindOf)
        return kinds.any { it == "refuel" || it == "service" || it == "expense" } &&
            names.none { it.equals("Log", true) || it.equals("Costs", true) }
    }

    fun parse(file: ImportFile, zone: ZoneId): Pair<SourceVehicle?, List<RowError>> {
        val text = file.text()
        val delimiter = Csv.sniffDelimiter(text)
        val sections = Sections.split(text, delimiter)
        val byKind = sections.groupBy { kindOf(it.name) }
        val errors = ArrayList<RowError>()
        val refuel = byKind["refuel"]?.firstOrNull()
        val service = byKind["service"]?.firstOrNull()
        val expense = byKind["expense"]?.firstOrNull()
        val vehicleSection = byKind["vehicle"]?.firstOrNull()
        if (refuel == null && service == null && expense == null) return null to errors

        val all = listOfNotNull(refuel, service, expense)
        val style = DecimalStyle.sniff(sequence { for (s in all) for (r in s.rows) yieldAll(r.cells) })
        val dates = DateParser(
            DateParser.sniffOrder(sequence { for (s in all) for (r in s.rows) yieldAll(r.cells) }, SlashOrder.DMY),
            zone,
        )

        // Vehicle row: name, model, plate, ?, year, notes.
        var name = file.baseName.substringBeforeLast('.').ifBlank { "Vehicle" }
        var model: String? = null
        var plate: String? = null
        var year: Int? = null
        vehicleSection?.rows?.firstOrNull { !it.isBlank }?.let { row ->
            row.cell(0)?.let { name = it }
            model = row.cell(1)
            plate = row.cell(2)
            year = row.cell(4)?.toIntOrNull()?.takeIf { it in 1900..2100 }
        }

        fun resolve(section: CsvSection, names: List<String>, index: Int): Int? =
            section.colStartingWith(*names.toTypedArray()) ?: index

        val fills = ArrayList<SourceFill>()
        if (refuel != null) {
            val cOdo = resolve(refuel, listOf("odometer", "odômetro", "odometro", "kilometerstand", "kilometraje"), 0)
            val cDate = resolve(refuel, listOf("date", "data", "fecha", "datum"), 1)
            val cGrade = 2
            val cPrice = resolve(refuel, listOf("priceperunit", "price", "preço", "preco", "precio", "preis"), 3)
            val cTotal = resolve(refuel, listOf("totalcost", "total", "valortotal", "gesamt"), 4)
            val cVolume = resolve(refuel, listOf("volume", "quantity", "litros", "litres", "liters", "menge"), 5)
            val cFull = resolve(refuel, listOf("full", "tanquecheio", "tanquelleno", "volltank", "tankvoll"), 6)
            val cNotes = 18
            for (row in refuel.rows) {
                if (row.isBlank) continue
                val date = dates.parse(row.cell(cDate))
                val odo = NumberParser.parse(row.cell(cOdo), style)
                val volume = NumberParser.parse(row.cell(cVolume), style)
                when {
                    date == null -> errors += RowError(file.baseName, row.line, "Unreadable date")
                    odo == null -> errors += RowError(file.baseName, row.line, "Unreadable odometer")
                    volume == null || volume <= 0.0 -> errors += RowError(file.baseName, row.line, "Unreadable volume")
                    else -> fills += SourceFill(
                        line = row.line, dateTime = date, odometer = odo, quantity = volume,
                        pricePerUnit = NumberParser.parse(row.cell(cPrice), style)?.takeIf { it > 0 },
                        total = NumberParser.parse(row.cell(cTotal), style)?.takeIf { it > 0 },
                        isFull = isYes(row.cell(cFull)),
                        missedPrevious = false,
                        note = row.cell(cNotes), fuelGrade = row.cell(cGrade),
                    )
                }
            }
        }

        val services = ArrayList<SourceService>()
        val expenses = ArrayList<SourceExpense>()
        fun costRows(section: CsvSection?, notesIndex: Int, isService: Boolean) {
            section ?: return
            val cOdo = resolve(section, listOf("odometer", "odômetro", "odometro", "kilometerstand"), 0)
            val cDate = resolve(section, listOf("date", "data", "fecha", "datum"), 1)
            val cCost = resolve(section, listOf("totalcost", "cost", "total", "valor", "coste", "kosten"), 2)
            val cTitle = 3
            for (row: CsvRow in section.rows) {
                if (row.isBlank) continue
                val date = dates.parse(row.cell(cDate))
                val amount = NumberParser.parse(row.cell(cCost), style)
                val odo = NumberParser.parse(row.cell(cOdo), style)?.takeIf { it > 0 }
                when {
                    date == null -> errors += RowError(file.baseName, row.line, "Unreadable date")
                    amount == null -> errors += RowError(file.baseName, row.line, "Unreadable cost")
                    else -> {
                        val title = row.cell(cTitle) ?: if (isService) "Service" else "Expense"
                        val note = row.cell(notesIndex)
                        if (isService) {
                            services += SourceService(row.line, date, odo, title, amount, note)
                        } else {
                            val category = (CostClassifier.classify(title) as? CostClassifier.Kind.Expense)?.category
                                ?: com.postpole.wheelfile.data.db.ExpenseCategory.OTHER
                            expenses += SourceExpense(row.line, date, odo, title, amount, category, note)
                        }
                    }
                }
            }
        }
        costRows(service, 5, isService = true)
        costRows(expense, 6, isService = false)

        return SourceVehicle(
            key = file.name, name = name, model = model, plate = plate, year = year,
            fills = fills, services = services, expenses = expenses,
        ) to errors
    }

    /** Localised yes/no: Yes/Si/Sim/Ja/Tak/Oui and friends are true, everything else (No/Nie/Nein/Nao) is false. */
    fun isYes(value: String?): Boolean = value?.trim()?.lowercase()?.trimEnd('.') in YES
}
