package com.postpole.wheelfile.ui.entry

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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.Recurrence
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.entry.FieldError
import com.postpole.wheelfile.domain.entry.RecordField
import com.postpole.wheelfile.domain.entry.RecordForm
import com.postpole.wheelfile.ui.common.ChoiceChips
import com.postpole.wheelfile.ui.common.DateField
import com.postpole.wheelfile.ui.common.SectionLabel
import com.postpole.wheelfile.ui.common.SwitchRow
import com.postpole.wheelfile.ui.common.WfTextField
import com.postpole.wheelfile.ui.common.label
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing

@androidx.annotation.StringRes
fun ExpenseCategory.label(): Int = when (this) {
    ExpenseCategory.INSURANCE -> R.string.expense_insurance
    ExpenseCategory.TAX_REGISTRATION -> R.string.expense_tax
    ExpenseCategory.PARKING -> R.string.expense_parking
    ExpenseCategory.TOLLS -> R.string.expense_tolls
    ExpenseCategory.WASHING -> R.string.expense_washing
    ExpenseCategory.FINES -> R.string.expense_fines
    ExpenseCategory.LOAN_LEASE -> R.string.expense_loan_lease
    ExpenseCategory.ACCESSORIES -> R.string.expense_accessories
    ExpenseCategory.OTHER -> R.string.expense_other
}

/** s07-add-service and the Expense form. 0 ads. Saving never starts the pump-session rule. */
@Composable
fun RecordFormRoute(onClose: () -> Unit, onOpenPro: () -> Unit, viewModel: RecordFormViewModel = hiltViewModel()) {
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
                is RecordEvent.Saved -> if (it.promptNotifications && needsNotificationPermission(context)) promptNotifications = true else onClose()
                RecordEvent.SaveFailed -> {
                    val result = snackbar.showSnackbar(failed, retry, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.save()
                }
                RecordEvent.AttachmentFailed -> snackbar.showSnackbar(attachFailed)
                RecordEvent.OpenProGate -> onOpenPro()
            }
        }
    }
    RecordFormScreen(state, snackbar, viewModel, onClose)
    if (promptNotifications) NotificationPromptSheet(onFinished = onClose)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFormScreen(state: RecordUiState, snackbar: SnackbarHostState, vm: RecordFormViewModel, onClose: () -> Unit) {
    val vehicle = state.vehicle
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(if (state.isService) R.string.form_service_title else R.string.form_expense_title),
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
            RecordFields(state, vehicle, vm)
            Spacer(Modifier.height(Spacing.s))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordFields(state: RecordUiState, vehicle: Vehicle, vm: RecordFormViewModel) {
    val form = state.form
    val errors = if (state.showErrors) state.validation.errors else emptyMap()
    val required = stringResource(R.string.form_error_required)
    val invalid = stringResource(R.string.form_error_invalid)
    val tooLarge = stringResource(R.string.form_error_too_large)
    fun errorText(field: RecordField): String? = when (errors[field]) {
        FieldError.REQUIRED -> required
        FieldError.INVALID -> invalid
        FieldError.TOO_LARGE -> tooLarge
        null -> null
    }
    val distanceUnit = stringResource(vehicle.distanceUnit.label())

    DateField(stringResource(R.string.form_date), form.dateTime, vm::setDate)

    if (state.isService) {
        SectionLabel(stringResource(R.string.service_items))
        val presets = serviceItemPresets()
        // Presets first, then any custom items the user added.
        val options = presets + form.items.filter { it !in presets }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            options.forEach { item ->
                val selected = item in form.items
                FilterChip(
                    selected = selected,
                    onClick = { vm.toggleItem(item) },
                    label = { Text(item) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(10.dp),
                )
            }
        }
        errorText(RecordField.ITEMS)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            WfTextField(
                label = stringResource(R.string.service_custom_item),
                value = form.customItem,
                onValueChange = vm::setCustomItem,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = vm::addCustomItem, enabled = form.customItem.isNotBlank(), modifier = Modifier.heightIn(min = Sizes.touchTargetMin)) {
                Text(stringResource(R.string.service_add_custom))
            }
        }
    } else {
        SectionLabel(stringResource(R.string.expense_category))
        ChoiceChips(
            options = RecordForm.EXPENSE_CATEGORIES,
            selected = form.category,
            label = { stringResource(it.label()) },
            onSelect = vm::setCategory,
        )
        errorText(RecordField.CATEGORY)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }

    WfTextField(
        label = stringResource(if (state.isService) R.string.service_cost else R.string.expense_amount, vehicle.currency),
        value = form.cost,
        onValueChange = vm::setCost,
        keyboardType = KeyboardType.Decimal,
        error = errorText(RecordField.COST),
    )
    WfTextField(
        label = stringResource(R.string.record_odometer_optional, distanceUnit),
        value = form.odometer,
        onValueChange = vm::setOdometer,
        keyboardType = KeyboardType.Decimal,
        error = errorText(RecordField.ODOMETER),
    )
    if (state.isService) {
        WfTextField(stringResource(R.string.service_workshop), form.workshop, vm::setWorkshop)
    } else {
        SectionLabel(stringResource(R.string.expense_recurring))
        ChoiceChips(
            options = listOf<Recurrence?>(null, Recurrence.MONTHLY, Recurrence.YEARLY),
            selected = form.recurring,
            label = {
                stringResource(
                    when (it) {
                        null -> R.string.expense_recurring_none
                        Recurrence.MONTHLY -> R.string.expense_recurring_monthly
                        Recurrence.YEARLY -> R.string.expense_recurring_yearly
                    },
                )
            },
            onSelect = vm::setRecurring,
        )
    }
    WfTextField(stringResource(R.string.form_note), form.note, vm::setNote, singleLine = false)

    AttachmentsSection(
        attachments = state.attachments,
        capReached = state.attachmentCapReached,
        onSource = vm::addAttachment,
        onRemove = vm::removeAttachment,
        onCapReached = vm::openProGate,
        onFailure = vm::reportAttachmentFailure,
    )

    if (state.isService) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = Sizes.cardPadding, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SwitchRow(
                    title = stringResource(R.string.service_next_reminder),
                    subtitle = stringResource(R.string.service_next_reminder_sub),
                    checked = form.createReminder,
                    onCheckedChange = vm::setCreateReminder,
                )
                if (form.createReminder) {
                    WfTextField(
                        label = stringResource(R.string.service_reminder_months),
                        value = form.reminderMonths,
                        onValueChange = vm::setReminderMonths,
                        keyboardType = KeyboardType.Number,
                        error = errorText(RecordField.REMINDER_MONTHS),
                    )
                    WfTextField(
                        label = stringResource(R.string.service_reminder_distance, distanceUnit),
                        value = form.reminderDistance,
                        onValueChange = vm::setReminderDistance,
                        keyboardType = KeyboardType.Decimal,
                        error = errorText(RecordField.REMINDER_DISTANCE),
                        supporting = stringResource(R.string.service_reminder_first),
                    )
                }
            }
        }
    }
}

@Composable
private fun serviceItemPresets(): List<String> = listOf(
    stringResource(R.string.item_oil_filter),
    stringResource(R.string.item_tyres),
    stringResource(R.string.item_brakes),
    stringResource(R.string.item_battery),
    stringResource(R.string.item_inspection),
    stringResource(R.string.item_air_filter),
    stringResource(R.string.item_coolant),
    stringResource(R.string.item_wipers),
)
