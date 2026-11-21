package com.postpole.wheelfile.importer

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A CSV record: [line] is the 1-based physical line it starts on. */
data class CsvRow(val line: Int, val cells: List<String>) {
    val isBlank: Boolean get() = cells.all { it.isBlank() }
}

object Csv {
    /** Picks `;` over `,` when the first lines use it outside quotes more often (comma-decimal locales). */
    fun sniffDelimiter(text: String): Char {
        var commas = 0
        var semis = 0
        var tabs = 0
        var inQuotes = false
        var lines = 0
        for (c in text) {
            if (c == '"') inQuotes = !inQuotes
            if (inQuotes) continue
            when (c) {
                ',' -> commas++
                ';' -> semis++
                '\t' -> tabs++
                '\n' -> if (++lines >= 12) break
            }
        }
        return when {
            semis > commas && semis >= tabs -> ';'
            tabs > commas && tabs > semis -> '\t'
            else -> ','
        }
    }

    /** RFC 4180 style parser: quoted cells, doubled quotes, CR/LF, multi-line cells. */
    fun parse(text: String, delimiter: Char = sniffDelimiter(text)): List<CsvRow> {
        val rows = ArrayList<CsvRow>()
        var cells = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var line = 1
        var rowLine = 1
        var i = 0
        var any = false
        fun endCell() {
            cells.add(cell.toString())
            cell.setLength(0)
        }
        fun endRow() {
            endCell()
            rows.add(CsvRow(rowLine, cells))
            cells = ArrayList()
            any = false
        }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        cell.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> {
                        if (c == '\n') line++
                        cell.append(c)
                    }
                }
                c == '"' -> {
                    inQuotes = true
                    any = true
                }
                c == delimiter -> {
                    endCell()
                    any = true
                }
                c == '\r' -> Unit
                c == '\n' -> {
                    if (any || cell.isNotEmpty() || cells.isNotEmpty()) endRow() else rows.add(CsvRow(rowLine, listOf("")))
                    line++
                    rowLine = line
                }
                else -> {
                    cell.append(c)
                    any = true
                }
            }
            i++
        }
        if (any || cell.isNotEmpty() || cells.isNotEmpty()) endRow()
        return rows
    }
}

/**
 * A CSV with `##`-style or plain-title section lines. [sections] keeps the order; each has its header row and data rows.
 * Blank lines end a plain section.
 */
class CsvSection(val name: String, val header: CsvRow?, val rows: List<CsvRow>) {
    private val index: Map<String, Int> =
        header?.cells?.mapIndexed { i, h -> normaliseHeader(h) to i }?.reversed()?.toMap().orEmpty()

    fun col(vararg names: String): Int? = names.firstNotNullOfOrNull { n -> index[normaliseHeader(n)] }

    /** First column whose normalised header starts with one of [prefixes]. */
    fun colStartingWith(vararg prefixes: String): Int? =
        header?.cells?.indices?.firstOrNull { i ->
            val h = normaliseHeader(header.cells[i])
            prefixes.any { h.startsWith(normaliseHeader(it)) }
        }

    companion object {
        /** Lowercase, drops the unit suffix "(km)" / "(optional)", and everything but letters and digits. */
        fun normaliseHeader(h: String): String =
            h.lowercase().replace(Regex("\\(.*?\\)"), "").filter { it.isLetterOrDigit() }
    }
}

fun CsvRow.cell(i: Int?): String? = if (i == null || i < 0 || i >= cells.size) null else cells[i].trim().ifEmpty { null }

/** How a file writes decimals. Sources carry no locale, so it is sniffed from the numbers themselves. */
enum class DecimalStyle {
    DOT,
    COMMA,
    ;

    companion object {
        private val numeric = Regex("^[-+]?[\\d.,\\s\\u00a0']+$")

        /** COMMA when any number looks like `12,5`, `1.234,56`; otherwise DOT (so `1,234` stays a thousand separator). */
        fun sniff(samples: Sequence<String>): DecimalStyle {
            var comma = false
            var dot = false
            for (raw in samples) {
                val s = raw.trim()
                if (s.isEmpty() || !numeric.matches(s) || s.none { it.isDigit() }) continue
                val lastComma = s.lastIndexOf(',')
                val lastDot = s.lastIndexOf('.')
                if (lastComma >= 0 && lastDot >= 0) {
                    if (lastComma > lastDot) comma = true else dot = true
                } else if (lastComma >= 0) {
                    val after = s.length - lastComma - 1
                    if (after != 3 || s.count { it == ',' } == 1 && s.substringBefore(',').trimStart('-', '+') == "0") comma = true
                } else if (lastDot >= 0) {
                    val dots = s.count { it == '.' }
                    val after = s.length - lastDot - 1
                    when {
                        // 1.234.567 groups thousands with dots, so the decimal mark is a comma.
                        dots > 1 && Regex("^[-+]?\\d{1,3}(\\.\\d{3})+$").matches(s) -> comma = true
                        // 15.01.2024 is a date, not a number.
                        dots > 1 -> Unit
                        // 12.5 is a decimal; 1.234 alone is ambiguous and left to the DOT default.
                        after != 3 -> dot = true
                    }
                }
            }
            return if (comma && !dot) COMMA else DOT
        }
    }
}

