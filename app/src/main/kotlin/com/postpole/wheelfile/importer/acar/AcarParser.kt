package com.postpole.wheelfile.importer.acar

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.importer.Columns
import com.postpole.wheelfile.importer.Csv
import com.postpole.wheelfile.importer.CsvRow
import com.postpole.wheelfile.importer.DateOrder
import com.postpole.wheelfile.importer.DateParser
import com.postpole.wheelfile.importer.EntryClassifier
import com.postpole.wheelfile.importer.ImportException
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
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.time.ZoneId
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * aCar `.abp` backups (a ZIP whose `vehicles.xml` holds vehicles with fill-up, service and expense records) and
 * aCar CSV exports (sections Vehicles, Fill-Up Records, Service Records, Expense Records). Structure comes from
 * community samples (spec §4.4), so element and column lookups are tolerant of renamed fields.
 */
object AcarParser {
    private val CSV_SECTIONS = setOf("vehicles", "fill-up records", "service records", "expense records")

    fun isBackup(input: ImportInput): Boolean = input.files.keys.any { it.substringAfterLast('/').lowercase() == "vehicles.xml" }

    private fun csvHeading(row: CsvRow): String? {
        val filled = row.cells.filter { it.isNotBlank() }
        val only = filled.singleOrNull()?.trim() ?: return null
        return only.takeIf { Parse.fold(it) in CSV_SECTIONS || Parse.fold(it) in setOf("metadata", "trip records") }
    }

    fun looksLikeCsv(text: String): Boolean {
        val names = Sections.split(Csv.parse(text), ::csvHeading).map { Parse.fold(it.name) }
        return "fill-up records" in names
    }

    fun parse(input: ImportInput, zone: ZoneId): ParsedFile =
        if (isBackup(input)) parseXml(input, zone) else parseCsv(input, zone)

    // ---- .abp XML ----

