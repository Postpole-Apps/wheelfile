package com.postpole.wheelfile.ui.document

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.DocumentRepository
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.domain.document.DocumentExpiry
import com.postpole.wheelfile.domain.document.ExpiryStatus
import com.postpole.wheelfile.ui.common.formatDate
import com.postpole.wheelfile.ui.entry.fileProviderAuthority
import com.postpole.wheelfile.ui.theme.LocalWheelfileColors
import com.postpole.wheelfile.ui.theme.LocalWheelfileType
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import javax.inject.Inject

data class DocumentDetailState(
    val loaded: Boolean = false,
    /** Null once loaded means the document no longer exists. */
    val document: VehicleDocument? = null,
    val attachments: List<Attachment> = emptyList(),
)

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val documents: DocumentRepository,
    val attachmentStore: AttachmentStore,
) : ViewModel() {
    private val documentId: Long = checkNotNull(savedState.get<Long>("documentId"))

    val state: StateFlow<DocumentDetailState> =
        combine(documents.observe(documentId), documents.observeAttachments(documentId)) { doc, files ->
            DocumentDetailState(loaded = true, document = doc, attachments = files)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentDetailState())

    fun delete(onDone: () -> Unit) {
        val doc = state.value.document ?: return
        viewModelScope.launch {
            documents.delete(doc)
            onDone()
        }
    }
}

@Composable
fun DocumentDetailRoute(
    onBack: () -> Unit,
    onEdit: (vehicleId: Long, documentId: Long) -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DocumentDetailScreen(
        state = state,
        now = System.currentTimeMillis(),
        fileFor = { viewModel.attachmentStore.fileFor(it.fileName) },
        onBack = onBack,
        onEdit = { state.document?.let { onEdit(it.vehicleId, it.id) } },
        onDelete = { viewModel.delete(onBack) },
    )
}

/**
 * s12-document-detail: expiry card, attachment preview with Open, detail rows. It never carries an ad: it is on the
 * attachment-viewer path (DECISIONS.md item 3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    state: DocumentDetailState,
    now: Long,
    fileFor: (Attachment) -> File,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    val doc = state.document
    Scaffold(
        modifier = Modifier.testTag(DOCUMENT_DETAIL_TAG),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.document_detail_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    if (doc != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.document_edit)) }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.document_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        if (doc == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(Spacing.xl), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.document_not_found), style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Sizes.screenMargin, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(doc.title, style = MaterialTheme.typography.headlineSmall)
            ExpiryCard(doc, now)
            AttachmentPreview(state.attachments, fileFor, snackbar)
            DetailRows(doc)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.document_delete_title)) },
            text = { Text(stringResource(R.string.document_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.document_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ExpiryCard(doc: VehicleDocument, now: Long) {
    val colors = LocalWheelfileColors.current
    val zone = ZoneId.systemDefault()
    val status = DocumentExpiry.status(doc.expiryDate, doc.alertDays, now, zone)
    val days = DocumentExpiry.daysLeft(doc.expiryDate, now, zone)
    val container = when (status) {
        ExpiryStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer
        ExpiryStatus.EXPIRING -> colors.warningContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val content = when (status) {
        ExpiryStatus.EXPIRED -> MaterialTheme.colorScheme.onErrorContainer
        ExpiryStatus.EXPIRING -> colors.onWarningContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(shape = RoundedCornerShape(20.dp), color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (doc.expiryDate == null || days == null) {
                Text(stringResource(R.string.document_expiry_card_none), style = MaterialTheme.typography.titleMedium)
            } else {
                val date = formatDate(doc.expiryDate, zone)
                Text(
                    stringResource(if (days < 0) R.string.document_expiry_card_expired else R.string.document_expiry_card_valid, date),
                    style = MaterialTheme.typography.titleMedium,
                )
                val remaining = if (days < 0) {
                    pluralStringResource(R.plurals.document_expired_days_ago, (-days).toInt(), (-days).toInt())
                } else {
                    pluralStringResource(R.plurals.document_expiry_in_days, days.toInt(), days.toInt())
                }
                Text(remaining, style = LocalWheelfileType.current.numberMedium)
            }
        }
    }
}

@Composable
private fun AttachmentPreview(attachments: List<Attachment>, fileFor: (Attachment) -> File, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val first = attachments.firstOrNull()
    var failed by remember { mutableStateOf(false) }
    val failedText = stringResource(R.string.document_open_failed)
    LaunchedEffect(failed) {
        if (failed) {
            snackbar.showSnackbar(failedText)
            failed = false
        }
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        if (first == null) {
            Text(
                stringResource(R.string.document_no_attachment),
                Modifier.padding(Sizes.cardPadding),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Surface
        }
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val file = fileFor(first)
            Box(
                Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap by produceState<Bitmap?>(null, file, first.mimeType) {
                    value = if (first.mimeType.startsWith("image/")) withContext(Dispatchers.IO) { decodeThumbnail(file) } else null
                }
                val image = bitmap
                if (image != null) {
                    Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(
                        stringResource(if (first.mimeType == "application/pdf") R.string.attachment_pdf else R.string.attachment_photo),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.document_attachments, attachments.size, attachments.size),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { if (!openAttachment(context, file, first.mimeType)) failed = true },
                    modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
                ) { Text(stringResource(R.string.document_open)) }
            }
        }
    }
}

@Composable
private fun DetailRows(doc: VehicleDocument) {
    val zone = ZoneId.systemDefault()
    val rows = buildList {
        add(stringResource(R.string.document_row_type) to stringResource(doc.type.label()))
        doc.number?.let { add(stringResource(R.string.document_row_number) to it) }
        doc.issueDate?.let { add(stringResource(R.string.document_row_issued) to formatDate(it, zone)) }
        doc.expiryDate?.let { add(stringResource(R.string.document_row_expires) to formatDate(it, zone)) }
        add(
            stringResource(R.string.document_row_alerts) to
                if (doc.expiryDate == null || doc.alertDays.isEmpty()) {
                    stringResource(R.string.document_alerts_none)
                } else {
                    doc.alertDays.sortedDescending().map { pluralStringResource(R.plurals.reminder_lead_days, it, it) }.joinToString(", ")
                },
        )
        doc.note?.let { add(stringResource(R.string.document_row_note) to it) }
    }
    Column {
        rows.forEach { (label, value) ->
            Column(Modifier.fillMaxWidth().heightIn(min = Sizes.listRowMin).padding(vertical = Spacing.xs), verticalArrangement = Arrangement.Center) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Hands the private file to the system viewer through the FileProvider. False when no app can open it. */
private fun openAttachment(context: Context, file: File, mime: String): Boolean {
    if (!file.exists()) return false
    return try {
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}

/** A small preview: decoded with a sample size so a large photo never fills memory. */
private fun decodeThumbnail(file: File, targetPx: Int = 480): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}
