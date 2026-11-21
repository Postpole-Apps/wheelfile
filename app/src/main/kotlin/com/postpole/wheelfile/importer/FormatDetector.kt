package com.postpole.wheelfile.importer

import com.postpole.wheelfile.importer.acar.AcarParser
import com.postpole.wheelfile.importer.drivvo.DrivvoParser
import com.postpole.wheelfile.importer.fuelio.FuelioParser
import com.postpole.wheelfile.importer.simplyauto.SimplyAutoParser
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.ZoneId
import java.util.zip.ZipInputStream

/**
 * Looks at the content of a picked file (section headers, ZIP entries, column names), never at its name alone,
 * and hands it to the matching parser. Pure Kotlin, no network.
 */
object FormatDetector {
    private const val MAX_ENTRIES = 500
    private const val MAX_TOTAL_BYTES = 200L * 1024 * 1024

    /** Turns a picked file into the files inside it: a ZIP/`.abp` is unpacked (also nested once), anything else is kept. */
    fun expand(file: ImportFile, depth: Int = 0): List<ImportFile> {
        if (!isZip(file.bytes) || depth > 2) return listOf(file)
        val out = ArrayList<ImportFile>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(file.bytes)).use { zip ->
                while (out.size < MAX_ENTRIES) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val bytes = zip.readUpTo((MAX_TOTAL_BYTES - total).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    total += bytes.size
                    if (total >= MAX_TOTAL_BYTES) break
                    out += expand(ImportFile(entry.name, bytes), depth + 1)
                }
            }
        } catch (_: IOException) {
            // A damaged ZIP yields whatever was readable.
        }
        return out
    }

    private fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() && b[2].toInt() == 3 && b[3].toInt() == 4

    fun detect(files: List<ImportFile>): ImportFormat? {
        val text = files.filter { isTextual(it) }
        if (text.any { it.baseName.equals("vehicles.xml", true) && AcarParser.isBackupXml(it.name, it.text()) }) return ImportFormat.ACAR
        if (text.any { it.baseName.endsWith(".csv", true) && FuelioParser.looksLike(it.text()) }) return ImportFormat.FUELIO
        if (text.any { SimplyAutoParser.isFuelLog(it) }) return ImportFormat.SIMPLY_AUTO
        if (text.any { AcarParser.looksLikeCsv(it.text()) }) return ImportFormat.ACAR
        if (text.any { DrivvoParser.looksLike(it.text()) }) return ImportFormat.DRIVVO
        return null
    }

    private fun isTextual(f: ImportFile): Boolean {
        val n = f.baseName.lowercase()
        return n.endsWith(".csv") || n.endsWith(".xml") || n.endsWith(".txt") || !n.contains('.')
    }

    /** Null when the content is none of the supported formats. */
    fun parse(picked: ImportFile, zone: ZoneId = ZoneId.systemDefault()): ParsedImport? = parse(expand(picked), zone)

    fun parse(files: List<ImportFile>, zone: ZoneId = ZoneId.systemDefault()): ParsedImport? {
        val format = detect(files) ?: return null
        val text = files.filter { isTextual(it) }
        val vehicles = ArrayList<SourceVehicle>()
        val errors = ArrayList<RowError>()
        when (format) {
            ImportFormat.FUELIO -> text.filter { it.baseName.endsWith(".csv", true) && FuelioParser.looksLike(it.text()) }.forEach {
                val (v, e) = FuelioParser.parse(it, zone)
                v?.let(vehicles::add)
                errors += e
            }
            ImportFormat.DRIVVO -> text.filter { DrivvoParser.looksLike(it.text()) }.forEach {
                val (v, e) = DrivvoParser.parse(it, zone)
                v?.let(vehicles::add)
                errors += e
            }
            ImportFormat.ACAR -> {
                val xml = text.firstOrNull { it.baseName.equals("vehicles.xml", true) && AcarParser.isBackupXml(it.name, it.text()) }
                if (xml != null) {
                    val (v, e) = AcarParser.parseXml(xml, zone)
                    vehicles += v
                    errors += e
                } else {
                    text.filter { AcarParser.looksLikeCsv(it.text()) }.forEach {
                        val (v, e) = AcarParser.parseCsv(it, zone)
                        vehicles += v
                        errors += e
                    }
                }
            }
            ImportFormat.SIMPLY_AUTO -> {
                val (v, e) = SimplyAutoParser.parse(text, zone)
                vehicles += v
                errors += e
            }
        }
        return ParsedImport(format, vehicles, errors)
    }
}