    private fun parseXml(input: ImportInput, zone: ZoneId): ParsedFile {
        val name = input.files.keys.first { it.substringAfterLast('/').lowercase() == "vehicles.xml" }
        val doc = try {
            secureBuilder().parse(ByteArrayInputStream(input.files.getValue(name)))
        } catch (e: org.xml.sax.SAXException) {
            throw ImportException("The backup's vehicle list couldn't be read.")
        }
        val subtypes = loadSubtypes(input)
        val issues = ArrayList<RowIssue>()
        val vehicles = ArrayList<ParsedVehicle>()
        val vehicleElements = doc.getElementsByTagName("vehicle")
        // The XML parser doesn't report line numbers per element, so rows are numbered by position (1-based).
        for (v in 0 until vehicleElements.length) {
            val el = vehicleElements.item(v) as? Element ?: continue
            val vName = el.text("name") ?: "Imported vehicle ${v + 1}"
            val file = "$name ($vName)"
            val energy = ArrayList<ParsedEnergy>()
            val services = ArrayList<ParsedService>()
            val expenses = ArrayList<ParsedExpense>()
            for ((index, rec) in el.records("fillup-record").withIndex()) {
                val line = index + 1
                val date = parseDate(rec.text("date"), zone)
                val odo = Parse.number(rec.text("odometer-reading", "odometer"), threeDigitsAreGrouping = true)
                val qty = Parse.number(rec.text("volume", "quantity"))
                when {
                    date == null -> issues.add(RowIssue("$file fill-up", line, "Unreadable date"))
                    odo == null -> issues.add(RowIssue("$file fill-up", line, "Unreadable odometer"))
                    qty == null || qty <= 0.0 -> issues.add(RowIssue("$file fill-up", line, "Unreadable volume"))
                    else -> energy.add(
                        ParsedEnergy(
                            line = line, dateTime = date, odometer = odo, quantity = qty,
                            totalMajor = Parse.number(rec.text("total-cost", "cost"))?.takeIf { it > 0 },
                            pricePerUnitMajor = null,
                            isFull = !(Parse.bool(rec.text("partial")) ?: false),
                            missedPrevious = (Parse.number(rec.text("previous-missed-fillups")) ?: 0.0) > 0.0 ||
                                Parse.bool(rec.text("previous-missed-fillups")) == true,
                            fuelGrade = rec.text("fuel-brand"),
                            station = rec.text("fueling-station-address"),
                            note = rec.text("notes"),
                        ),
                    )
                }
            }
            for (tag in listOf("service-record", "expense-record", "event-record")) {
                for ((index, rec) in el.records(tag).withIndex()) {
                    val line = index + 1
                    val date = parseDate(rec.text("date"), zone)
                    val cost = Parse.number(rec.text("total-cost", "cost", "amount"))
                    val typeId = rec.text("service-type-id", "expense-type-id", "event-subtype-id", "type-id", "type")
                    val title = typeId?.let { subtypes[it] } ?: typeId?.takeIf { it.any { c -> c.isLetter() } }
                        ?: rec.text("notes") ?: if (tag == "service-record") "Service" else "Expense"
                    val odo = Parse.number(rec.text("odometer-reading", "odometer"), threeDigitsAreGrouping = true)?.takeIf { it > 0 }
                    when {
                        date == null -> issues.add(RowIssue("$file $tag", line, "Unreadable date"))
                        cost == null -> issues.add(RowIssue("$file $tag", line, "Unreadable cost"))
                        else -> {
                            val kind = if (tag == "service-record") EntryClassifier.Kind.Service else EntryClassifier.classify(title)
                            val note = rec.text("notes")
                            when (kind) {
                                EntryClassifier.Kind.Service -> services.add(ParsedService(line, date, odo, title, cost, note))
                                is EntryClassifier.Kind.Expense -> expenses.add(ParsedExpense(line, date, odo, kind.category, title, cost, note))
                            }
                        }
                    }
                }
            }
            vehicles.add(
                ParsedVehicle(
                    key = "$v", name = vName,
                    make = el.text("make"), model = el.text("model"),
                    year = el.text("year")?.toIntOrNull()?.takeIf { it > 1800 },
                    plate = el.text("license-plate"), vin = el.text("vin"),
                    distanceUnit = distanceUnit(el.text("distance-unit")),
                    volumeUnit = volumeUnit(el.text("volume-unit")),
                    energy = energy, services = services, expenses = expenses,
                ),
            )
        }
        return ParsedFile(ImportFormat.ACAR_BACKUP, vehicles, issues)
    }

    private fun loadSubtypes(input: ImportInput): Map<String, String> {
        val out = HashMap<String, String>()
        for ((n, bytes) in input.files) {
            val base = n.substringAfterLast('/').lowercase()
            if (base !in setOf("event-subtypes.xml", "services.xml", "expenses.xml")) continue
            val doc = try {
                secureBuilder().parse(ByteArrayInputStream(bytes))
            } catch (e: org.xml.sax.SAXException) {
                continue
            }
            val all = doc.getElementsByTagName("*")
            for (i in 0 until all.length) {
                val el = all.item(i) as? Element ?: continue
                val name = el.text("name") ?: continue
                val id = el.text("id") ?: el.getAttribute("id").takeIf { it.isNotEmpty() } ?: continue
                out[id] = name
            }
        }
        return out
    }

    private fun secureBuilder(): javax.xml.parsers.DocumentBuilder {
        val f = DocumentBuilderFactory.newInstance()
        // Backups are untrusted input: no DTDs, no external entities.
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        f.isXIncludeAware = false
        f.isExpandEntityReferences = false
        return f.newDocumentBuilder()
    }

    private fun Element.children(): List<Element> {
        val out = ArrayList<Element>()
        var n: Node? = firstChild
        while (n != null) {
            if (n is Element) out.add(n)
            n = n.nextSibling
        }
        return out
    }

    /** Text of the first direct child named like one of [names] (case-insensitive). */
    private fun Element.text(vararg names: String): String? =
        children().firstOrNull { c -> names.any { it.equals(c.tagName, ignoreCase = true) } }
            ?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    /** Record elements named [tag], whether nested in a `...-records` wrapper or direct children. */
    private fun Element.records(tag: String): List<Element> =
        children().flatMap { c ->
            when {
                c.tagName.equals(tag, ignoreCase = true) -> listOf(c)
                c.tagName.endsWith("-records", ignoreCase = true) -> c.children().filter { it.tagName.equals(tag, ignoreCase = true) }
                else -> emptyList()
            }
        }

