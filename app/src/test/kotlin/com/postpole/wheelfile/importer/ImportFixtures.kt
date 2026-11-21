package com.postpole.wheelfile.importer

import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Synthetic fixtures under `src/test/resources/import/` (no third-party data). */
object ImportFixtures {
    val zone: ZoneOffset = ZoneOffset.UTC

    fun bytes(path: String): ByteArray =
        checkNotNull(ImportFixtures::class.java.getResourceAsStream("/import/$path")) { "missing fixture $path" }.use { it.readBytes() }

    fun csv(path: String): ImportInput = ImportInput.fromBytes(path.substringAfterLast('/'), bytes(path))

    /** A ZIP made from fixtures: entry name to fixture path. */
    fun zip(entries: Map<String, String>): ImportInput = ImportInput.fromBytes("archive.zip", zipBytes(entries))

    fun zipBytes(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, path) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes(path))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun simplyAuto(): ImportInput = ImportInput(
        mapOf("Vehicles.csv" to bytes("simplyauto/Vehicles.csv"), "Fuel_Log.csv" to bytes("simplyauto/Fuel_Log.csv")),
    )

    fun acarBackup(): ImportInput = zip(
        mapOf(
            "metadata.inf" to "acar/event-subtypes.xml", // content is irrelevant for detection
            "vehicles.xml" to "acar/vehicles.xml",
            "event-subtypes.xml" to "acar/event-subtypes.xml",
        ),
    )
}
