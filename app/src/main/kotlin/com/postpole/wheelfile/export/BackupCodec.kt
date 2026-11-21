package com.postpole.wheelfile.export

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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupException(message: String) : Exception(message)

/** Manifest of a `.wheelfile` backup (spec §2.8): what wrote it and when. */
data class BackupManifest(
    val format: String,
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val vehicleCount: Int,
    val attachmentCount: Int,
) {
    fun toJson(): String = JSONObject()
        .put("format", format).put("formatVersion", formatVersion).put("schemaVersion", schemaVersion)
        .put("appVersion", appVersion).put("createdAt", createdAt)
        .put("vehicles", vehicleCount).put("attachments", attachmentCount)
        .toString(2)

    companion object {
        fun parse(json: String): BackupManifest = try {
            val o = JSONObject(json)
            BackupManifest(
                o.getString("format"), o.getInt("formatVersion"), o.getInt("schemaVersion"),
                o.optString("appVersion", ""), o.getLong("createdAt"), o.optInt("vehicles"), o.optInt("attachments"),
            )
        } catch (e: JSONException) {
            throw BackupException("This isn't a Wheelfile backup.")
        }
    }
}

/** JSON form of the database (`data.json` inside the backup). Field names are the entity property names. */
object BackupCodec {
    const val FORMAT = "wheelfile-backup"
    const val FORMAT_VERSION = 1

    fun encode(d: DataSnapshot): String = JSONObject()
        .put("vehicles", array(d.vehicles) { it.toJson() })
        .put("energyLogs", array(d.energyLogs) { it.toJson() })
        .put("serviceRecords", array(d.serviceRecords) { it.toJson() })
        .put("expenses", array(d.expenses) { it.toJson() })
        .put("reminders", array(d.reminders) { it.toJson() })
        .put("documents", array(d.documents) { it.toJson() })
        .put("attachments", array(d.attachments) { it.toJson() })
        .put("customCategories", array(d.customCategories) { it.toJson() })
        .toString()

    fun decode(json: String): DataSnapshot = try {
        val o = JSONObject(json)
        DataSnapshot(
            list(o, "vehicles", ::vehicle), list(o, "energyLogs", ::energy), list(o, "serviceRecords", ::service),
            list(o, "expenses", ::expense), list(o, "reminders", ::reminder), list(o, "documents", ::document),
            list(o, "attachments", ::attachment), list(o, "customCategories", ::category),
        )
    } catch (e: JSONException) {
        throw BackupException("The backup's data couldn't be read.")
    } catch (e: IllegalArgumentException) {
        throw BackupException("The backup's data couldn't be read.")
    }

    private fun <T> array(rows: List<T>, f: (T) -> JSONObject) = JSONArray().also { a -> rows.forEach { a.put(f(it)) } }

