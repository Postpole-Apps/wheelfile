package com.postpole.wheelfile.importer

import com.postpole.wheelfile.data.db.ExpenseCategory
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** One CSV record. [line] is the 1-based physical line the record starts on. */
data class CsvRow(val line: Int, val cells: List<String>) {
    val isBlank: Boolean get() = cells.all { it.isBlank() }
    fun cell(i: Int): String? = cells.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
}

object Csv {
    /** Comma or semicolon, whichever appears more often outside quotes in the first lines. */
    fun sniffDelimiter(text: String): Char {
        var commas = 0
        var semis = 0
        var inQuotes = false
        var lines = 0
        for (c in text) {
            if (c == '"') inQuotes = !inQuotes
            if (!inQuotes) {
                if (c == ',') commas++
                if (c == ';') semis++
                if (c == '\n' && ++lines >= 30) break
            }
        }
        return if (semis > commas) ';' else ','
    }

    /** RFC 4180 style records with quoted fields (quotes doubled, newlines allowed inside quotes). */
    fun parse(text: String, delimiter: Char = sniffDelimiter(text)): List<CsvRow> {
        val rows = ArrayList<CsvRow>()
        var cells = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var line = 1
        var startLine = 1
        var i = 0
        var sawAny = false

        fun endField() {
            cells.add(field.toString())
            field.clear()
        }

        fun endRecord() {
            endField()
            rows.add(CsvRow(startLine, cells))
            cells = ArrayList()
            sawAny = false
        }

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && text.getOrNull(i + 1) == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> {
                        if (c == '\n') line++
                        field.append(c)
                    }
                }
                c == '"' && field.isEmpty() -> {
                    inQuotes = true
                    sawAny = true
                }
                c == delimiter -> {
                    endField()
                    sawAny = true
                }
                c == '\r' -> Unit
                c == '\n' -> {
                    if (sawAny || field.isNotEmpty() || cells.isNotEmpty()) endRecord() else rows.add(CsvRow(startLine, listOf("")))
                    line++
                    startLine = line
                }
                else -> {
                    field.append(c)
                    sawAny = true
                }
            }
            i++
        }
        if (sawAny || field.isNotEmpty() || cells.isNotEmpty()) endRecord()
        return rows
    }
}

object Parse {
    private val YES = setOf("yes", "y", "true", "1", "si", "sim", "ja", "tak", "oui", "s", "j", "t", "x")

    fun bool(raw: String?): Boolean? {
        val v = fold(raw ?: return null)
        return when {
            v.isEmpty() -> null
            v in YES -> true
            v in NO -> false
            else -> null
        }
    }

    private val NO = setOf("no", "n", "false", "0", "nie", "nein", "nao", "non", "-")

    /** Lower case, accents removed, so "Não" and "nao" compare equal. */
    fun fold(raw: String): String =
        Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /**
     * Locale tolerant number: "1,234.56", "1.234,56", "12,5", "$1,234.50", "134,417".
     * With a single separator followed by exactly three digits, [threeDigitsAreGrouping] decides whether it is a
     * thousands separator (odometers: "134,417") or a decimal mark (volumes: "1,234").
     */
    fun number(raw: String?, threeDigitsAreGrouping: Boolean = false): Double? {
        if (raw == null) return null
        var s = raw.trim().replace(' ', ' ').replace(" ", "").replace("'", "")
        val negative = s.startsWith("-") || (s.startsWith("(") && s.endsWith(")"))
        s = s.filter { it.isDigit() || it == '.' || it == ',' }
        if (s.isEmpty() || s.none { it.isDigit() }) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val normalised = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimal = if (lastDot > lastComma) '.' else ','
                val group = if (decimal == '.') ',' else '.'
                s.replace(group.toString(), "").replace(decimal, '.')
            }
            lastDot >= 0 || lastComma >= 0 -> {
                val sep = if (lastDot >= 0) '.' else ','
                val count = s.count { it == sep }
                val after = s.length - 1 - s.lastIndexOf(sep)
                when {
                    count > 1 -> s.replace(sep.toString(), "")
                    after == 3 && threeDigitsAreGrouping && s.indexOf(sep) in 1..3 -> s.replace(sep.toString(), "")
                    else -> s.replace(sep, '.')
                }
            }
            else -> s
        }
        val value = normalised.toDoubleOrNull() ?: return null
        return if (negative) -value else value
    }

    /** Minor units for [major] in a currency with [fractionDigits] decimals. */
    fun minor(major: Double, fractionDigits: Int): Long =
        java.math.BigDecimal.valueOf(major).movePointRight(fractionDigits).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
}

/** Whether "03/04/2025" is 3 April (day first) or March 4 (month first). Decided per file, never per row. */
enum class DateOrder { DAY_FIRST, MONTH_FIRST }

/** Dates and times of the supported sources. All results are epoch milliseconds in [zone]. */
class DateParser(private val zone: ZoneId, private val order: DateOrder = DateOrder.DAY_FIRST) {

