package com.postpole.wheelfile.ui.document

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.domain.document.DocumentAlerts
import com.postpole.wheelfile.domain.document.DocumentError
import com.postpole.wheelfile.domain.document.DocumentField
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

@Composable
fun DocumentFormRoute(onClose: () -> Unit, onOpenPro: () -> Unit, viewModel: DocumentFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var promptNotifications by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.document_form_save_failed)
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

/** The document form. A form never carries an ad. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentFormScreen(state: DocumentFormUiState, snackbar: SnackbarHostState, vm: DocumentFormViewModel, onClose: () -> Unit) {
    val vehicle = state.vehicle
    val form = state.form
    val errors = if (state.showErrors) state.errors else emptyMap()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(if (state.editingId == null) R.string.document_form_add_title else R.string.document_form_edit_title),
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
        if (!state.loaded || vehicle == null) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = Sizes.screenMargin, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            SectionLabel(stringResource(R.string.document_form_type))
            val types = DocumentType.entries
            val typeNames = types.associateWith { stringResource(it.label()) }
            ChoiceChips(types, form.type, label = { typeNames.getValue(it) }, onSelect = { vm.setType(it, typeNames.getValue(it)) })

            WfTextField(
                label = stringResource(R.string.document_form_title),
                value = form.title,
                onValueChange = vm::setTitle,
                error = if (errors[DocumentField.TITLE] == DocumentError.REQUIRED) stringResource(R.string.document_form_title_required) else null,
            )
            WfTextField(label = stringResource(R.string.document_form_number), value = form.number, onValueChange = vm::setNumber)
            DateField(stringResource(R.string.document_form_issue), form.issueDate, vm::setIssue)
            if (form.issueDate != null) ClearButton { vm.setIssue(null) }
            DateField(
                stringResource(R.string.document_form_expiry),
                form.expiryDate,
                vm::setExpiry,
                error = if (errors[DocumentField.EXPIRY] == DocumentError.EXPIRY_BEFORE_ISSUE) stringResource(R.string.document_form_expiry_before_issue) else null,
            )
            if (form.expiryDate != null) ClearButton { vm.setExpiry(null) }

            SectionLabel(stringResource(R.string.document_form_alerts))
            val options = (DocumentAlerts.CHOICES + form.alertDays).distinct().sortedDescending()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                options.forEach { days ->
                    val selected = days in form.alertDays
                    FilterChip(
                        selected = selected,
                        enabled = form.expiryDate != null,
                        onClick = { vm.toggleAlert(days) },
                        label = { Text(pluralStringResource(R.plurals.reminder_lead_days, days, days)) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
            if (form.expiryDate == null) {
                Text(
                    stringResource(R.string.document_form_alerts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            WfTextField(label = stringResource(R.string.document_form_note), value = form.note, onValueChange = vm::setNote, singleLine = false)

            state.existing.forEach { a ->
                Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetMin), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (a.mimeType == "application/pdf") R.string.attachment_pdf else R.string.attachment_photo) +
                            " · " + stringResource(R.string.attachment_size_kb, (a.sizeBytes / 1024).coerceAtLeast(1)),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = { vm.removeExisting(a) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.attachment_remove))
                    }
                }
            }
            AttachmentsSection(
                attachments = state.attachments,
                capReached = state.attachmentCapReached,
                onSource = vm::addAttachment,
                onRemove = vm::removeStaged,
                onCapReached = vm::openProGate,
                onFailure = vm::reportAttachmentFailure,
            )
            Spacer(Modifier.height(Spacing.s))
        }
    }
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, Modifier.heightIn(min = Sizes.touchTargetMin)) { Text(stringResource(R.string.document_form_clear_date)) }
}
