package com.postpole.wheelfile.ui.document

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.domain.document.DocumentAlerts
import com.postpole.wheelfile.domain.document.ExpiryStatus
import com.postpole.wheelfile.ui.common.formatDate
import com.postpole.wheelfile.ui.entry.fileProviderAuthority
import com.postpole.wheelfile.ui.theme.LocalWheelfileColors
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * s12-document-detail. Never carries an ad: the screen has no ad slot, the same as the attachment viewer it opens.
 */
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
        onBack = onBack,
        onEdit = { state.document?.let { onEdit(it.vehicleId, it.id) } },
        onDelete = { viewModel.delete(onBack) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    state: DocumentDetailUiState,
    now: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    val failedText = stringResource(R.string.doc_attachment_open_failed)
    LaunchedEffect(openFailed) {
        if (openFailed) {
            snackbar.showSnackbar(failedText)
            openFailed = false
        }
    }
    val doc = state.document
    val readOnly = state.vehicle?.archived == true
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doc_detail_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (doc != null && !readOnly) {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.doc_edit)) }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.doc_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (doc == null) {
                if (state.loaded) {
                    Text(
                        stringResource(R.string.doc_detail_missing),
                        Modifier.padding(Sizes.screenMargin),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@Box
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Sizes.screenMargin, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(doc.title, style = MaterialTheme.typography.headlineSmall)
                ExpiryCard(doc, now)
                AttachmentPreview(state.attachments.firstOrNull()) { openAttachment(context, it) { openFailed = true } }
                DetailRows(doc)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.doc_delete_title)) },
            text = { Text(stringResource(R.string.doc_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }, Modifier.heightIn(min = Sizes.touchTargetMin)) {
                    Text(stringResource(R.string.doc_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ExpiryCard(doc: VehicleDocument, now: Long) {
    val colors = LocalWheelfileColors.current
    val status = DocumentAlerts.status(doc.expiryDate, doc.alertDays, now)
    val left = DocumentAlerts.daysLeft(doc.expiryDate, now)
    val (container, content) = when (status) {
        ExpiryStatus.EXPIRED -> colors.negativeContainer to colors.onNegativeContainer
        ExpiryStatus.EXPIRING -> colors.warningContainer to colors.onWarningContainer
        else -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurface
    }
    val expiry = doc.expiryDate
    val text = when {
        expiry == null || left == null -> stringResource(R.string.doc_expiry_card_none)
        left < 0 -> stringResource(R.string.doc_expiry_card_expired, formatDate(expiry))
        left == 0L -> stringResource(R.string.doc_expiry_card_today, formatDate(expiry))
        status == ExpiryStatus.EXPIRING -> pluralStringResource(R.plurals.doc_expiry_card_in_days, left.toInt(), left.toInt(), formatDate(expiry))
        else -> stringResource(R.string.doc_expiry_card_valid, formatDate(expiry))
    }
    Card(
        Modifier.fillMaxWidth().heightIn(min = 80.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(Sizes.cardPadding), contentAlignment = Alignment.CenterStart) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AttachmentPreview(attachment: Attachment?, onOpen: (Attachment) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (attachment == null) {
            Text(
                stringResource(R.string.doc_attachment_none),
                Modifier.padding(Sizes.cardPadding),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Card
        }
        val isPdf = attachment.mimeType == "application/pdf"
        val context = LocalContext.current
        val bitmap by produceState<Bitmap?>(null, attachment.fileName) {
            value = if (isPdf) null else withContext(Dispatchers.IO) { decodePreview(attachmentFile(context, attachment.fileName)) }
        }
        val preview = bitmap
        if (preview != null) {
            Image(
                preview.asImageBitmap(),
                contentDescription = stringResource(R.string.doc_attachment_photo),
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Sizes.cardPadding, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (isPdf) R.string.doc_attachment_pdf else R.string.doc_attachment_photo),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            Button(onClick = { onOpen(attachment) }, Modifier.heightIn(min = Sizes.touchTargetMin), shape = RoundedCornerShape(percent = 50)) {
                Text(stringResource(R.string.doc_attachment_open))
            }
        }
    }
}

@Composable
private fun DetailRows(doc: VehicleDocument) {
    val empty = stringResource(R.string.doc_row_empty)
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        DetailRow(stringResource(R.string.doc_row_type), stringResource(doc.type.label()))
        DetailRow(stringResource(R.string.doc_row_number), doc.number ?: empty)
        DetailRow(stringResource(R.string.doc_row_issue), doc.issueDate?.let(::formatDate) ?: empty)
        DetailRow(stringResource(R.string.doc_row_expiry), doc.expiryDate?.let(::formatDate) ?: empty)
        if (doc.expiryDate != null) {
            DetailRow(stringResource(R.string.doc_row_alerts), doc.alertDays.joinToString(", "))
        }
        DetailRow(stringResource(R.string.doc_row_notes), doc.note ?: empty)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = Sizes.listRowMin).padding(horizontal = Sizes.cardPadding, vertical = Spacing.s)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun attachmentFile(context: Context, fileName: String): File =
    File(File(context.filesDir, AttachmentStore.DIR_NAME), File(fileName).name)

/** Opens the private copy in whatever app the user has for it, through the FileProvider. No storage permission. */
private fun openAttachment(context: Context, attachment: Attachment, onFailure: () -> Unit) {
    val uri = try {
        FileProvider.getUriForFile(context, fileProviderAuthority(context), attachmentFile(context, attachment.fileName))
    } catch (_: IllegalArgumentException) {
        onFailure()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onFailure()
    }
}

/** A downsampled bitmap for the preview card, or null when the file can't be decoded. */
private fun decodePreview(file: File, targetWidth: Int = 720): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}
