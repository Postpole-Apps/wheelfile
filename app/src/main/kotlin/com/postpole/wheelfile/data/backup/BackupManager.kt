package com.postpole.wheelfile.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.postpole.wheelfile.BuildConfig
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.ChargerType
import com.postpole.wheelfile.data.db.CustomCategory
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Recurrence
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.vehicle.VehicleLimit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RestoreOutcome {
    /** [archivedForCap] vehicles were archived because the free tier holds 2 active vehicles; nothing was dropped. */
    data class Restored(val vehicles: Int, val archivedForCap: Int) : RestoreOutcome

    /** Not a Wheelfile backup, damaged, or written by a newer app version. */
    data object Invalid : RestoreOutcome
}

/**
 * `.wheelfile` backup (spec §2.8): a ZIP with `manifest.json` (schema version, app version, created at),
 * `data.json` (every table) and `attachments/<file>`. Restore replaces the whole database and the attachment folder,
 * is never limited by the free caps (extra active vehicles are archived, never dropped), and is all-or-nothing.
 */
@Singleton
class BackupManager(
    private val db: AppDatabase,
    private val attachmentsDir: File,
    private val tempDir: File,
    private val isPro: () -> Boolean,
    private val clock: () -> Long,
    private val appVersion: String,
) {
    @Inject
    constructor(@ApplicationContext context: Context, db: AppDatabase, billing: BillingRepository) : this(
        db = db,
        attachmentsDir = File(context.filesDir, AttachmentStore.DIR_NAME),
        tempDir = context.cacheDir,
        isPro = { billing.isPro.value },
        clock = System::currentTimeMillis,
        appVersion = BuildConfig.VERSION_NAME,
    )

    /** Canonical `data.json` text: rows ordered by id, so the same data always gives the same text and hash. */
    suspend fun dumpData(): String = db.withTransaction { toJson(readAll()).toString() }

    /** SHA-256 (hex) of [dumpData]. The round-trip test compares it before the wipe and after the restore. */
    suspend fun databaseHash(): String = sha256(dumpData().toByteArray(Charsets.UTF_8))

    suspend fun backup(out: OutputStream) = withContext(Dispatchers.IO) {
        val data = db.withTransaction { readAll() }
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(
            JSONObject()
                .put("format", FORMAT)
                .put("schemaVersion", SCHEMA_VERSION)
                .put("appVersion", appVersion)
                .put("createdAt", clock())
                .put("vehicles", data.vehicles.size)
                .put("attachments", data.attachments.size)
                .toString().toByteArray(Charsets.UTF_8),
        )
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(DATA))
        zip.write(toJson(data).toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        for (a in data.attachments) {
            val file = File(attachmentsDir, File(a.fileName).name)
            if (!file.isFile) continue
            zip.putNextEntry(ZipEntry("$ATTACHMENTS_PREFIX${file.name}"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
        out.flush()
    }

    suspend fun restore(input: InputStream): RestoreOutcome = withContext(Dispatchers.IO) {
        val staging = File(tempDir, "restore-${clock()}").apply { mkdirs() }
        try {
            var manifest: JSONObject? = null
            var dataText: String? = null
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    when {
                        entry.name == MANIFEST -> manifest = JSONObject(String(zip.readBytes(), Charsets.UTF_8))
                        entry.name == DATA -> dataText = String(zip.readBytes(), Charsets.UTF_8)
                        entry.name.startsWith(ATTACHMENTS_PREFIX) -> {
                            // Only the bare file name is used: a crafted path can never leave the staging folder.
                            val name = File(entry.name.removePrefix(ATTACHMENTS_PREFIX)).name
                            if (name.isNotEmpty() && name != "." && name != "..") {
                                File(staging, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                }
            }
            val m = manifest ?: return@withContext RestoreOutcome.Invalid
            val text = dataText ?: return@withContext RestoreOutcome.Invalid
            if (m.optString("format") != FORMAT || m.optInt("schemaVersion", Int.MAX_VALUE) > SCHEMA_VERSION) {
                return@withContext RestoreOutcome.Invalid
            }
            val data = fromJson(JSONObject(text))

            // Never capped: extra active vehicles are archived so the free tier's rule still holds.
            var archivedForCap = 0
            val vehicles = if (isPro()) {
                data.vehicles
            } else {
                var active = 0
                data.vehicles.sortedBy { it.id }.map { v ->
                    when {
                        v.archived -> v
                        VehicleLimit.canAddActive(active, isPro = false) -> v.also { active++ }
                        else -> v.copy(archived = true).also { archivedForCap++ }
                    }
                }
            }

            db.withTransaction {
                db.vehicleDao().deleteAll() // cascades to every child table
                db.attachmentDao().deleteAll()
                db.customCategoryDao().deleteAll()
                vehicles.forEach { db.vehicleDao().insert(it) }
                data.energyLogs.forEach { db.energyLogDao().insert(it) }
                data.services.forEach { db.serviceRecordDao().insert(it) }
                data.expenses.forEach { db.expenseDao().insert(it) }
                data.documents.forEach { db.vehicleDocumentDao().insert(it) }
                data.reminders.forEach { db.reminderDao().insert(it) }
                data.attachments.forEach { db.attachmentDao().insert(it) }
                data.categories.forEach { db.customCategoryDao().insert(it) }
            }

            // The database now matches the backup; swap the attachment files to match it too.
            attachmentsDir.mkdirs()
            attachmentsDir.listFiles()?.forEach { it.delete() }
            staging.listFiles()?.forEach { it.copyTo(File(attachmentsDir, it.name), overwrite = true) }
            RestoreOutcome.Restored(vehicles.size, archivedForCap)
        } catch (_: JSONException) {
            RestoreOutcome.Invalid
        } catch (_: IOException) {
            RestoreOutcome.Invalid
        } finally {
            staging.deleteRecursively()
        }
    }

    // ---- reading and JSON --------------------------------------------------------------------------------------

    private class Data(
        val vehicles: List<Vehicle>,
        val energyLogs: List<EnergyLog>,
        val services: List<ServiceRecord>,
        val expenses: List<Expense>,
        val reminders: List<Reminder>,
        val documents: List<VehicleDocument>,
        val attachments: List<Attachment>,
        val categories: List<CustomCategory>,
    )

    private suspend fun readAll(): Data {
        val vehicles = db.vehicleDao().getAll().sortedBy { it.id }
        val ids = vehicles.map { it.id }
        return Data(
            vehicles = vehicles,
            energyLogs = ids.flatMap { db.energyLogDao().getAll(it) }.sortedBy { it.id },
            services = ids.flatMap { db.serviceRecordDao().getForVehicle(it) }.sortedBy { it.id },
            expenses = ids.flatMap { db.expenseDao().getForVehicle(it) }.sortedBy { it.id },
            reminders = db.reminderDao().getAll().sortedBy { it.id },
            documents = db.vehicleDocumentDao().getAll().sortedBy { it.id },
            attachments = db.attachmentDao().getAll().sortedBy { it.id },
            categories = db.customCategoryDao().getAll().sortedBy { it.id },
        )
    }

    private fun toJson(d: Data): JSONObject = JSONObject()
        .put("vehicles", JSONArray(d.vehicles.map(::vehicleJson)))
        .put("energyLogs", JSONArray(d.energyLogs.map(::energyJson)))
        .put("services", JSONArray(d.services.map(::serviceJson)))
        .put("expenses", JSONArray(d.expenses.map(::expenseJson)))
        .put("reminders", JSONArray(d.reminders.map(::reminderJson)))
        .put("documents", JSONArray(d.documents.map(::documentJson)))
        .put("attachments", JSONArray(d.attachments.map(::attachmentJson)))
        .put("categories", JSONArray(d.categories.map(::categoryJson)))

    private fun fromJson(o: JSONObject) = Data(
        vehicles = o.list("vehicles", ::vehicleFrom),
        energyLogs = o.list("energyLogs", ::energyFrom),
        services = o.list("services", ::serviceFrom),
        expenses = o.list("expenses", ::expenseFrom),
        reminders = o.list("reminders", ::reminderFrom),
        documents = o.list("documents", ::documentFrom),
        attachments = o.list("attachments", ::attachmentFrom),
        categories = o.list("categories", ::categoryFrom),
    )

    private fun <T> JSONObject.list(key: String, map: (JSONObject) -> T): List<T> {
        val arr = optJSONArray(key) ?: return emptyList()
        return List(arr.length()) { map(arr.getJSONObject(it)) }
    }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.lng(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun JSONObject.int(k: String): Int? = if (isNull(k)) null else getInt(k)
    private fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else getDouble(k)
    private fun JSONObject.put0(k: String, v: Any?): JSONObject = put(k, v ?: JSONObject.NULL)

    private fun vehicleJson(v: Vehicle) = JSONObject().put("id", v.id).put("name", v.name).put("type", v.type.name)
        .put("powertrain", v.powertrain.name).put0("make", v.make).put0("model", v.model).put0("year", v.year)
        .put0("plate", v.plate).put0("vin", v.vin).put("distanceUnit", v.distanceUnit.name)
        .put("volumeUnit", v.volumeUnit.name).put("economyDisplay", v.economyDisplay.name).put("currency", v.currency)
        .put0("tankCapacity", v.tankCapacity).put0("batteryKwh", v.batteryKwh).put0("photoPath", v.photoPath)
        .put("archived", v.archived).put0("soldAt", v.soldAt).put("createdAt", v.createdAt).put("updatedAt", v.updatedAt)

    private fun vehicleFrom(o: JSONObject) = Vehicle(
        id = o.getLong("id"), name = o.getString("name"), type = VehicleType.valueOf(o.getString("type")),
        powertrain = Powertrain.valueOf(o.getString("powertrain")), make = o.str("make"), model = o.str("model"),
        year = o.int("year"), plate = o.str("plate"), vin = o.str("vin"),
        distanceUnit = DistanceUnit.valueOf(o.getString("distanceUnit")), volumeUnit = VolumeUnit.valueOf(o.getString("volumeUnit")),
        economyDisplay = EconomyUnit.valueOf(o.getString("economyDisplay")), currency = o.getString("currency"),
        tankCapacity = o.dbl("tankCapacity"), batteryKwh = o.dbl("batteryKwh"), photoPath = o.str("photoPath"),
        archived = o.getBoolean("archived"), soldAt = o.lng("soldAt"), createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
    )

    private fun energyJson(e: EnergyLog) = JSONObject().put("id", e.id).put("vehicleId", e.vehicleId).put("stream", e.stream.name)
        .put("dateTime", e.dateTime).put("odometer", e.odometer).put("quantity", e.quantity)
        .put0("pricePerUnitMinor", e.pricePerUnitMinor).put0("totalMinor", e.totalMinor).put("isFull", e.isFull)
        .put("missedPrevious", e.missedPrevious).put0("fuelGrade", e.fuelGrade).put0("chargerType", e.chargerType?.name)
        .put0("socStart", e.socStart).put0("socEnd", e.socEnd).put0("station", e.station).put0("note", e.note)
        .put0("importSource", e.importSource).put0("importKey", e.importKey)
        .put("createdAt", e.createdAt).put("updatedAt", e.updatedAt)

    private fun energyFrom(o: JSONObject) = EnergyLog(
        id = o.getLong("id"), vehicleId = o.getLong("vehicleId"), stream = EnergyStream.valueOf(o.getString("stream")),
        dateTime = o.getLong("dateTime"), odometer = o.getDouble("odometer"), quantity = o.getDouble("quantity"),
        pricePerUnitMinor = o.lng("pricePerUnitMinor"), totalMinor = o.lng("totalMinor"), isFull = o.getBoolean("isFull"),
        missedPrevious = o.getBoolean("missedPrevious"), fuelGrade = o.str("fuelGrade"),
        chargerType = o.str("chargerType")?.let(ChargerType::valueOf), socStart = o.int("socStart"), socEnd = o.int("socEnd"),
        station = o.str("station"), note = o.str("note"), importSource = o.str("importSource"), importKey = o.str("importKey"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
    )

    private fun serviceJson(s: ServiceRecord) = JSONObject().put("id", s.id).put("vehicleId", s.vehicleId)
        .put("dateTime", s.dateTime).put0("odometer", s.odometer).put("items", JSONArray(s.items)).put0("totalMinor", s.totalMinor)
        .put0("workshop", s.workshop).put0("note", s.note).put0("importSource", s.importSource).put0("importKey", s.importKey)
        .put("createdAt", s.createdAt).put("updatedAt", s.updatedAt)

    private fun serviceFrom(o: JSONObject): ServiceRecord {
        val items = o.getJSONArray("items")
        return ServiceRecord(
            id = o.getLong("id"), vehicleId = o.getLong("vehicleId"), dateTime = o.getLong("dateTime"),
            odometer = o.dbl("odometer"), items = List(items.length()) { items.getString(it) }, totalMinor = o.lng("totalMinor"),
            workshop = o.str("workshop"), note = o.str("note"), importSource = o.str("importSource"), importKey = o.str("importKey"),
            createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
        )
    }

    private fun expenseJson(e: Expense) = JSONObject().put("id", e.id).put("vehicleId", e.vehicleId).put("dateTime", e.dateTime)
        .put0("odometer", e.odometer).put("category", e.category.name).put0("customCategoryId", e.customCategoryId)
        .put("amountMinor", e.amountMinor).put0("recurring", e.recurring?.name).put0("note", e.note)
        .put0("importSource", e.importSource).put0("importKey", e.importKey)
        .put("createdAt", e.createdAt).put("updatedAt", e.updatedAt)

    private fun expenseFrom(o: JSONObject) = Expense(
        id = o.getLong("id"), vehicleId = o.getLong("vehicleId"), dateTime = o.getLong("dateTime"), odometer = o.dbl("odometer"),
        category = ExpenseCategory.valueOf(o.getString("category")), customCategoryId = o.lng("customCategoryId"),
        amountMinor = o.getLong("amountMinor"), recurring = o.str("recurring")?.let(Recurrence::valueOf), note = o.str("note"),
        importSource = o.str("importSource"), importKey = o.str("importKey"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
    )

    private fun reminderJson(r: Reminder) = JSONObject().put("id", r.id).put("vehicleId", r.vehicleId).put("title", r.title)
        .put("kind", r.kind.name).put0("dueDate", r.dueDate).put0("dueOdometer", r.dueOdometer)
        .put0("repeatMonths", r.repeatMonths).put0("repeatDistance", r.repeatDistance).put("leadDays", r.leadDays)
        .put0("leadDistance", r.leadDistance).put0("presetCode", r.presetCode).put0("linkedDocumentId", r.linkedDocumentId)
        .put("done", r.done).put0("lastNotifiedAt", r.lastNotifiedAt)

    private fun reminderFrom(o: JSONObject) = Reminder(
        id = o.getLong("id"), vehicleId = o.getLong("vehicleId"), title = o.getString("title"),
        kind = ReminderKind.valueOf(o.getString("kind")), dueDate = o.lng("dueDate"), dueOdometer = o.dbl("dueOdometer"),
        repeatMonths = o.int("repeatMonths"), repeatDistance = o.dbl("repeatDistance"), leadDays = o.getInt("leadDays"),
        leadDistance = o.dbl("leadDistance"), presetCode = o.str("presetCode"), linkedDocumentId = o.lng("linkedDocumentId"),
        done = o.getBoolean("done"), lastNotifiedAt = o.lng("lastNotifiedAt"),
    )

    private fun documentJson(d: VehicleDocument) = JSONObject().put("id", d.id).put("vehicleId", d.vehicleId)
        .put("type", d.type.name).put("title", d.title).put0("number", d.number).put0("issueDate", d.issueDate)
        .put0("expiryDate", d.expiryDate).put("alertDays", JSONArray(d.alertDays)).put0("note", d.note)

    private fun documentFrom(o: JSONObject): VehicleDocument {
        val days = o.getJSONArray("alertDays")
        return VehicleDocument(
            id = o.getLong("id"), vehicleId = o.getLong("vehicleId"), type = DocumentType.valueOf(o.getString("type")),
            title = o.getString("title"), number = o.str("number"), issueDate = o.lng("issueDate"), expiryDate = o.lng("expiryDate"),
            alertDays = List(days.length()) { days.getInt(it) }, note = o.str("note"),
        )
    }

    private fun attachmentJson(a: Attachment) = JSONObject().put("id", a.id).put("ownerType", a.ownerType.name)
        .put("ownerId", a.ownerId).put("fileName", a.fileName).put("mimeType", a.mimeType).put("sizeBytes", a.sizeBytes)
        .put("createdAt", a.createdAt)

    private fun attachmentFrom(o: JSONObject) = Attachment(
        id = o.getLong("id"), ownerType = OwnerType.valueOf(o.getString("ownerType")), ownerId = o.getLong("ownerId"),
        fileName = File(o.getString("fileName")).name, mimeType = o.getString("mimeType"), sizeBytes = o.getLong("sizeBytes"),
        createdAt = o.getLong("createdAt"),
    )

    private fun categoryJson(c: CustomCategory) = JSONObject().put("id", c.id).put("name", c.name).put("kind", c.kind)
    private fun categoryFrom(o: JSONObject) = CustomCategory(o.getLong("id"), o.getString("name"), o.getString("kind"))

    companion object {
        const val FORMAT = "wheelfile-backup"
        const val SCHEMA_VERSION = 1
        const val EXTENSION = "wheelfile"
        private const val MANIFEST = "manifest.json"
        private const val DATA = "data.json"
        private const val ATTACHMENTS_PREFIX = "attachments/"

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