    private fun parseDate(raw: String?, zone: ZoneId): Long? = DateParser(zone, DateOrder.DAY_FIRST).parse(raw)

    private fun distanceUnit(raw: String?): DistanceUnit? = when {
        raw == null -> null
        Parse.fold(raw).startsWith("mi") -> DistanceUnit.MI
        Parse.fold(raw).startsWith("k") -> DistanceUnit.KM
        else -> null
    }

    private fun volumeUnit(raw: String?): VolumeUnit? {
        val v = Parse.fold(raw ?: return null)
        return when {
            v.contains("uk") || v.contains("imp") -> VolumeUnit.UK_GAL
            v.contains("gal") -> VolumeUnit.US_GAL
            v == "l" || v.startsWith("lit") -> VolumeUnit.L
            else -> null
        }
    }

    // ---- CSV ----

    private fun parseCsv(input: ImportInput, zone: ZoneId): ParsedFile {
        val file = input.files.keys.first()
        val sections = Sections.split(Csv.parse(input.text(file)), ::csvHeading).associateBy { Parse.fold(it.name) }
        val issues = ArrayList<RowIssue>()
        val fillUps = sections["fill-up records"]
        val serviceRows = sections["service records"]
        val expenseRows = sections["expense records"]

        val order = DateOrder.DAY_FIRST.let { fallback ->
            val samples = listOfNotNull(fillUps, serviceRows, expenseRows).flatMap { s ->
                val iDate = s.rows.firstOrNull()?.let { Columns(it.cells).exact("date") } ?: 1
                s.rows.drop(1).map { it.cell(iDate) }
            }
            DateParser.inferOrder(samples.asSequence(), fallback)
        }
        val dates = DateParser(zone, order)

        class Builder(val name: String) {
            val energy = ArrayList<ParsedEnergy>()
            val services = ArrayList<ParsedService>()
            val expenses = ArrayList<ParsedExpense>()
            var distance: DistanceUnit? = null
            var volume: VolumeUnit? = null
        }
        val builders = LinkedHashMap<String, Builder>()
        sections["vehicles"]?.let { s ->
            val cols = Columns(s.rows.first().cells)
            val iName = cols.exact("name", "vehicle") ?: 0
            for (row in s.rows.drop(1)) row.cell(iName)?.let { builders.getOrPut(it) { Builder(it) } }
        }
        val vehicleMeta = HashMap<String, List<String?>>()
        sections["vehicles"]?.let { s ->
            val cols = Columns(s.rows.first().cells)
            val iName = cols.exact("name", "vehicle") ?: 0
            for (row in s.rows.drop(1)) {
                val n = row.cell(iName) ?: continue
                vehicleMeta[n] = listOf(
                    cols.exact("make")?.let { row.cell(it) }, cols.exact("model")?.let { row.cell(it) },
                    cols.exact("year")?.let { row.cell(it) }, cols.find("license", "plate")?.let { row.cell(it) },
                    cols.exact("vin")?.let { row.cell(it) },
                )
            }
        }

        fun builderFor(row: CsvRow, iVehicle: Int?): Builder =
            builders.getOrPut(iVehicle?.let { row.cell(it) } ?: "Imported vehicle") { Builder(iVehicle?.let { row.cell(it) } ?: "Imported vehicle") }

        fun withTime(row: CsvRow, iDate: Int, iTime: Int?): Long? = dates.parse(row.cell(iDate), iTime?.let { row.cell(it) })

        fun units(b: Builder, row: CsvRow, cols: Columns) {
            if (b.distance == null) b.distance = cols.find("distance unit")?.let { distanceUnit(row.cell(it)) }
            if (b.volume == null) b.volume = cols.find("volume unit")?.let { volumeUnit(row.cell(it)) }
        }

        if (fillUps != null) {
            val cols = Columns(fillUps.rows.first().cells)
            val iVehicle = cols.exact("vehicle")
            val iDate = cols.exact("date") ?: 1
            val iTime = cols.exact("time")
            val iOdo = cols.find("odometer") ?: 3
            val iVolume = cols.exact("volume") ?: 5
            val iTotal = cols.find("total cost", "total") ?: 8
            val iPrice = cols.find("price per unit", "price")
            val iPartial = cols.find("partial")
            val iMissed = cols.find("missed")
            val iNotes = cols.exact("notes")
            val iBrand = cols.find("fuel brand", "brand")
            for (row in fillUps.rows.drop(1)) {
                val date = withTime(row, iDate, iTime)
                val odo = Parse.number(row.cell(iOdo), threeDigitsAreGrouping = true)
                val qty = Parse.number(row.cell(iVolume))
                when {
                    date == null -> issues.add(RowIssue(file, row.line, "Unreadable date"))
                    odo == null -> issues.add(RowIssue(file, row.line, "Unreadable odometer"))
                    qty == null || qty <= 0.0 -> issues.add(RowIssue(file, row.line, "Unreadable volume"))
                    else -> {
                        val b = builderFor(row, iVehicle)
                        units(b, row, cols)
                        b.energy.add(
                            ParsedEnergy(
                                line = row.line, dateTime = date, odometer = odo, quantity = qty,
                                totalMajor = Parse.number(row.cell(iTotal))?.takeIf { it > 0 },
                                pricePerUnitMajor = iPrice?.let { Parse.number(row.cell(it)) }?.takeIf { it > 0 },
                                isFull = !(iPartial?.let { Parse.bool(row.cell(it)) } ?: false),
                                missedPrevious = iMissed?.let { Parse.bool(row.cell(it)) } ?: false,
                                fuelGrade = iBrand?.let { row.cell(it) },
                                note = iNotes?.let { row.cell(it) },
                            ),
                        )
                    }
                }
            }
        }
        for ((section, isService) in listOf(serviceRows to true, expenseRows to false)) {
            section ?: continue
            val cols = Columns(section.rows.first().cells)
            val iVehicle = cols.exact("vehicle")
            val iDate = cols.exact("date") ?: 1
            val iTime = cols.exact("time")
            val iOdo = cols.find("odometer")
            val iType = cols.find("service type", "expense type", "type", "category")
            val iCost = cols.find("total cost", "cost", "amount")
            val iNotes = cols.exact("notes")
            for (row in section.rows.drop(1)) {
                val date = withTime(row, iDate, iTime)
                val cost = iCost?.let { Parse.number(row.cell(it)) }
                if (date == null) {
                    issues.add(RowIssue(file, row.line, "Unreadable date"))
                    continue
                }
                if (cost == null) {
                    issues.add(RowIssue(file, row.line, "Unreadable cost"))
                    continue
                }
                val b = builderFor(row, iVehicle)
                units(b, row, cols)
                val odo = iOdo?.let { Parse.number(row.cell(it), threeDigitsAreGrouping = true) }?.takeIf { it > 0 }
                val note = iNotes?.let { row.cell(it) }
                val title = iType?.let { row.cell(it) } ?: note ?: if (isService) "Service" else "Expense"
                val kind = if (isService) EntryClassifier.Kind.Service else EntryClassifier.classify(title)
                when (kind) {
                    EntryClassifier.Kind.Service -> b.services.add(ParsedService(row.line, date, odo, title, cost, note))
                    is EntryClassifier.Kind.Expense -> b.expenses.add(ParsedExpense(row.line, date, odo, kind.category, title, cost, note))
                }
            }
        }
        val vehicles = builders.values.map { b ->
            val meta = vehicleMeta[b.name]
            ParsedVehicle(
                key = b.name, name = b.name, make = meta?.get(0), model = meta?.get(1),
                year = meta?.get(2)?.toIntOrNull()?.takeIf { it > 1800 }, plate = meta?.get(3), vin = meta?.get(4),
                distanceUnit = b.distance, volumeUnit = b.volume,
                energy = b.energy, services = b.services, expenses = b.expenses,
            )
        }
        return ParsedFile(ImportFormat.ACAR_CSV, vehicles, issues)
    }
}
