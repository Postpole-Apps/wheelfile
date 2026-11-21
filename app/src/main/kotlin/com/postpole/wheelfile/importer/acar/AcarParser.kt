package com.postpole.wheelfile.importer.acar

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.ExpenseCategory
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
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.time.ZoneId
import javax.xml.parsers.DocumentBuilderFactory

/** aCar: the `.abp` backup (a ZIP whose `vehicles.xml` holds everything) and the CSV export (spec §4.4). */
object AcarParser {
    private val CSV_TITLES = setOf(
        "metadata", "vehicles", "fill-up records", "service records", "expense records", "trip records", "event records",
    )

    fun isBackupXml(name: String, text: String): Boolean =
        name.substringAfterLast('/').equals("vehicles.xml", ignoreCase = true) && text.contains("<vehicle")

    fun looksLikeCsv(text: String): Boolean {
        val titles = text.lineSequence().map { it.trim().trim('"', ',', ';').lowercase() }.filter { it in CSV_TITLES }.toSet()
        return "fill-up records" in titles || ("vehicles" in titles && ("service records" in titles || "expense records" in titles))
    }

    // ---- .abp XML ----------------------------------------------------------------------------------------------

    fun parseXml(file: ImportFile, zone: ZoneId): Pair<List<SourceVehicle>, List<RowError>> {
        val text = file.text()
        val errors = ArrayList<RowError>()
        val doc = try {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                isExpandEntityReferences = false
            }.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            return emptyList<SourceVehicle>() to listOf(RowError(file.baseName, 1, "The vehicle file is not valid XML"))
        }
        val dates = DateParser(SlashOrder.MDY, zone)
        val vehicles = ArrayList<SourceVehicle>()
        val vehicleNodes = doc.getElementsByTagName("vehicle")
        // Line numbers: the n-th "<tag" in the text, counted per tag name.
        val lineFinders = HashMap<String, LineFinder>()
        fun lineOf(tag: String, n: Int) = lineFinders.getOrPut(tag) { LineFinder(text, tag) }.lineOf(n)

