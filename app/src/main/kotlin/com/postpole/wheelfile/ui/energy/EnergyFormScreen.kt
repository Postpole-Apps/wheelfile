package com.postpole.wheelfile.ui.energy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.ChargerType
import com.postpole.wheelfile.domain.fillup.NumberText
import com.postpole.wheelfile.domain.fillup.PriceField
import com.postpole.wheelfile.domain.units.EconomyUnits
import com.postpole.wheelfile.ui.components.ChoiceChips
import com.postpole.wheelfile.ui.components.SaveBar
import com.postpole.wheelfile.ui.components.SwitchRow
import com.postpole.wheelfile.ui.components.TotalHero
import com.postpole.wheelfile.ui.components.WfTextField
import com.postpole.wheelfile.ui.format.Formatters
import com.postpole.wheelfile.ui.format.currentLocale
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing

/** Fill-up and Charge forms (`s06-add-fillup`, `s06b-add-charge`, `st-error-save`). No ads on forms or on Save. */
@Composable
fun EnergyFormScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EnergyFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it == EnergyFormEvent.Saved) onSaved() }
    }
    EnergyFormContent(
        state = state,
        onClose = onClose,
        actions = EnergyFormActions(
            onDate = viewModel::onDate, onOdometer = viewModel::onOdometer, onQuantity = viewModel::onQuantity,
            onPrice = viewModel::onPrice, onTotal = viewModel::onTotal, onFull = viewModel::onFull,
            onMissed = viewModel::onMissed, onFuelGrade = viewModel::onFuelGrade, onStation = viewModel::onStation,
            onNote = viewModel::onNote, onCharger = viewModel::onCharger, onSocStart = viewModel::onSocStart,
            onSocEnd = viewModel::onSocEnd, onSave = viewModel::save, onDismissError = viewModel::dismissSaveError,
        ),
    )
}

