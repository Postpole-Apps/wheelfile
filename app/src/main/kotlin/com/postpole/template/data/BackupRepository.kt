package com.postpole.template.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** The backup file is not valid, or was written by a newer app version. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Versioned JSON backup of all Room data, plus CSV export of items. Works on plain streams;
 * the UI supplies them from the Storage Access Framework, so no storage permission is needed.
 *
 * Format v1: `{"format":"postpole-backup","version":1,"exportedAt":<ms>,"items":[{"id","title","createdAt"}]}`.
 * Add new tables as new top-level arrays and bump [FORMAT_VERSION]; keep reading older versions.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: AppDatabase,
) {
    /** Writes the backup and returns the number of items exported. */
    suspend fun exportJson(out: OutputStream, now: Long = System.currentTimeMillis()): Int {
        val items = db.itemDao().getAll()
        val json = JSONObject()
            .put("format", FORMAT_NAME)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", now)
            .put(
                "items",
                JSONArray().apply {
                    items.forEach {
                        put(
                            JSONObject()
                                .put("id", it.id)
                                .put("title", it.title)
                                .put("createdAt", it.createdAt),
                        )
                    }
                },
            )
        out.write(json.toString(2).toByteArray(Charsets.UTF_8))
        out.flush()
        return items.size
    }

    /**
     * Replaces all data with the backup's contents, atomically. The file is fully parsed and
     * validated first, so an invalid file leaves existing data untouched. Returns the item count.
     */
    suspend fun importJson(input: InputStream): Int {
        val items = parse(input.readBytes().toString(Charsets.UTF_8))
        db.withTransaction {
            val dao = db.itemDao()
            dao.deleteAll()
            dao.insertAll(items)
        }
        return items.size
    }

    /** Writes Items as RFC 4180 CSV (header `id,title,createdAt`) and returns the row count. */
    suspend fun exportItemsCsv(out: OutputStream): Int {
        val items = db.itemDao().getAll()
        val csv = buildString {
            append("id,title,createdAt\r\n")
            items.forEach { append(it.id).append(',').append(csvCell(it.title)).append(',').append(it.createdAt).append("\r\n") }
        }
        out.write(csv.toByteArray(Charsets.UTF_8))
        out.flush()
        return items.size
    }

    internal fun parse(text: String): List<Item> {
        try {
            val root = JSONObject(text)
            if (root.optString("format") != FORMAT_NAME) throw InvalidBackupException("Not a backup file")
            val version = root.getInt("version")
            if (version < 1 || version > FORMAT_VERSION) {
                throw InvalidBackupException("Unsupported backup version $version")
            }
            val array = root.getJSONArray("items")
            val seen = HashSet<Long>()
            return List(array.length()) { i ->
                val o = array.getJSONObject(i)
                val id = o.getLong("id")
                if (!seen.add(id)) throw InvalidBackupException("Duplicate item id $id")
                Item(id = id, title = o.getString("title"), createdAt = o.getLong("createdAt"))
            }
        } catch (e: JSONException) {
            throw InvalidBackupException("Malformed backup file", e)
        }
    }

    private fun csvCell(value: String): String {
        // Neutralise spreadsheet formula injection (=, +, -, @, tab, CR) before quoting.
        val safe = if (value.isNotEmpty() && value[0] in FORMULA_PREFIXES) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    companion object {
        const val FORMAT_NAME = "postpole-backup"
        const val FORMAT_VERSION = 1
        private val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@', '\t', '\r')
    }
}