        for (vi in 0 until vehicleNodes.length) {
            val v = vehicleNodes.item(vi) as? Element ?: continue
            val style = DecimalStyle.sniff(
                sequence {
                    for (tag in listOf("volume", "total-cost", "odometer-reading", "cost")) {
                        val list = v.getElementsByTagName(tag)
                        for (i in 0 until list.length) yield(list.item(i).textContent.orEmpty())
                    }
                },
            )
            val fills = ArrayList<SourceFill>()
            val services = ArrayList<SourceService>()
            val expenses = ArrayList<SourceExpense>()

            fun records(tag: String): List<Pair<Int, Element>> {
                val list = v.getElementsByTagName(tag)
                return (0 until list.length).mapNotNull { i -> (list.item(i) as? Element)?.let { i to it } }
            }
            // The nth record's global ordinal across vehicles, for line numbers.
            fun ordinal(tag: String, el: Element): Int {
                val all = doc.getElementsByTagName(tag)
                for (i in 0 until all.length) if (all.item(i) === el) return i
                return 0
            }

            for ((_, el) in records("fillup-record")) {
                val line = lineOf("fillup-record", ordinal("fillup-record", el))
                val date = dates.parse(el.child("date"))
                val volume = NumberParser.parse(el.child("volume"), style)
                val odo = NumberParser.parse(el.child("odometer-reading") ?: el.child("odometer"), style)
                when {
                    date == null -> errors += RowError(file.baseName, line, "Unreadable date")
                    volume == null || volume <= 0.0 -> errors += RowError(file.baseName, line, "Unreadable volume")
                    odo == null -> errors += RowError(file.baseName, line, "Unreadable odometer")
                    else -> fills += SourceFill(
                        line = line, dateTime = date, odometer = odo, quantity = volume,
                        pricePerUnit = NumberParser.parse(el.child("price-per-unit"), style)?.takeIf { it > 0 },
                        total = NumberParser.parse(el.child("total-cost"), style)?.takeIf { it > 0 },
                        isFull = !isTrue(el.child("partial")),
                        missedPrevious = (NumberParser.parse(el.child("previous-missed-fillups"), style) ?: 0.0) > 0.0 ||
                            isTrue(el.child("previous-missed-fillups")),
                        station = el.child("fueling-station-address"), note = el.child("notes"),
                        fuelGrade = el.child("fuel-brand"),
                    )
                }
            }

            for (tag in listOf("service-record", "expense-record", "event-record")) {
                for ((_, el) in records(tag)) {
                    val line = lineOf(tag, ordinal(tag, el))
                    val date = dates.parse(el.child("date"))
                    val cost = NumberParser.parse(el.child("total-cost") ?: el.child("cost"), style)
                    val odo = NumberParser.parse(el.child("odometer-reading") ?: el.child("odometer"), style)?.takeIf { it > 0 }
                    val title = el.child("type") ?: el.child("service-type") ?: el.child("expense-type") ?: el.child("subtype")
                        ?: el.child("title") ?: el.child("notes") ?: if (tag == "service-record") "Service" else "Expense"
                    val note = el.child("notes")?.takeIf { it != title }
                    val isService = tag == "service-record" ||
                        (tag == "event-record" && CostClassifier.classify(title) == CostClassifier.Kind.Service)
                    when {
                        date == null -> errors += RowError(file.baseName, line, "Unreadable date")
                        isService -> services += SourceService(line, date, odo, title, cost, note)
                        cost == null -> errors += RowError(file.baseName, line, "Unreadable cost")
                        else -> expenses += SourceExpense(
                            line, date, odo, title, cost,
                            (CostClassifier.classify(title) as? CostClassifier.Kind.Expense)?.category ?: ExpenseCategory.OTHER, note,
                        )
                    }
                }
            }

            vehicles += SourceVehicle(
                key = "${file.name}#$vi",
                name = v.child("name") ?: "Vehicle ${vi + 1}",
                make = v.child("make"), model = v.child("model"),
                year = v.child("year")?.toIntOrNull(),
                plate = v.child("license-plate"), vin = v.child("vin"),
                distanceUnit = distanceOf(v.child("distance-unit")),
                volumeUnit = volumeOf(v.child("volume-unit")),
                fills = fills, services = services, expenses = expenses,
            )
        }
        return vehicles to errors
    }

    /** Text of the direct child element [tag], trimmed; null when missing or empty. */
    private fun Element.child(tag: String): String? {
        var n: Node? = firstChild
        while (n != null) {
            if (n is Element && n.tagName == tag) return n.textContent?.trim()?.ifEmpty { null }
            n = n.nextSibling
        }
        return null
    }

    private fun isTrue(s: String?): Boolean = s?.trim()?.lowercase() in setOf("true", "1", "yes", "y")

    private fun distanceOf(s: String?): DistanceUnit? = when (s?.trim()?.lowercase()) {
        null -> null
        "km", "kilometer", "kilometers", "kilometre", "kilometres" -> DistanceUnit.KM
        "mi", "mile", "miles" -> DistanceUnit.MI
        else -> null
    }

    private fun volumeOf(s: String?): VolumeUnit? {
        val v = s?.trim()?.lowercase() ?: return null
        return when {
            v in setOf("l", "liter", "liters", "litre", "litres") -> VolumeUnit.L
            "uk" in v || "imperial" in v -> VolumeUnit.UK_GAL
            "gal" in v -> VolumeUnit.US_GAL
            else -> null
        }
    }

    /** Finds the line of the n-th opening tag in the XML text. */
    private class LineFinder(text: String, tag: String) {
        private val lines: List<Int>

        init {
            val re = Regex("<${Regex.escape(tag)}(?=[\\s>/])")
            lines = re.findAll(text).map { m ->
                var line = 1
                for (i in 0 until m.range.first) if (text[i] == '\n') line++
                line
            }.toList()
        }

        fun lineOf(n: Int): Int = lines.getOrElse(n) { 1 }
    }

    // ---- CSV ---------------------------------------------------------------------------------------------------

    fun parseCsv(file: ImportFile, zone: ZoneId): Pair<List<SourceVehicle>, List<RowError>> {
        val text = file.text()
        val sections = Sections.split(text, Csv.sniffDelimiter(text)) { it.lowercase().trim() in CSV_TITLES }
        fun section(name: String) = sections.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val fillSection = section("Fill-Up Records")
        val serviceSection = section("Service Records")
        val expenseSection = section("Expense Records")
        val vehicleSection = section("Vehicles")
        val errors = ArrayList<RowError>()
        val allRows = listOfNotNull(fillSection, serviceSection, expenseSection)
        val samples = sequence { for (s in allRows) for (r in s.rows) yieldAll(r.cells) }
        val style = DecimalStyle.sniff(samples)
        val dates = DateParser(DateParser.sniffOrder(samples, SlashOrder.MDY), zone)

        val vehicleInfo = HashMap<String, SourceVehicle>()
        vehicleSection?.let { s ->
            for (row in s.rows) {
                if (row.isBlank) continue
                val name = row.cell(s.col("Name", "Vehicle")) ?: continue
                vehicleInfo[name] = SourceVehicle(
                    key = name, name = name, make = row.cell(s.col("Make")), model = row.cell(s.col("Model")),
                    year = row.cell(s.col("Year"))?.toIntOrNull(),
                    plate = row.cell(s.col("License Plate", "License", "Plate")), vin = row.cell(s.col("VIN")),
                )
            }
        }

        class Acc(
            val fills: ArrayList<SourceFill> = ArrayList(),
            val services: ArrayList<SourceService> = ArrayList(),
            val expenses: ArrayList<SourceExpense> = ArrayList(),
            var distance: DistanceUnit? = null,
            var volume: VolumeUnit? = null,
        )
        val acc = LinkedHashMap<String, Acc>()
        vehicleInfo.keys.forEach { acc[it] = Acc() }
        fun accFor(name: String?) = acc.getOrPut(name ?: "Vehicle") { Acc() }

        fillSection?.let { s ->
            val cVehicle = s.col("Vehicle")
            val cDate = s.col("Date")
            val cTime = s.col("Time")
            val cOdo = s.colStartingWith("Odometer")
            val cDistUnit = s.col("Distance Unit")
            val cVolume = s.col("Volume")
            val cVolUnit = s.col("Volume Unit")
            val cPrice = s.colStartingWith("Price per Unit")
            val cTotal = s.col("Total Cost")
            val cPartial = s.colStartingWith("Partial")
            val cMissed = s.colStartingWith("Previously Missed")
            val cNotes = s.col("Notes")
            for (row in s.rows) {
                if (row.isBlank) continue
                val a = accFor(row.cell(cVehicle))
                row.cell(cDistUnit)?.let { a.distance = distanceOf(it) ?: a.distance }
                row.cell(cVolUnit)?.let { a.volume = volumeOf(it) ?: a.volume }
                val date = dates.parse(row.cell(cDate), row.cell(cTime))
                val volume = NumberParser.parse(row.cell(cVolume), style)
                val odo = NumberParser.parse(row.cell(cOdo), style)
                when {
                    date == null -> errors += RowError(file.baseName, row.line, "Unreadable date")
                    volume == null || volume <= 0.0 -> errors += RowError(file.baseName, row.line, "Unreadable volume")
                    odo == null -> errors += RowError(file.baseName, row.line, "Unreadable odometer")
                    else -> a.fills += SourceFill(
                        line = row.line, dateTime = date, odometer = odo, quantity = volume,
                        pricePerUnit = NumberParser.parse(row.cell(cPrice), style)?.takeIf { it > 0 },
                        total = NumberParser.parse(row.cell(cTotal), style)?.takeIf { it > 0 },
                        isFull = !yes(row.cell(cPartial)), missedPrevious = yes(row.cell(cMissed)),
                        note = row.cell(cNotes),
                    )
                }
            }
        }
        for ((section, isService) in listOf(serviceSection to true, expenseSection to false)) {
            val s = section ?: continue
            val cVehicle = s.col("Vehicle")
            val cDate = s.col("Date")
            val cTime = s.col("Time")
            val cOdo = s.colStartingWith("Odometer")
            val cType = s.col("Type", "Service Type", "Expense Type", "Subtype")
            val cCost = s.col("Total Cost", "Cost")
            val cNotes = s.col("Notes")
            for (row in s.rows) {
                if (row.isBlank) continue
                val a = accFor(row.cell(cVehicle))
                val date = dates.parse(row.cell(cDate), row.cell(cTime))
                val cost = NumberParser.parse(row.cell(cCost), style)
                val odo = NumberParser.parse(row.cell(cOdo), style)?.takeIf { it > 0 }
                val title = row.cell(cType) ?: row.cell(cNotes) ?: if (isService) "Service" else "Expense"
                val note = row.cell(cNotes)?.takeIf { it != title }
                when {
                    date == null -> errors += RowError(file.baseName, row.line, "Unreadable date")
                    isService -> a.services += SourceService(row.line, date, odo, title, cost, note)
                    cost == null -> errors += RowError(file.baseName, row.line, "Unreadable cost")
                    else -> a.expenses += SourceExpense(
                        row.line, date, odo, title, cost,
                        (CostClassifier.classify(title) as? CostClassifier.Kind.Expense)?.category ?: ExpenseCategory.OTHER, note,
                    )
                }
            }
        }

        val vehicles = acc.map { (name, a) ->
            val info = vehicleInfo[name] ?: SourceVehicle(key = name, name = name)
            info.copy(
                key = "${file.name}#$name", distanceUnit = a.distance, volumeUnit = a.volume,
                fills = a.fills, services = a.services, expenses = a.expenses,
            )
        }
        return vehicles to errors
    }

    private fun yes(s: String?): Boolean = s?.trim()?.lowercase() in setOf("yes", "true", "1", "y", "x")
}
