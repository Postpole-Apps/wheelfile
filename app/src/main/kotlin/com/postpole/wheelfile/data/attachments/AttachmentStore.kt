package com.postpole.wheelfile.data.attachments

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.data.db.OwnerType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

/** Free-tier attachment cap (spec §5.1): 30 in total for free and Remove ads. Pro is unlimited. */
object AttachmentLimit {
    const val FREE_ATTACHMENTS = 30

    /** True when one more attachment may be added. [count] includes entries and documents. */
    fun canAdd(count: Int, isPro: Boolean): Boolean = isPro || count < FREE_ATTACHMENTS
}

/** A file already copied into app-private storage that has no database row yet (the form is still open). */
data class StagedAttachment(val fileName: String, val mimeType: String, val sizeBytes: Long)

sealed interface StageResult {
    data class Staged(val attachment: StagedAttachment) : StageResult

    /** The 31st attachment on the free tier: show the Pro gate. Nothing was copied. */
    data object CapReached : StageResult

    data object Failed : StageResult
}

/** Opens the bytes behind a picked content or file URI. Null when the source can't be read. */
fun interface SourceOpener {
    fun open(source: String): InputStream?
}

/**
 * Copies picked photos and PDFs into app-private `files/attachments/` so they survive deletion of the source.
 * No storage permission is needed: the picker, camera and SAF hand us a readable URI.
 */
class AttachmentStore(
    private val dir: File,
    private val dao: AttachmentDao,
    private val opener: SourceOpener,
    private val mimeOf: (String) -> String?,
    private val isPro: () -> Boolean,
    private val clock: () -> Long,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        dao: AttachmentDao,
        billing: com.postpole.wheelfile.billing.BillingRepository,
    ) : this(
        dir = File(context.filesDir, DIR_NAME),
        dao = dao,
        opener = contentOpener(context.contentResolver),
        mimeOf = { context.contentResolver.getType(Uri.parse(it)) },
        isPro = { billing.isPro.value },
        clock = System::currentTimeMillis,
    )

    fun fileFor(fileName: String): File = File(dir, File(fileName).name)

    /**
     * Copies [source] into private storage. [pending] is how many staged files the open form already holds, because
     * they count towards the cap before they are saved. Returns [StageResult.CapReached] without copying at the cap.
     */
    suspend fun stage(source: String, pending: Int = 0): StageResult = withContext(Dispatchers.IO) {
        if (!AttachmentLimit.canAdd(dao.count() + pending, isPro())) return@withContext StageResult.CapReached
        try {
            dir.mkdirs()
            val mime = mimeOf(source) ?: DEFAULT_MIME
            val target = File(dir, "${UUID.randomUUID()}${extensionFor(mime)}")
            val input = opener.open(source) ?: return@withContext StageResult.Failed
            val size = try {
                input.use { src -> target.outputStream().use { out -> src.copyTo(out) } }
            } catch (e: IOException) {
                target.delete()
                return@withContext StageResult.Failed
            }
            StageResult.Staged(StagedAttachment(target.name, mime, size))
        } catch (e: IOException) {
            StageResult.Failed
        } catch (e: SecurityException) {
            StageResult.Failed
        }
    }

    /** Adds the database row for a staged file. Returns null when the cap was reached in the meantime. */
    suspend fun commit(staged: StagedAttachment, ownerType: OwnerType, ownerId: Long): Long? = withContext(Dispatchers.IO) {
        if (!AttachmentLimit.canAdd(dao.count(), isPro())) {
            fileFor(staged.fileName).delete()
            return@withContext null
        }
        dao.insert(
            Attachment(
                ownerType = ownerType, ownerId = ownerId, fileName = staged.fileName,
                mimeType = staged.mimeType, sizeBytes = staged.sizeBytes, createdAt = clock(),
            ),
        )
    }

    /** Stage then commit in one go, for owners that already exist. */
    suspend fun add(source: String, ownerType: OwnerType, ownerId: Long): StageResult {
        val result = stage(source)
        if (result is StageResult.Staged && commit(result.attachment, ownerType, ownerId) == null) return StageResult.CapReached
        return result
    }

    /** Throws away a staged file whose form was closed without saving. */
    suspend fun discard(staged: StagedAttachment) = withContext(Dispatchers.IO) {
        fileFor(staged.fileName).delete()
        Unit
    }

    /** Blocking delete of a staged file, for places that can't suspend (a view model being cleared). */
    fun discardNow(staged: StagedAttachment) {
        fileFor(staged.fileName).delete()
    }

    /** Removes the row and its private file. */
    suspend fun delete(attachment: Attachment) = withContext(Dispatchers.IO) {
        dao.delete(attachment)
        fileFor(attachment.fileName).delete()
        Unit
    }

    companion object {
        const val DIR_NAME = "attachments"
        private const val DEFAULT_MIME = "application/octet-stream"

        fun contentOpener(resolver: ContentResolver) = SourceOpener { source ->
            try {
                resolver.openInputStream(Uri.parse(source))
            } catch (e: java.io.FileNotFoundException) {
                null
            }
        }

        fun extensionFor(mime: String): String = when (mime) {
            "application/pdf" -> ".pdf"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { ".$it" } ?: ""
        }
    }
}
