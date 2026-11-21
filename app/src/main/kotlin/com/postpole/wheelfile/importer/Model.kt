package com.postpole.wheelfile.importer

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.VolumeUnit

/** The source apps Wheelfile can read. Names appear only in importer code (spec §6.2, D3). */
enum class ImportFormat(val beta: Boolean = false) {
    FUELIO,
    DRIVVO(beta = true),
    ACAR,
    SIMPLY_AUTO,
}

/** One file handed to the importer: a picked CSV/XML, or an entry of a picked ZIP. */
class ImportFile(val name: String, val bytes: ByteArray) {
    val baseName: String get() = name.substringAfterLast('/').substringAfterLast('\\')
    /** UTF-8 text without a leading byte order mark. */
    fun text(): String = String(bytes, Charsets.UTF_8).let { if (it.isNotEmpty() && it[0].code == BOM) it.substring(1) else it }

    private companion object {
        const val BOM = 0xFEFF
    }
}

/** Reads at most [limit] bytes (API 26 has no `readNBytes`). */
fun java.io.InputStream.readUpTo(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** A row or record that could not be read, with the 1-based line number in its file. */
data class RowError(val file: String, val line: Int, val reason: String)

data class SourceFill(
    val line: Int,
    val dateTime: Long,
    val odometer: Double,
    val quantity: Double,
    /** Major currency units (for example 1.859), converted to minor units at import. */
    val pricePerUnit: Double?,
    val total: Double?,
    val isFull: Boolean,
    val missedPrevious: Boolean,
    val station: String? = null,
    val note: String? = null,
    val fuelGrade: String? = null,
)

data class SourceService(
    val line: Int,
    val dateTime: Long,
    val odometer: Double?,
    val title: String,
    val total: Double?,
    val note: String? = null,
)

data class SourceExpense(
    val line: Int,
    val dateTime: Long,
    val odometer: Double?,
    val title: String,
    val amount: Double,
    val category: ExpenseCategory,
    val note: String? = null,
)

/** One vehicle found in a file. Units are null when the source does not say (the importer then uses device defaults). */
data class SourceVehicle(
    val key: String,
    val name: String,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val plate: String? = null,
    val vin: String? = null,
    val distanceUnit: DistanceUnit? = null,
    val volumeUnit: VolumeUnit? = null,
    val fills: List<SourceFill> = emptyList(),
    val services: List<SourceService> = emptyList(),
    val expenses: List<SourceExpense> = emptyList(),
) {
    val firstDate: Long? get() = allDates().minOrNull()
    val lastDate: Long? get() = allDates().maxOrNull()
    private fun allDates() = fills.map { it.dateTime } + services.map { it.dateTime } + expenses.map { it.dateTime }
}

data class ParsedImport(
    val format: ImportFormat,
    val vehicles: List<SourceVehicle>,
    val errors: List<RowError>,
) {
    val fillCount get() = vehicles.sumOf { it.fills.size }
    val serviceCount get() = vehicles.sumOf { it.services.size }
    val expenseCount get() = vehicles.sumOf { it.expenses.size }
}

/** Maps a service or expense title to the closest Wheelfile bucket. */
object CostClassifier {
    sealed interface Kind {
        data object Service : Kind
        data class Expense(val category: ExpenseCategory) : Kind
    }

    fun classify(text: String): Kind {
        val t = text.lowercase()
        fun has(vararg words: String) = words.any { it in t }
        return when {
            has("insur", "seguro", "versicher", "assurance") -> Kind.Expense(ExpenseCategory.INSURANCE)
            has("tax", "regist", "licen", "imposto", "ipva", "steuer", "impuesto") -> Kind.Expense(ExpenseCategory.TAX_REGISTRATION)
            has("park", "estacion", "parkplatz") -> Kind.Expense(ExpenseCategory.PARKING)
            has("toll", "pedagio", "peaje", "maut") -> Kind.Expense(ExpenseCategory.TOLLS)
            has("wash", "lavag", "lavado", "wäsche", "waesche") -> Kind.Expense(ExpenseCategory.WASHING)
            has("fine", "ticket", "multa", "bußgeld", "bussgeld") -> Kind.Expense(ExpenseCategory.FINES)
            has("loan", "lease", "financ", "leasing") -> Kind.Expense(ExpenseCategory.LOAN_LEASE)
            has("accessor", "acess", "accesorio") -> Kind.Expense(ExpenseCategory.ACCESSORIES)
            has("service", "repair", "maint", "oil", "tyre", "tire", "brake", "filter", "revis", "manuten", "reparo", "wartung", "mecan") ->
                Kind.Service
            else -> Kind.Expense(ExpenseCategory.OTHER)
        }
    }
}
