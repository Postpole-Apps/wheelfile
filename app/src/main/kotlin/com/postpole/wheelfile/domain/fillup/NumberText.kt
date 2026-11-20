package com.postpole.wheelfile.domain.fillup

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Parsing and formatting of the decimal text typed into form fields. */
object NumberText {
    private val allowed = Regex("^\\d*([.,]\\d*)?$")

    /** True for text a decimal field may hold: digits and at most one `.` or `,`. */
    fun isValidInput(text: String): Boolean = allowed.matches(text)

    /** Parses "1.859" or "1,859"; null for empty or incomplete text such as "." */
    fun parse(text: String): Double? {
        val t = text.trim()
        if (t.isEmpty() || !allowed.matches(t)) return null
        return t.replace(',', '.').toDoubleOrNull()
    }

    /** Formats without grouping, with between [minDigits] and [maxDigits] decimals, using the locale's separator. */
    fun format(value: Double, minDigits: Int, maxDigits: Int, locale: Locale): String {
        val format = DecimalFormat("0", DecimalFormatSymbols(locale))
        format.isGroupingUsed = false
        format.minimumFractionDigits = minDigits
        format.maximumFractionDigits = maxDigits
        return format.format(value)
    }
}
