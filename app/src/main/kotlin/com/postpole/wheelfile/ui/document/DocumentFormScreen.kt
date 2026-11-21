package com.postpole.wheelfile.ui.document

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.domain.document.DocumentField
import com.postpole.wheelfile.domain.document.DocumentForm
import com.postpole.wheelfile.ui.common.ChoiceChips
import com.postpole.wheelfile.ui.common.DateField
import com.postpole.wheelfile.ui.common.SectionLabel
import com.postpole.wheelfile.ui.common.WfTextField
import com.postpole.wheelfile.ui.entry.AttachmentsSection
import com.postpole.wheelfile.ui.entry.NotificationPromptSheet
import com.postpole.wheelfile.ui.entry.needsNotificationPermission
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing

/** The document form (add and edit). 0 ads, like every form. */
@Composable
fun DocumentFormRoute(onClose: () -> Unit, onOpenPro: () -> Unit, viewModel: DocumentFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var promptNotifications by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.form_save_failed)
    val retry = stringResource(R.string.retry)
    val attachFailed = stringResource(R.string.attachment_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            when (it) {
                is DocumentFormEvent.Saved -> if (it.promptNotifications && needsNotificationPermission(context)) promptNotifications = true else onClose()
                DocumentFormEvent.SaveFailed -> {
                    val result = snackbar.showSnackbar(failed, retry, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.save()
                }
                DocumentFormEvent.AttachmentFailed -> snackbar.showSnackbar(attachFailed)
                DocumentFormEvent.OpenProGate -> onOpenPro()
            }
        }
    }
    DocumentFormScreen(state, snackbar, viewModel, onClose)
    if (promptNotifications) NotificationPromptSheet(onFinished = onClose)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentFormScreen(state: DocumentFormUiState, snackbar: SnackbarHostState, vm: DocumentFormViewModel, onClose: () -> Unit) {
    val vehicle = state.vehicle
    val form = state.form
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(if (state.isEditing) R.string.doc_form_edit_title else R.string.doc_form_add_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (vehicle != null) {
                            Text(vehicle.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = vm::save,
                    enabled = vehicle != null && !vehicle.archived && !state.saving,
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = Sizes.screenMargin, vertical = Spacing.m)
                        .fillMaxWidth().heightIn(min = Sizes.saveButtonHeight),
                    shape = PillShape,
                ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge) }
            }
        },
    ) { padding ->
        if (vehicle == null) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = Sizes.screenMargin, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (vehicle.archived) Text(stringResource(R.string.form_read_only), style = MaterialTheme.typography.bodyMedium)
            SectionLabel(stringResource(R.string.doc_field_type))
            ChoiceChips(DocumentType.entries, form.type, { stringResource(it.label()) }, vm::setType)
            val titleError = state.validation.errors[DocumentField.TITLE]
            WfTextField(
                label = stringResource(R.string.doc_field_title),
                value = form.title,
                onValueChange = vm::setTitle,
                error = if (state.showErrors && titleError != null) stringResource(R.string.doc_field_title_required) else null,
            )
            WfTextField(stringResource(R.string.doc_field_number), form.number, vm::setNumber)
            DateField(stringResource(R.string.doc_field_issue), form.issueDate, vm::setIssueDate)
            val expiryError = state.validation.errors[DocumentField.EXPIRY]
            DateField(
                label = stringResource(R.string.doc_field_expiry),
                epochMillis = form.expiryDate,
                onPick = vm::setExpiryDate,
                error = if (expiryError != null) stringResource(R.string.doc_field_expiry_invalid) else null,
            )
            if (form.expiryDate != null) {
                TextButton(onClick = vm::clearExpiry, Modifier.heightIn(min = Sizes.touchTargetMin)) {
                    Text(stringResource(R.string.doc_expiry_clear))
                }
                SectionLabel(stringResource(R.string.doc_alert_days))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    DocumentForm.ALERT_CHOICES.forEach { day ->
                        FilterChip(
                            selected = day in form.alertDays,
                            onClick = { vm.toggleAlertDay(day) },
                            label = { Text(pluralStringResource(R.plurals.reminder_lead_days, day, day)) },
                            modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
                        )
                    }
                }
                Text(
                    stringResource(R.string.doc_alert_days_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WfTextField(
                label = stringResource(R.string.doc_field_note),
                value = form.note,
                onValueChange = vm::setNote,
                singleLine = false,
            )
            state.saved.forEach { a ->
                Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetMin), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (a.mimeType == "application/pdf") R.string.attachment_pdf else R.string.attachment_photo),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    IconButton(onClick = { vm.removeSaved(a) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.attachment_remove))
                    }
                }
            }
            AttachmentsSection(
                attachments = state.staged,
                capReached = state.attachmentCapReached,
                onSource = vm::addAttachment,
                onRemove = vm::removeStaged,
                onCapReached = vm::openProGate,
                onFailure = vm::reportAttachmentFailure,
            )
        }
    }
}
