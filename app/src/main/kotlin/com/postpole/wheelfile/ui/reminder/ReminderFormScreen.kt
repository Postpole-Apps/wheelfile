package com.postpole.wheelfile.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.domain.entry.FieldError
import com.postpole.wheelfile.domain.reminder.ReminderField
import com.postpole.wheelfile.domain.reminder.ReminderForm
import com.postpole.wheelfile.ui.common.ChoiceChips
import com.postpole.wheelfile.ui.common.DateField
import com.postpole.wheelfile.ui.common.SectionLabel
import com.postpole.wheelfile.ui.common.WfTextField
import com.postpole.wheelfile.ui.common.formatDate
import com.postpole.wheelfile.ui.common.formatNumber
import com.postpole.wheelfile.ui.common.label
import com.postpole.wheelfile.ui.entry.NotificationPromptSheet
import com.postpole.wheelfile.ui.entry.needsNotificationPermission
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing

/** s10-reminder-form and st-notification-prompt. 0 ads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderFormRoute(onClose: () -> Unit, viewModel: ReminderFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var prompt by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.form_save_failed)
    val retry = stringResource(R.string.retry)
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            when (it) {
                is ReminderFormEvent.Saved -> if (it.promptNotifications && needsNotificationPermission(context)) prompt = true else onClose()
                ReminderFormEvent.SaveFailed -> {
                    val result = snackbar.showSnackbar(failed, retry, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.save()
                }
            }
        }
    }
    val vehicle = state.vehicle
    val form = state.form
    val errors = if (state.showErrors) state.validation.errors else emptyMap()
    val required = stringResource(R.string.form_error_required)
    val invalid = stringResource(R.string.form_error_invalid)
    val tooLarge = stringResource(R.string.form_error_too_large)
    fun err(f: ReminderField): String? = when (errors[f]) {
        FieldError.REQUIRED -> required
        FieldError.INVALID -> invalid
        FieldError.TOO_LARGE -> tooLarge
        null -> null
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.reminder_form_title), style = MaterialTheme.typography.titleLarge)
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
                    onClick = viewModel::save,
                    enabled = vehicle != null && !vehicle.archived && !state.saving,
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = Sizes.screenMargin, vertical = Spacing.m)
                        .fillMaxWidth().heightIn(min = Sizes.saveButtonHeight),
                    shape = PillShape,
                ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge) }
            }
        },
    ) { padding ->
        if (vehicle == null) return@Scaffold
        val unit = stringResource(vehicle.distanceUnit.label())
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = Sizes.screenMargin, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            WfTextField(stringResource(R.string.reminder_title_field), form.title, viewModel::setTitle, error = err(ReminderField.TITLE))

            SectionLabel(stringResource(R.string.reminder_trigger))
            ChoiceChips(
                options = ReminderKind.entries,
                selected = form.kind,
                label = {
                    stringResource(
                        when (it) {
                            ReminderKind.DATE -> R.string.reminder_trigger_date
                            ReminderKind.ODOMETER -> R.string.reminder_trigger_odometer
                            ReminderKind.FIRST_OF -> R.string.reminder_trigger_first
                        },
                    )
                },
                onSelect = viewModel::setKind,
            )

            if (ReminderForm.usesDate(form.kind)) {
                DateField(
                    label = stringResource(R.string.reminder_due_date),
                    epochMillis = form.dueDate,
                    onPick = viewModel::setDueDate,
                    error = err(ReminderField.DUE_DATE),
                )
            }
            if (ReminderForm.usesOdometer(form.kind)) {
                val current = state.usage.currentOdometer
                WfTextField(
                    label = stringResource(R.string.reminder_due_odometer, unit),
                    value = form.dueOdometer,
                    onValueChange = viewModel::setDueOdometer,
                    keyboardType = KeyboardType.Decimal,
                    error = err(ReminderField.DUE_ODOMETER),
                    supporting = current?.let { stringResource(R.string.form_odometer_hint, formatNumber(it, 0), unit) },
                )
                // The projection is only an estimate, never a promise.
                state.estimatedDate?.let {
                    Text(
                        stringResource(R.string.reminder_estimated_around, formatDate(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.estimatedDate == null && form.dueOdometer.isNotBlank()) {
                    Text(
                        stringResource(R.string.reminder_estimate_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.reminder_estimate_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel(stringResource(R.string.reminder_repeat))
            if (ReminderForm.usesDate(form.kind)) {
                WfTextField(
                    label = stringResource(R.string.reminder_repeat_months),
                    value = form.repeatMonths,
                    onValueChange = viewModel::setRepeatMonths,
                    keyboardType = KeyboardType.Number,
                    error = err(ReminderField.REPEAT_MONTHS),
                    supporting = stringResource(R.string.reminder_repeat_blank),
                )
            }
            if (ReminderForm.usesOdometer(form.kind)) {
                WfTextField(
                    label = stringResource(R.string.reminder_repeat_distance, unit),
                    value = form.repeatDistance,
                    onValueChange = viewModel::setRepeatDistance,
                    keyboardType = KeyboardType.Decimal,
                    error = err(ReminderField.REPEAT_DISTANCE),
                    supporting = stringResource(R.string.reminder_repeat_blank),
                )
            }

            if (ReminderForm.usesDate(form.kind)) {
                SectionLabel(stringResource(R.string.reminder_alert_lead))
                ChoiceChips(
                    options = ReminderForm.LEAD_DAY_CHOICES,
                    selected = form.leadDays,
                    label = { if (it == 0) stringResource(R.string.reminder_lead_on_day) else pluralStringResource(R.plurals.reminder_lead_days, it, it) },
                    onSelect = viewModel::setLeadDays,
                )
            }
        }
    }
    if (prompt) NotificationPromptSheet(onFinished = onClose)
}
