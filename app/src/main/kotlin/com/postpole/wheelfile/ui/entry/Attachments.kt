package com.postpole.wheelfile.ui.entry

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.attachments.StagedAttachment
import com.postpole.wheelfile.ui.common.SectionLabel
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import java.io.File

/** FileProvider authority for camera captures (cache) and opening attachments. Needs no storage permission. */
fun fileProviderAuthority(context: Context) = "${context.packageName}.fileprovider"

/**
 * Receipt attachments: Photo Picker, camera through the TakePicture intent (the app holds no CAMERA permission) and PDFs
 * through the system document picker. [onSource] gets a URI string to copy into private storage. When the free cap is
 * reached the buttons call [onCapReached] (the Pro gate) instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttachmentsSection(
    attachments: List<StagedAttachment>,
    capReached: Boolean,
    onSource: (String) -> Unit,
    onRemove: (StagedAttachment) -> Unit,
    onCapReached: () -> Unit,
    onFailure: () -> Unit,
) {
    val context = LocalContext.current
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onSource(it.toString()) } }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { onSource(it.toString()) } }
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = cameraTarget
        if (ok && target != null) onSource(target)
        cameraTarget = null
    }

    SectionLabel(stringResource(R.string.attachments_title))
    attachments.forEach { a ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetMin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (a.mimeType == "application/pdf") R.string.attachment_pdf else R.string.attachment_photo),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.attachment_size_kb, (a.sizeBytes / 1024).coerceAtLeast(1)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onRemove(a) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.attachment_remove))
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedButton(
            onClick = { if (capReached) onCapReached() else photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
        ) { Text(stringResource(R.string.attachment_add_photo)) }
        OutlinedButton(
            onClick = {
                if (capReached) {
                    onCapReached()
                } else {
                    val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture-${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
                    cameraTarget = uri.toString()
                    try {
                        camera.launch(uri)
                    } catch (_: ActivityNotFoundException) {
                        cameraTarget = null
                        onFailure()
                    }
                }
            },
            modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
        ) { Text(stringResource(R.string.attachment_take_photo)) }
        OutlinedButton(
            onClick = { if (capReached) onCapReached() else pdf.launch(arrayOf("application/pdf")) },
            modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
        ) { Text(stringResource(R.string.attachment_add_pdf)) }
    }
}

/** True on Android 13+ when the notification permission is still missing. */
fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED

/**
 * st-notification-prompt: the rationale sheet shown when the first reminder is saved, then the system dialog.
 * [onFinished] runs when the user answers either way. If notifications are denied, reminders still show in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPromptSheet(onFinished: () -> Unit) {
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinished() }
    ModalBottomSheet(onDismissRequest = onFinished) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.notification_prompt_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.notification_prompt_body), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) request.launch(Manifest.permission.POST_NOTIFICATIONS) else onFinished()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.saveButtonHeight),
                shape = PillShape,
            ) { Text(stringResource(R.string.notification_prompt_allow)) }
            TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetMin)) {
                Text(stringResource(R.string.notification_prompt_later))
            }
        }
    }
}
