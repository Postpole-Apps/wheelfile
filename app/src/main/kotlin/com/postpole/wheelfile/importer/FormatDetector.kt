package com.postpole.wheelfile.importer

import com.postpole.wheelfile.importer.acar.AcarParser
import com.postpole.wheelfile.importer.drivvo.DrivvoParser
import com.postpole.wheelfile.importer.fuelio.FuelioParser
import com.postpole.wheelfile.importer.simplyauto.SimplyAutoParser
import java.time.ZoneId

/** Detects the source app from the content (ZIP entries, section headings), never from the file name alone. */
object FormatDetector {
    fun detect(input: ImportInput): ImportFormat? {
        if (AcarParser.isBackup(input)) return ImportFormat.ACAR_BACKUP
        if (SimplyAutoParser.looksLike(input)) return ImportFormat.SIMPLY_AUTO
        val texts = input.files.values.map { ImportInput.decode(it) }
        val fuelio = texts.count { FuelioParser.looksLike(it) }
        if (fuelio > 0) return if (input.files.size > 1) ImportFormat.FUELIO_BACKUP_ZIP else ImportFormat.FUELIO_CSV
        if (texts.any { AcarParser.looksLikeCsv(it) }) return ImportFormat.ACAR_CSV
        if (texts.any { DrivvoParser.looksLike(it) }) return ImportFormat.DRIVVO_CSV
        return null
    }
}

/** Detects the format and parses it. Throws [ImportException] when the file isn't one of the supported formats. */
object ImportParser {
    fun parse(input: ImportInput, zone: ZoneId = ZoneId.systemDefault()): ParsedFile {
        val format = FormatDetector.detect(input) ?: throw ImportException("Unrecognised file")
        val parsed = when (format) {
            ImportFormat.FUELIO_CSV, ImportFormat.FUELIO_BACKUP_ZIP -> FuelioParser.parse(input, zone)
            ImportFormat.ACAR_BACKUP, ImportFormat.ACAR_CSV -> AcarParser.parse(
                if (format == ImportFormat.ACAR_CSV) ImportInput(input.files.filterValues { AcarParser.looksLikeCsv(ImportInput.decode(it)) }) else input,
                zone,
            )
            ImportFormat.SIMPLY_AUTO -> SimplyAutoParser.parse(input, zone)
            ImportFormat.DRIVVO_CSV -> DrivvoParser.parse(
                ImportInput(input.files.filterValues { DrivvoParser.looksLike(ImportInput.decode(it)) }),
                zone,
            )
        }
        return parsed
    }
}
