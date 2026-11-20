package com.postpole.wheelfile.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.ChargerType
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.entry.EntryForm
import com.postpole.wheelfile.domain.entry.FieldError
import com.postpole.wheelfile.domain.entry.FormField
import com.postpole.wheelfile.domain.entry.PriceField
import com.postpole.wheelfile.domain.entry.PriceMath
import com.postpole.wheelfile.ui.common.ChoiceChips
import com.postpole.wheelfile.ui.common.SectionLabel
import com.postpole.wheelfile.ui.common.SwitchRow
import com.postpole.wheelfile.ui.common.WfTextField
import com.postpole.wheelfile.ui.common.formatMoney
import com.postpole.wheelfile.ui.common.formatNumber
import com.postpole.wheelfile.ui.common.label
import com.postpole.wheelfile.ui.common.plainNumber
import com.postpole.wheelfile.ui.theme.LocalWheelfileType
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EntryActions(
    val onClose: () -> Unit,
    val onDate: (Long) -> Unit,
    val onOdometer: (String) -> Unit,
    val onQuantity: (String) -> Unit,
    val onUnitPrice: (String) -> Unit,
    val onTotal: (String) -> Unit,
    val onFull: (Boolean) -> Unit,
    val onMissed: (Boolean) -> Unit,
    val onGrade: (String) -> Unit,
    val onStation: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onCharger: (ChargerType?) -> Unit,
    val onSocStart: (String) -> Unit,
    val onSocEnd: (String) -> Unit,
    val onSave: () -> Unit,
)

@Composable
fun EntryFormRoute(onClose: () -> Unit, viewModel: EntryFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.form_save_failed)
    val retry = stringResource(R.string.retry)
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            when (it) {
                EntryEvent.Saved -> onClose()
                EntryEvent.SaveFailed -> {
                    // st-error-save: "Couldn't save" with Retry, shown above the Save bar.
                    val result = snackbar.showSnackbar(failed, retry, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.save()
                }
            }
        }
    }
    EntryFormScreen(
        state = state,
        snackbar = snackbar,
        actions = viewModel.toActions(onClose),
    )
}

fun EntryFormViewModel.toActions(onClose: () -> Unit) = EntryActions(
    onClose, ::setDate, ::setOdometer, ::setQuantity, ::setUnitPrice, ::setTotal, ::setFull, ::setMissed,
    ::setFuelGrade, ::setStation, ::setNote, ::setCharger, ::setSocStart, ::setSocEnd, ::save,
)

