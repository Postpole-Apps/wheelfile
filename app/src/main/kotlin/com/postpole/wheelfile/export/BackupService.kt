package com.postpole.wheelfile.export

import android.content.Context
import androidx.room.withTransaction
import com.postpole.wheelfile.BuildConfig
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.domain.vehicle.VehicleLimit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** What a restore put back. [archivedForCap] counts vehicles restored as archived because the free cap is 2 active. */
data class RestoreSummary(
    val vehicles: Int,
    val entries: Int,
    val attachments: Int,
    val archivedForCap: Int,
    val missingAttachmentFiles: Int,
)

/**
 * `.wheelfile` backup and restore (spec §2.8): a ZIP with `manifest.json`, `data.json` (every table) and
 * `attachments/<file>`. Free on every tier. Restore replaces the current data and is never capped: more than two
 * active vehicles on the free tier are all restored, the extras archived (spec §2.7).
 */
class BackupService(
    private val db: AppDatabase,
    private val attachmentsDir: File,
    private val isPro: () -> Boolean,
    private val clock: () -> Long,
    private val appVersion: String,
) {
    @Inject
    constructor(@ApplicationContext context: Context, db: AppDatabase, billing: BillingRepository) : this(
        db = db,
        attachmentsDir = File(context.filesDir, AttachmentStore.DIR_NAME),
        isPro = { billing.isPro.value },
        clock = System::currentTimeMillis,
        appVersion = BuildConfig.VERSION_NAME,
    )

    suspend fun write(out: OutputStream): BackupManifest = withContext(Dispatchers.IO) {
        val data = db.withTransaction { db.backupDao().snapshot() }
        val present = data.attachments.filter { fileFor(it.fileName).isFile }
        val manifest = BackupManifest(
            BackupCodec.FORMAT, BackupCodec.FORMAT_VERSION, AppDatabase.VERSION, appVersion, clock(),
            data.vehicles.size, present.size,
        )
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(manifest.toJson().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(DATA))
        zip.write(BackupCodec.encode(data).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        for (a in present) {
            zip.putNextEntry(ZipEntry("$ATTACHMENTS_PREFIX${File(a.fileName).name}"))
            fileFor(a.fileName).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
        manifest
    }

    /** Throws [BackupException] (before anything is changed) when the file isn't a usable backup. */
    suspend fun restore(input: InputStream): RestoreSummary = withContext(Dispatchers.IO) {
        val staging = File(attachmentsDir.parentFile, "attachments_restore")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            var manifestJson: String? = null
            var dataJson: String? = null
            val staged = HashSet<String>()
            try {
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        when {
                            entry.isDirectory -> Unit
                            entry.name == MANIFEST -> manifestJson = String(zip.readBytes(), Charsets.UTF_8)
                            entry.name == DATA -> dataJson = String(zip.readBytes(), Charsets.UTF_8)
                            entry.name.startsWith(ATTACHMENTS_PREFIX) -> {
                                // Only the base name is used: an entry can never write outside the staging folder.
                                val name = File(entry.name).name
                                if (name.isNotEmpty()) {
                                    File(staging, name).outputStream().use { zip.copyTo(it) }
                                    staged.add(name)
                                }
                            }
                        }
                    }
                }
            } catch (e: java.util.zip.ZipException) {
                throw BackupException("This isn't a Wheelfile backup.")
            }
            val manifest = BackupManifest.parse(manifestJson ?: throw BackupException("This isn't a Wheelfile backup."))
            if (manifest.format != BackupCodec.FORMAT) throw BackupException("This isn't a Wheelfile backup.")
            if (manifest.formatVersion > BackupCodec.FORMAT_VERSION || manifest.schemaVersion > AppDatabase.VERSION) {
                throw BackupException("This backup was made by a newer version of the app. Update the app and try again.")
            }
            val data = BackupCodec.decode(dataJson ?: throw BackupException("The backup has no data."))

            val ids = data.vehicles.map { it.id }.toSet()
            val consistent = data.energyLogs.all { it.vehicleId in ids } && data.serviceRecords.all { it.vehicleId in ids } &&
                data.expenses.all { it.vehicleId in ids } && data.reminders.all { it.vehicleId in ids } &&
                data.documents.all { it.vehicleId in ids }
            if (!consistent) throw BackupException("The backup's data is inconsistent.")

            // The cap never blocks a restore: everything comes back, extra active vehicles are archived.
            var archivedForCap = 0
            var active = 0
            val vehicles = data.vehicles.sortedBy { it.id }.map { v ->
                if (v.archived) {
                    v
                } else if (VehicleLimit.canAddActive(active, isPro())) {
                    active++
                    v
                } else {
                    archivedForCap++
                    v.copy(archived = true)
                }
            }
            val attachments = data.attachments.filter { File(it.fileName).name in staged }
            val missing = data.attachments.size - attachments.size

            val dao = db.backupDao()
            db.withTransaction {
                dao.deleteVehicles()
                dao.deleteAttachments()
                dao.deleteCustomCategories()
                dao.insertVehicles(vehicles)
                dao.insertEnergyLogs(data.energyLogs)
                dao.insertServiceRecords(data.serviceRecords)
                dao.insertExpenses(data.expenses)
                dao.insertDocuments(data.documents)
                dao.insertReminders(data.reminders)
                dao.insertAttachments(attachments)
                dao.insertCustomCategories(data.customCategories)
            }

            // Database is in place; now swap the files.
            attachmentsDir.mkdirs()
            attachmentsDir.listFiles()?.forEach { it.delete() }
            // Only files that an attachment row points at come back; anything else in the ZIP is ignored.
            for (name in attachments.map { File(it.fileName).name }.toSet()) {
                val target = File(attachmentsDir, name)
                if (!File(staging, name).renameTo(target)) {
                    File(staging, name).copyTo(target, overwrite = true)
                }
            }
            RestoreSummary(
                vehicles = vehicles.size,
                entries = data.energyLogs.size + data.serviceRecords.size + data.expenses.size,
                attachments = attachments.size,
                archivedForCap = archivedForCap,
                missingAttachmentFiles = missing,
            )
        } catch (e: IOException) {
            throw BackupException("The backup file couldn't be read.")
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun fileFor(name: String) = File(attachmentsDir, File(name).name)

    companion object {
        const val MANIFEST = "manifest.json"
        const val DATA = "data.json"
        const val ATTACHMENTS_PREFIX = "attachments/"
    }
}