class EnergyFormActions(
    val onDate: (Long) -> Unit,
    val onOdometer: (String) -> Unit,
    val onQuantity: (String) -> Unit,
    val onPrice: (String) -> Unit,
    val onTotal: (String) -> Unit,
    val onFull: (Boolean) -> Unit,
    val onMissed: (Boolean) -> Unit,
    val onFuelGrade: (String) -> Unit,
    val onStation: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onCharger: (ChargerType?) -> Unit,
    val onSocStart: (String) -> Unit,
    val onSocEnd: (String) -> Unit,
    val onSave: () -> Unit,
    val onDismissError: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyFormContent(state: EnergyFormState, onClose: () -> Unit, actions: EnergyFormActions) {
    val locale = currentLocale()
    val vehicle = state.vehicle
    val snackbar = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.save_failed)
    val retryText = stringResource(R.string.retry)
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            val result = snackbar.showSnackbar(failedText, retryText, withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) actions.onSave() else actions.onDismissError()
        }
    }
    var pickingDate by remember { mutableStateOf(false) }
    val digits = vehicle?.let { com.postpole.wheelfile.domain.economy.VehicleSummary.fractionDigits(it.currency) } ?: 2
    val quantityUnit = vehicle?.let { EconomyUnits.quantityLabel(it, state.stream) } ?: ""
    val distanceUnit = vehicle?.let { EconomyUnits.distanceLabel(it.distanceUnit) } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isCharge) R.string.charge_title else R.string.fillup_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { SaveBar(stringResource(R.string.save), actions.onSave, enabled = !state.saving && !state.readOnly && vehicle != null) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Sizes.screenMargin, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (state.readOnly) Text(stringResource(R.string.form_read_only), color = MaterialTheme.colorScheme.error)
            val total = NumberText.parse(state.total)
            TotalHero(
                label = stringResource(R.string.form_total_hero),
                value = if (total != null && vehicle != null) Formatters.money(
                    com.postpole.wheelfile.domain.fillup.PriceMath.toMinor(total, digits), vehicle.currency, locale,
                ) else "—",
                chip = when (state.derived) {
                    PriceField.TOTAL -> stringResource(R.string.form_auto_total)
                    PriceField.PRICE -> stringResource(R.string.form_auto_price)
                    PriceField.VOLUME -> stringResource(R.string.form_auto_volume)
                    null -> null
                },
                electric = state.isCharge,
            )
            OutlinedButton(
                onClick = { pickingDate = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetMin),
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Text(
                    stringResource(R.string.form_date) + ": " + Formatters.date(state.dateTime, locale),
                    Modifier.padding(start = Spacing.s),
                )
            }
            val last = state.lastOdometer
            WfTextField(
                value = state.odometer,
                onValueChange = actions.onOdometer,
                modifier = Modifier.testTag("odometer"),
                label = stringResource(R.string.form_odometer, distanceUnit),
                decimal = true,
                isError = state.odometerError,
                isWarning = state.odometerWarning,
                supporting = when {
                    state.odometerError -> stringResource(R.string.form_odometer_error)
                    state.odometerWarning && last != null ->
                        stringResource(R.string.form_odometer_warning, Formatters.number(last, 1, locale), distanceUnit)
                    state.odometerWarning -> stringResource(R.string.form_odometer_warning_generic)
                    last != null -> stringResource(R.string.form_odometer_hint, Formatters.number(last, 1, locale), distanceUnit)
                    else -> null
                },
            )
            WfTextField(
                value = state.quantity,
                onValueChange = actions.onQuantity,
                modifier = Modifier.testTag("quantity"),
                label = stringResource(if (state.isCharge) R.string.form_energy else R.string.form_volume, quantityUnit),
                decimal = true,
                isError = state.quantityError,
                supporting = if (state.quantityError) stringResource(R.string.form_quantity_error) else stringResource(R.string.form_enter_two),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                WfTextField(
                    value = state.price,
                    onValueChange = actions.onPrice,
                    label = stringResource(R.string.form_price_per_unit, quantityUnit),
                    modifier = Modifier.weight(1f),
                    decimal = true,
                )
                WfTextField(
                    value = state.total,
                    onValueChange = actions.onTotal,
                    label = stringResource(R.string.form_total),
                    modifier = Modifier.weight(1f).testTag("total"),
                    decimal = true,
                )
            }
            SwitchRow(
                title = stringResource(if (state.isCharge) R.string.form_charged_full else R.string.form_full_tank),
                summary = stringResource(if (state.isCharge) R.string.form_charged_full_summary else R.string.form_full_tank_summary),
                checked = state.isFull,
                onCheckedChange = actions.onFull,
            )
            SwitchRow(
                title = stringResource(R.string.form_missed),
                summary = stringResource(R.string.form_missed_summary),
                checked = state.missedPrevious,
                onCheckedChange = actions.onMissed,
            )
            if (state.isCharge) {
                Text(stringResource(R.string.form_charger), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceChips(
                    options = ChargerType.entries,
                    selected = state.chargerType,
                    onSelect = { actions.onCharger(if (it == state.chargerType) null else it) },
                    label = {
                        stringResource(
                            when (it) {
                                ChargerType.HOME -> R.string.charger_home
                                ChargerType.AC_PUBLIC -> R.string.charger_ac
                                ChargerType.DC_FAST -> R.string.charger_dc
                            },
                        )
                    },
                )
                Text(stringResource(R.string.form_soc), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    WfTextField(
                        state.socStart, actions.onSocStart, stringResource(R.string.form_soc_start), Modifier.weight(1f),
                        number = true, suffix = "%", isError = state.socError,
                    )
                    WfTextField(
                        state.socEnd, actions.onSocEnd, stringResource(R.string.form_soc_end), Modifier.weight(1f),
                        number = true, suffix = "%", isError = state.socError,
                    )
                }
                if (state.socError) Text(stringResource(R.string.form_soc_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else {
                WfTextField(state.fuelGrade, actions.onFuelGrade, stringResource(R.string.form_fuel_grade) + " (" + stringResource(R.string.optional) + ")")
            }
            WfTextField(state.station, actions.onStation, stringResource(R.string.form_station) + " (" + stringResource(R.string.optional) + ")")
            WfTextField(state.note, actions.onNote, stringResource(R.string.form_note) + " (" + stringResource(R.string.optional) + ")", singleLine = false)
        }
    }

    if (pickingDate) {
        val localDay = java.time.Instant.ofEpochMilli(state.dateTime).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDay.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { picked ->
                        // The picker returns midnight UTC of the chosen day; keep the local time of day.
                        val zone = java.time.ZoneId.systemDefault()
                        val day = java.time.Instant.ofEpochMilli(picked).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        val time = java.time.Instant.ofEpochMilli(state.dateTime).atZone(zone).toLocalTime()
                        actions.onDate(day.atTime(time).atZone(zone).toInstant().toEpochMilli())
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState) }
    }
}
