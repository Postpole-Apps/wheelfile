package com.postpole.wheelfile.importer

/** A named block of a sectioned CSV (Fuelio "## Log", Drivvo "##Refuelling", aCar "Fill-Up Records"). */
class Section(val name: String, val rows: List<CsvRow>)

object Sections {
    /** Splits rows at heading rows: [isHeading] returns the section name for a heading row, else null. */
    fun split(rows: List<CsvRow>, isHeading: (CsvRow) -> String? = ::hashHeading): List<Section> {
        val out = ArrayList<Section>()
        var name: String? = null
        var current = ArrayList<CsvRow>()
        fun flush() {
            name?.let { out.add(Section(it, current)) }
            current = ArrayList()
        }
        for (row in rows) {
            val heading = isHeading(row)
            if (heading != null) {
                flush()
                name = heading
            } else if (name != null && !row.isBlank) {
                current.add(row)
            }
        }
        flush()
        return out
    }

    /** A row whose only filled cell starts with '#' (Fuelio "## Log", Drivvo "##Refuelling"). */
    fun hashHeading(row: CsvRow): String? {
        val first = row.cells.firstOrNull()?.trim() ?: return null
        if (!first.startsWith("#") || row.cells.drop(1).any { it.isNotBlank() }) return null
        return first.trimStart('#').trim()
    }
}

/** Header sniffing: finds a column by fragments of its (folded) title, so units and translations don't matter. */
class Columns(header: List<String>) {
    private val folded = header.map { Parse.fold(it) }

    /** Index of the first column whose title contains any of [needles], or null. */
    fun find(vararg needles: String): Int? =
        folded.indexOfFirst { h -> needles.any { h.contains(it) } }.takeIf { it >= 0 }

    /** Index of the first column whose title equals one of [names] exactly. */
    fun exact(vararg names: String): Int? =
        folded.indexOfFirst { it in names }.takeIf { it >= 0 }
}
