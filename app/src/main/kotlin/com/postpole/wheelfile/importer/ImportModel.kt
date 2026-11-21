package com.postpole.wheelfile.importer

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.VolumeUnit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Which kind of history file was detected. Names are for parser code and the result report only. */
enum class ImportFormat(val sourceTag: String, val beta: Boolean = false) {
    FUELIO_CSV("fuelio"),
    FUELIO_BACKUP_ZIP("fuelio"),
    ACAR_BACKUP("acar"),
    ACAR_CSV("acar"),
    SIMPLY_AUTO("simplyauto"),
    DRIVVO_CSV("drivvo", beta = true),
}

/** The files behind one picked file: a single CSV, or the entries of a ZIP (.abp and Backup-All are ZIPs). */
class ImportInput(val files: Map<String, ByteArray>) {
    fun text(name: String): String = decode(files.getValue(name))

    companion object {
        private const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        fun fromBytes(name: String, bytes: ByteArray): ImportInput {
            val isZip = bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(ZIP_MAGIC)
            if (!isZip) return ImportInput(mapOf(name to bytes))
            val out = LinkedHashMap<String, ByteArray>()
            var total = 0L
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val n = zip.read(chunk)
                        if (n < 0) break
                        total += n
                        // Entry names are only used as map keys, never as file paths.
                        if (total > MAX_TOTAL_BYTES) throw ImportException("The file is too large to import.")
                        buffer.write(chunk, 0, n)
                    }
                    out[entry.name] = buffer.toByteArray()
                }
            }
            return ImportInput(out)
        }

        /** UTF-8 (with or without BOM); falls back to Latin-1 for files that aren't valid UTF-8. */
        fun decode(bytes: ByteArray): String {
            val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
            val decoder = Charsets.UTF_8.newDecoder()
            return try {
                decoder.decode(java.nio.ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
            } catch (e: java.nio.charset.CharacterCodingException) {
                String(bytes, start, bytes.size - start, Charsets.ISO_8859_1)
            }
        }
    }
}

class ImportException(message: String) : Exception(message)

/** A row that could not be turned into an entry. [line] is the 1-based line in [file]. */
data class RowIssue(val file: String, val line: Int, val reason: String)

/** Money is kept in major units here; the minor-unit conversion needs the currency the user picks. */
data class ParsedEnergy(
    val line: Int,
    val dateTime: Long,
    val odometer: Double,
    val quantity: Double,
    val totalMajor: Double?,
    val pricePerUnitMajor: Double?,
    val isFull: Boolean,
    val missedPrevious: Boolean,
    val fuelGrade: String? = null,
    val station: String? = null,
    val note: String? = null,
)

data class ParsedService(
    val line: Int,
    val dateTime: Long,
    val odometer: Double?,
    val title: String,
    val totalMajor: Double?,
    val note: String? = null,
)

data class ParsedExpense(
    val line: Int,
    val dateTime: Long,
    val odometer: Double?,
    val category: ExpenseCategory,
    val title: String,
    val amountMajor: Double,
    val note: String? = null,
)

data class ParsedVehicle(
    /** Unique within the file; the preview maps it to a new or existing vehicle. */
    val key: String,
    val name: String,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val plate: String? = null,
    val vin: String? = null,
    /** Null when the file doesn't say (the preview then asks, defaulting to the device locale). */
    val distanceUnit: DistanceUnit? = null,
    val volumeUnit: VolumeUnit? = null,
    val energy: List<ParsedEnergy> = emptyList(),
    val services: List<ParsedService> = emptyList(),
    val expenses: List<ParsedExpense> = emptyList(),
) {
    /** First and last entry date across all entry types, or null for an empty vehicle. */
    val dateRange: Pair<Long, Long>?
        get() {
            val all = energy.map { it.dateTime } + services.map { it.dateTime } + expenses.map { it.dateTime }
            return if (all.isEmpty()) null else all.min() to all.max()
        }
}

data class ParsedFile(
    val format: ImportFormat,
    val vehicles: List<ParsedVehicle>,
    val issues: List<RowIssue>,
) {
    val beta: Boolean get() = format.beta
}

/** Reports what happened after the commit (spec §2.7: "Imported 312 fill-ups, 41 services; skipped 3 duplicates ..."). */
data class ImportResult(
    val fillUps: Int,
    val services: Int,
    val expenses: Int,
    val skippedDuplicates: Int,
    val unreadable: List<RowIssue>,
    val vehiclesCreated: Int,
    val archivedVehicles: Int,
    val beta: Boolean,
) {
    val imported: Int get() = fillUps + services + expenses
}