object NumberParser {
    /** Parses `1.234,56`, `12,5`, `$1,299.00`, `EUR 45.10`, `-3`. Null when no number is found. */
    fun parse(raw: String?, style: DecimalStyle): Double? {
        if (raw == null) return null
        var s = raw.trim().filter { it.isDigit() || it == '.' || it == ',' || it == '-' || it == '+' }
        if (s.isEmpty() || s.none { it.isDigit() }) return null
        val negative = s.startsWith("-")
        s = s.replace("-", "").replace("+", "")
        val decimal = if (style == DecimalStyle.COMMA) ',' else '.'
        val thousands = if (style == DecimalStyle.COMMA) '.' else ','
        s = s.replace(thousands.toString(), "")
        if (s.count { it == decimal } > 1) return null
        s = s.replace(decimal, '.')
        val value = s.toDoubleOrNull() ?: return null
        return if (negative) -value else value
    }
}

/** Slash dates are ambiguous: D/M/YYYY (default for most of the world) or M/D/YYYY. */
enum class SlashOrder { DMY, MDY }

class DateParser(private val order: SlashOrder, private val zone: ZoneId) {
    /** Epoch millis for a date, with an optional separate [time] cell. Null when the text is not a date. */
    fun parse(date: String?, time: String? = null): Long? {
        val d = date?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        d.toLongOrNull()?.let { if (d.length >= 10) return if (d.length >= 13) it else it * 1000 }
        val parsed = parseDateTime(d) ?: return null
        val t = time?.trim()?.takeIf { it.isNotEmpty() }?.let(::parseTime)
        val dt = if (t != null && parsed.toLocalTime() == LocalTime.MIDNIGHT) parsed.toLocalDate().atTime(t) else parsed
        return dt.atZone(zone).toInstant().toEpochMilli()
    }

    private fun parseTime(t: String): LocalTime? {
        val clean = t.replace(Regex("(?i)\\s*([ap])\\.?m\\.?$")) { " ${it.groupValues[1].uppercase()}M" }
        for (f in TIME_FORMATS) try {
            return LocalTime.parse(clean, f)
        } catch (_: DateTimeParseException) {
            // try the next pattern
        }
        return null
    }

    private fun parseDateTime(d: String): LocalDateTime? {
        runCatching { return java.time.OffsetDateTime.parse(d).atZoneSameInstant(zone).toLocalDateTime() }
        runCatching { return LocalDateTime.parse(d) }
        // Split "date time" on the first space or 'T'.
        val m = Regex("^(\\S+?)(?:[ T](\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s?[AaPp]\\.?[Mm]\\.?)?))?$").find(d) ?: return null
        val datePart = m.groupValues[1]
        val timePart = m.groupValues[2].ifEmpty { null }
        val date = parseDate(datePart) ?: return null
        val time = timePart?.let(::parseTime) ?: LocalTime.MIDNIGHT
        return date.atTime(time)
    }

    private fun parseDate(s: String): LocalDate? {
        Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$").find(s)?.let {
            return safeDate(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        Regex("^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})$").find(s)?.let {
            val a = it.groupValues[1].toInt()
            val b = it.groupValues[2].toInt()
            var y = it.groupValues[3].toInt()
            if (it.groupValues[3].length == 2) y += 2000
            val dotted = s.contains('.') || s.contains('-')
            val dayFirst = dotted || order == SlashOrder.DMY
            // An unambiguous value wins over the sniffed order.
            return if (dayFirst && !(a <= 12 && b > 12 && !dotted)) {
                safeDate(y, b, a) ?: safeDate(y, a, b)
            } else {
                safeDate(y, a, b) ?: safeDate(y, b, a)
            }
        }
        return null
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = try {
        LocalDate.of(y, m, d)
    } catch (_: java.time.DateTimeException) {
        null
    }

    companion object {
        private val TIME_FORMATS = listOf(
            DateTimeFormatter.ofPattern("H:mm:ss"),
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mm:ss a", java.util.Locale.ENGLISH),
        )

        /** Looks at every slash date: a first part above 12 means D/M, a second part above 12 means M/D. */
        fun sniffOrder(samples: Sequence<String>, default: SlashOrder): SlashOrder {
            var dmy = false
            var mdy = false
            val re = Regex("^(\\d{1,2})/(\\d{1,2})/\\d{2,4}")
            for (s in samples) {
                val m = re.find(s.trim()) ?: continue
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                if (a > 12) dmy = true
                if (b > 12) mdy = true
            }
            return when {
                dmy && !mdy -> SlashOrder.DMY
                mdy && !dmy -> SlashOrder.MDY
                else -> default
            }
        }
    }
}

/** Splits CSV text into sections. A section line is a single non-empty cell starting with `#` (`## Log`) or a known title. */
object Sections {
    fun split(text: String, delimiter: Char = Csv.sniffDelimiter(text), isTitle: (String) -> Boolean = { false }): List<CsvSection> {
        val rows = Csv.parse(text, delimiter)
        val out = ArrayList<CsvSection>()
        var name: String? = null
        var header: CsvRow? = null
        var data = ArrayList<CsvRow>()
        var plain = false
        fun flush() {
            name?.let { out.add(CsvSection(it, header, data)) }
            header = null
            data = ArrayList()
        }
        for (row in rows) {
            val nonEmpty = row.cells.filter { it.isNotBlank() }
            val first = nonEmpty.firstOrNull()?.trim()
            val titleLine = nonEmpty.size == 1 && first != null && (first.startsWith("#") || isTitle(first))
            when {
                titleLine -> {
                    flush()
                    name = first!!.trimStart('#').trim()
                    plain = !first.startsWith("#")
                }
                nonEmpty.isEmpty() -> if (plain) {
                    flush()
                    name = null
                }
                name == null -> Unit
                header == null -> header = row
                else -> data.add(row)
            }
        }
        flush()
        return out
    }
}