    private fun <T> list(o: JSONObject, key: String, f: (JSONObject) -> T): List<T> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return List(a.length()) { f(a.getJSONObject(it)) }
    }

    private fun JSONObject.long(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.int(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.double(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.string(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.bool(k: String): Boolean? = if (has(k) && !isNull(k)) getBoolean(k) else null
    private inline fun <reified E : Enum<E>> JSONObject.enum(k: String): E = enumValueOf<E>(getString(k))
    private inline fun <reified E : Enum<E>> JSONObject.enumOrNull(k: String): E? = string(k)?.let { enumValueOf<E>(it) }

    private fun JSONObject.nullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)

    private fun Vehicle.toJson() = JSONObject().put("id", id).put("name", name).put("type", type.name)
        .put("powertrain", powertrain.name).nullable("make", make).nullable("model", model).nullable("year", year)
        .nullable("plate", plate).nullable("vin", vin).put("distanceUnit", distanceUnit.name)
        .put("volumeUnit", volumeUnit.name).put("economyDisplay", economyDisplay.name).put("currency", currency)
        .nullable("tankCapacity", tankCapacity).nullable("batteryKwh", batteryKwh).nullable("photoPath", photoPath)
        .put("archived", archived).nullable("soldAt", soldAt).put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun vehicle(o: JSONObject) = Vehicle(
        id = o.getLong("id"), name = o.getString("name"), type = o.enum<VehicleType>("type"),
        powertrain = o.enum<Powertrain>("powertrain"), make = o.string("make"), model = o.string("model"),
        year = o.int("year"), plate = o.string("plate"), vin = o.string("vin"),
        distanceUnit = o.enum<DistanceUnit>("distanceUnit"), volumeUnit = o.enum<VolumeUnit>("volumeUnit"),
        economyDisplay = o.enum<EconomyUnit>("economyDisplay"), currency = o.getString("currency"),
        tankCapacity = o.double("tankCapacity"), batteryKwh = o.double("batteryKwh"), photoPath = o.string("photoPath"),
        archived = o.bool("archived") ?: false, soldAt = o.long("soldAt"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
    )

    private fun EnergyLog.toJson() = JSONObject().put("id", id).put("vehicleId", vehicleId).put("stream", stream.name)
        .put("dateTime", dateTime).put("odometer", odometer).put("quantity", quantity)
        .nullable("pricePerUnitMinor", pricePerUnitMinor).nullable("totalMinor", totalMinor)
        .put("isFull", isFull).put("missedPrevious", missedPrevious).nullable("fuelGrade", fuelGrade)
        .nullable("chargerType", chargerType?.name).nullable("socStart", socStart).nullable("socEnd", socEnd)
        .nullable("station", station).nullable("note", note).nullable("importSource", importSource)
        .nullable("importKey", importKey).put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun energy(o: JSONObject) = EnergyLog(
        id = o.getLong("id"), vehicleId = o.getLong("vehicleId"), stream = o.enum<EnergyStream>("stream"),
        dateTime = o.getLong("dateTime"), odometer = o.getDouble("odometer"), quantity = o.getDouble("quantity"),
        pricePerUnitMinor = o.long("pricePerUnitMinor"), totalMinor = o.long("totalMinor"),
        isFull = o.getBoolean("isFull"), missedPrevious = o.bool("missedPrevious") ?: false,
        fuelGrade = o.string("fuelGrade"), chargerType = o.enumOrNull<ChargerType>("chargerType"),
        socStart = o.int("socStart"), socEnd = o.int("socEnd"), station = o.string("station"), note = o.string("note"),
        importSource = o.string("importSource"), importKey = o.string("importKey"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
    )

    private fun ServiceRecord.toJson() = JSONObject().put("id", id).put("vehicleId", vehicleId).put("dateTime", dateTime)
        .nullable("odometer", odometer).put("items", JSONArray(items)).nullable("totalMinor", totalMinor)
        .nullable("workshop", workshop).nullable("note", note).nullable("importSource", importSource)
        .nullable("importKey", importKey).put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun service(o: JSONObject) = ServiceRecord(
        id = o.getLong("id"), vehicleId = o.getLong("vehicleId"), dateTime = o.getLong("dateTime"),
        odometer = o.double("odometer"),
        items = o.optJSONArray("items")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
        totalMinor = o.long("totalMinor"), workshop = o.string("workshop"), note = o.string("note"),
        importSource = o.string("importSource"), importKey = o.string("importKey"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
    )

    private fun Expense.toJson() = JSONObject().put("id", id).put("vehicleId", vehicleId).put("dateTime", dateTime)
        .nullable("odometer", odometer).put("category", category.name).nullable("customCategoryId", customCategoryId)
        .put("amountMinor", amountMinor).nullable("recurring", recurring?.name).nullable("note", note)
        .nullable("importSource", importSource).nullable("importKey", importKey)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun expense(o: JSONObject) = Expense(
        id = o.getLong("id"), vehicleId = o.getLong("vehicleId"), dateTime = o.getLong("dateTime"),
        odometer = o.double("odometer"), category = o.enum<ExpenseCategory>("category"),
        customCategoryId = o.long("customCategoryId"), amountMinor = o.getLong("amountMinor"),
        recurring = o.enumOrNull<Recurrence>("recurring"), note = o.string("note"),
        importSource = o.string("importSource"), importKey = o.string("importKey"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
    )

    private fun Reminder.toJson() = JSONObject().put("id", id).put("vehicleId", vehicleId).put("title", title)
        .put("kind", kind.name).nullable("dueDate", dueDate).nullable("dueOdometer", dueOdometer)
        .nullable("repeatMonths", repeatMonths).nullable("repeatDistance", repeatDistance).put("leadDays", leadDays)
        .nullable("leadDistance", leadDistance).nullable("presetCode", presetCode)
        .nullable("linkedDocumentId", linkedDocumentId).put("done", done).nullable("lastNotifiedAt", lastNotifiedAt)

    private fun reminder(o: JSONObject) = Reminder(
        id = o.getLong("id"), vehicleId = o.getLong("vehicleId"), title = o.getString("title"),
        kind = o.enum<ReminderKind>("kind"), dueDate = o.long("dueDate"), dueOdometer = o.double("dueOdometer"),
        repeatMonths = o.int("repeatMonths"), repeatDistance = o.double("repeatDistance"),
        leadDays = o.int("leadDays") ?: 0, leadDistance = o.double("leadDistance"), presetCode = o.string("presetCode"),
        linkedDocumentId = o.long("linkedDocumentId"), done = o.bool("done") ?: false,
        lastNotifiedAt = o.long("lastNotifiedAt"),
    )

    private fun VehicleDocument.toJson() = JSONObject().put("id", id).put("vehicleId", vehicleId).put("type", type.name)
        .put("title", title).nullable("number", number).nullable("issueDate", issueDate)
        .nullable("expiryDate", expiryDate).put("alertDays", JSONArray(alertDays)).nullable("note", note)

    private fun document(o: JSONObject) = VehicleDocument(
        id = o.getLong("id"), vehicleId = o.getLong("vehicleId"), type = o.enum<DocumentType>("type"),
        title = o.getString("title"), number = o.string("number"), issueDate = o.long("issueDate"),
        expiryDate = o.long("expiryDate"),
        alertDays = o.optJSONArray("alertDays")?.let { a -> List(a.length()) { a.getInt(it) } } ?: listOf(30, 14, 7, 1),
        note = o.string("note"),
    )

    private fun Attachment.toJson() = JSONObject().put("id", id).put("ownerType", ownerType.name).put("ownerId", ownerId)
        .put("fileName", fileName).put("mimeType", mimeType).put("sizeBytes", sizeBytes).put("createdAt", createdAt)

    private fun attachment(o: JSONObject) = Attachment(
        id = o.getLong("id"), ownerType = o.enum<OwnerType>("ownerType"), ownerId = o.getLong("ownerId"),
        fileName = o.getString("fileName"), mimeType = o.getString("mimeType"), sizeBytes = o.getLong("sizeBytes"),
        createdAt = o.getLong("createdAt"),
    )

    private fun CustomCategory.toJson() = JSONObject().put("id", id).put("name", name).put("kind", kind)

    private fun category(o: JSONObject) = CustomCategory(o.getLong("id"), o.getString("name"), o.getString("kind"))
}