/** s06-add-fillup and s06b-add-charge. No ads. The Save bar is pinned and the form scrolls above it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFormScreen(state: EntryUiState, snackbar: SnackbarHostState, actions: EntryActions) {
    val vehicle = state.vehicle
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(if (state.isCharge) R.string.form_charge_title else R.string.form_fillup_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (vehicle != null) {
                            Text(vehicle.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = actions.onSave,
                    enabled = vehicle != null && !vehicle.archived && !state.saving,
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = Sizes.screenMargin, vertical = Spacing.m)
                        .fillMaxWidth().heightIn(min = Sizes.saveButtonHeight),
                    shape = PillShape,
                ) {
                    Text(
                        stringResource(if (state.saveAnyway) R.string.save_anyway else R.string.save),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
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
            TotalHero(state, vehicle)
            FormFields(state, vehicle, actions)
        }
    }
}

@Composable
private fun TotalHero(state: EntryUiState, vehicle: Vehicle) {
    val price = state.validation?.price
    val digits = EntryForm.fractionDigits(vehicle.currency)
    val ev = state.isCharge
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (ev) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (ev) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.form_total), style = MaterialTheme.typography.labelLarge)
            val total = price?.total
            if (total != null) {
                Text(formatMoney(PriceMath.toMinor(total, digits), vehicle.currency), style = LocalWheelfileType.current.numberHero)
            } else {
                Text(stringResource(R.string.form_total_empty), style = MaterialTheme.typography.titleMedium)
            }
            val auto = when (price?.derived) {
                PriceField.TOTAL -> R.string.form_auto_total
                PriceField.UNIT_PRICE -> R.string.form_auto_price
                PriceField.QUANTITY -> R.string.form_auto_quantity
                null -> null
            }
            if (auto != null) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                    Text(
                        stringResource(auto),
                        Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormFields(state: EntryUiState, vehicle: Vehicle, actions: EntryActions) {
    val form = state.form
    val validation = state.validation
    val price = validation?.price
    val energy = EntryForm.quantityUnitIsEnergy(vehicle, state.isCharge)
    val distanceUnit = stringResource(vehicle.distanceUnit.label())
    val volumeUnit = if (energy) stringResource(R.string.unit_kwh) else stringResource(vehicle.volumeUnit.label())

    // Field errors only appear after the first Save attempt.
    val errors = if (state.showErrors) validation?.errors.orEmpty() else emptyMap()
    val required = stringResource(R.string.form_error_required)
    val invalid = stringResource(R.string.form_error_invalid)
    val tooLarge = stringResource(R.string.form_error_too_large)
    val socInvalid = stringResource(R.string.form_error_soc)
    fun errorText(field: FormField): String? = when (errors[field]) {
        FieldError.REQUIRED -> required
        FieldError.INVALID -> if (field == FormField.SOC_START || field == FormField.SOC_END) socInvalid else invalid
        FieldError.TOO_LARGE -> tooLarge
        null -> null
    }

    // Date
    var pickDate by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val dateText = Instant.ofEpochMilli(form.dateTime).atZone(zone).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    Surface(
        onClick = { pickDate = true },
        shape = com.postpole.wheelfile.ui.theme.FieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.fieldMinHeight),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = Spacing.s), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.form_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dateText, style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (pickDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = Instant.ofEpochMilli(form.dateTime).atZone(zone).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        // Keep the time of day; swap only the calendar date.
                        val picked = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = Instant.ofEpochMilli(form.dateTime).atZone(zone).toLocalTime()
                        actions.onDate(picked.atTime(time).atZone(zone).toInstant().toEpochMilli())
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState) }
    }

    // Odometer with the last reading as a hint and the soft warning (never blocks).
    val warning = validation?.warning
    val warningText = warning?.let {
        stringResource(
            if (it.tooLow) R.string.form_warning_low else R.string.form_warning_high,
            formatNumber(it.reference, 0),
            distanceUnit,
        )
    }
    WfTextField(
        label = stringResource(R.string.form_odometer, distanceUnit),
        value = form.odometer,
        onValueChange = actions.onOdometer,
        keyboardType = KeyboardType.Decimal,
        error = errorText(FormField.ODOMETER),
        warning = warningText,
        supporting = state.lastOdometer?.let { stringResource(R.string.form_odometer_hint, formatNumber(it, 0), distanceUnit) },
    )

    // Any two of volume / price / total: the third is derived and shown read-back in its field.
    fun shown(field: PriceField, typed: String, value: java.math.BigDecimal?): String =
        if (price?.derived == field && value != null) plainNumber(value) else typed
    val calculated = stringResource(R.string.form_derived_suffix)
    WfTextField(
        label = if (energy) stringResource(R.string.form_energy) else stringResource(R.string.form_volume, volumeUnit),
        value = shown(PriceField.QUANTITY, form.quantity, price?.quantity),
        onValueChange = actions.onQuantity,
        keyboardType = KeyboardType.Decimal,
        error = errorText(FormField.QUANTITY),
        supporting = if (price?.derived == PriceField.QUANTITY) calculated else null,
    )
    WfTextField(
        label = stringResource(R.string.form_unit_price, volumeUnit),
        value = shown(PriceField.UNIT_PRICE, form.unitPrice, price?.unitPrice),
        onValueChange = actions.onUnitPrice,
        keyboardType = KeyboardType.Decimal,
        error = errorText(FormField.UNIT_PRICE),
        supporting = if (price?.derived == PriceField.UNIT_PRICE) calculated else null,
        suffix = vehicle.currency,
    )
    WfTextField(
        label = stringResource(R.string.form_total_field, vehicle.currency),
        value = shown(PriceField.TOTAL, form.total, price?.total),
        onValueChange = actions.onTotal,
        keyboardType = KeyboardType.Decimal,
        error = errorText(FormField.TOTAL),
        supporting = if (price?.derived == PriceField.TOTAL) calculated else null,
    )

    SwitchRow(
        title = stringResource(if (state.isCharge) R.string.form_full_charge else R.string.form_full_tank),
        subtitle = stringResource(if (state.isCharge) R.string.form_full_charge_sub else R.string.form_full_tank_sub),
        checked = form.isFull,
        onCheckedChange = actions.onFull,
    )
    SwitchRow(
        title = stringResource(R.string.form_missed),
        subtitle = stringResource(R.string.form_missed_sub),
        checked = form.missedPrevious,
        onCheckedChange = actions.onMissed,
    )

    if (state.isCharge) {
        SectionLabel(stringResource(R.string.form_charger))
        ChoiceChips(
            options = ChargerType.entries,
            selected = form.charger,
            label = { stringResource(it.label()) },
            onSelect = { actions.onCharger(if (form.charger == it) null else it) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            WfTextField(
                label = stringResource(R.string.form_soc_start),
                value = form.socStart,
                onValueChange = actions.onSocStart,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                error = errorText(FormField.SOC_START),
                suffix = stringResource(R.string.form_soc_suffix),
            )
            WfTextField(
                label = stringResource(R.string.form_soc_end),
                value = form.socEnd,
                onValueChange = actions.onSocEnd,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                error = errorText(FormField.SOC_END),
                suffix = stringResource(R.string.form_soc_suffix),
            )
        }
    } else {
        WfTextField(stringResource(R.string.form_grade), form.fuelGrade, actions.onGrade)
    }
    WfTextField(stringResource(R.string.form_station), form.station, actions.onStation)
    WfTextField(stringResource(R.string.form_note), form.note, actions.onNote, singleLine = false)
    Spacer(Modifier.height(Spacing.s))
}