    fun parse(raw: String?, time: String? = null): Long? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (s.all { it.isDigit() } && s.length >= 9) {
            val n = s.toLongOrNull() ?: return null
            return if (s.length >= 12) n else n * 1000
        }
        parseIsoOffset(s)?.let { return it }
        val parts = Regex("^(\\d{1,4})[-/. ](\\d{1,2})[-/. ](\\d{1,4})[T ,]*(.*)$").matchEntire(s) ?: return null
        val (a, b, c, rest) = parts.destructured
        val date = toDate(a.toInt(), b.toInt(), c.toInt(), a.length) ?: return null
        val t = parseTime(rest.ifBlank { time ?: "" }) ?: LocalTime.NOON.takeIf { rest.isBlank() && time.isNullOrBlank() } ?: return null
        return LocalDateTime.of(date, t).atZone(zone).toInstant().toEpochMilli()
    }

    private fun parseIsoOffset(s: String): Long? {
        val m = Regex("^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}(?::\\d{2})?(?:\\.\\d+)?)\\s*(Z|[+-]\\d{2}:?\\d{2})$").matchEntire(s)
            ?: return null
        // "2014-01-05 10:00 +0000" -> "2014-01-05T10:00:00+00:00", the shape OffsetDateTime expects.
        val time = m.groupValues[2].let { if (it.count { c -> c == ':' } == 1) "$it:00" else it }
        val offset = m.groupValues[3].let { if (it == "Z" || it.contains(':')) it else it.substring(0, 3) + ":" + it.substring(3) }
        return try {
            OffsetDateTime.parse("${m.groupValues[1]}T$time$offset").toInstant().toEpochMilli()
        } catch (e: java.time.format.DateTimeParseException) {
            null
        }
    }

    private fun toDate(a: Int, b: Int, c: Int, firstLength: Int): LocalDate? = try {
        when {
            firstLength == 4 -> LocalDate.of(a, b, c)
            order == DateOrder.DAY_FIRST -> LocalDate.of(fullYear(c), b, a)
            else -> LocalDate.of(fullYear(c), a, b)
        }
    } catch (e: java.time.DateTimeException) {
        null
    }

    private fun fullYear(y: Int) = if (y < 100) 2000 + y else y

    private fun parseTime(raw: String): LocalTime? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val m = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:\\.\\d+)?\\s*([AaPp][Mm])?$").matchEntire(s) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val sec = m.groupValues[3].ifEmpty { "0" }.toInt()
        val mer = m.groupValues[4].lowercase()
        if (mer == "pm" && h < 12) h += 12
        if (mer == "am" && h == 12) h = 0
        return try {
            LocalTime.of(h, min, sec)
        } catch (e: java.time.DateTimeException) {
            null
        }
    }

    companion object {
        /**
         * Looks at every date-like sample of a file: a first part above 12 means day first, a second part above 12
         * means month first. If every sample is ambiguous the file's regional default [fallback] wins.
         */
        fun inferOrder(samples: Sequence<String?>, fallback: DateOrder = DateOrder.DAY_FIRST): DateOrder {
            val regex = Regex("^(\\d{1,2})[-/. ](\\d{1,2})[-/. ]\\d{2,4}")
            for (s in samples) {
                val m = regex.find(s?.trim() ?: continue) ?: continue
                val first = m.groupValues[1].toInt()
                val second = m.groupValues[2].toInt()
                if (first > 12) return DateOrder.DAY_FIRST
                if (second > 12) return DateOrder.MONTH_FIRST
            }
            return fallback
        }
    }
}

/** Splits a source's service and expense titles into a Wheelfile service record or an expense category. */
object EntryClassifier {
    sealed interface Kind {
        data object Service : Kind
        data class Expense(val category: ExpenseCategory) : Kind
    }

    private val SERVICE_WORDS = listOf(
        "service", "servicio", "servico", "repair", "reparatur", "reparac", "maint", "manuten", "mantenim", "wartung",
        "oil", "oleo", "aceite", "tyre", "tire", "pneu", "brake", "freio", "freno", "battery", "bateria", "inspection",
        "filter", "filtro", "mechanic", "oficina", "taller", "werkstatt",
    )

    fun classify(title: String): Kind {
        val t = Parse.fold(title)
        fun has(vararg words: String) = words.any { t.contains(it) }
        return when {
            has("insur", "seguro", "versicher", "assicur") -> Kind.Expense(ExpenseCategory.INSURANCE)
            has("parking", "estacionamento", "parkplatz", "aparcamiento") -> Kind.Expense(ExpenseCategory.PARKING)
            has("toll", "pedagio", "peaje", "maut") -> Kind.Expense(ExpenseCategory.TOLLS)
            has("wash", "lavagem", "lavado", "autowasch") -> Kind.Expense(ExpenseCategory.WASHING)
            has("fine", "multa", "ticket", "bussgeld") -> Kind.Expense(ExpenseCategory.FINES)
            has("tax", "imposto", "impuesto", "steuer", "registration", "licen", "ipva") -> Kind.Expense(ExpenseCategory.TAX_REGISTRATION)
            has("loan", "lease", "leasing", "financ") -> Kind.Expense(ExpenseCategory.LOAN_LEASE)
            has("accessor", "acessor") -> Kind.Expense(ExpenseCategory.ACCESSORIES)
            SERVICE_WORDS.any { t.contains(it) } -> Kind.Service
            else -> Kind.Expense(ExpenseCategory.OTHER)
        }
    }
}
