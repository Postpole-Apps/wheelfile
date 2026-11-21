package com.postpole.wheelfile.ui.vehicle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.document.Region
import com.postpole.wheelfile.domain.units.LocaleDefaults
import com.postpole.wheelfile.ui.document.PresetPicker
import java.time.LocalDate
import com.postpole.wheelfile.ui.common.ChoiceChips
import com.postpole.wheelfile.ui.common.SectionLabel
import com.postpole.wheelfile.ui.common.WfTextField
import com.postpole.wheelfile.ui.common.label
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing

/** s01-welcome. No ads. "Import from another app" arrives with the importers in V6. */
@Composable
fun WelcomeScreen(onAddVehicle: () -> Unit, onImport: () -> Unit = {}) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(64.dp))
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(24.dp))) {
                    Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, Modifier.size(96.dp))
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(96.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(Spacing.l))
                Text(
                    stringResource(R.string.welcome_headline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.s))
                Text(
                    stringResource(R.string.welcome_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.xl))
                listOf(R.string.welcome_feature_1, R.string.welcome_feature_2, R.string.welcome_feature_3).forEach {
                    Text(
                        stringResource(it),
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(
                onClick = onAddVehicle,
                modifier = Modifier.fillMaxWidth().height(Sizes.saveButtonHeight),
                shape = PillShape,
            ) { Text(stringResource(R.string.welcome_add_vehicle), style = MaterialTheme.typography.labelLarge) }
            // Secondary call to action (spec §2.7): neutral label, no source app names.
            androidx.compose.material3.TextButton(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().height(Sizes.saveButtonHeight),
            ) { Text(stringResource(R.string.import_entry), style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(Spacing.l))
        }
    }
}

@Composable
fun AddVehicleRoute(
    onClose: () -> Unit,
    onSaved: (Long) -> Unit,
    onOpenPro: () -> Unit,
    viewModel: AddVehicleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            when (it) {
                is AddVehicleEvent.Saved -> onSaved(it.vehicleId)
                AddVehicleEvent.OpenPro -> onOpenPro()
            }
        }
    }
    AddVehicleScreen(state = state, actions = viewModel.toActions(onClose))
}

fun AddVehicleViewModel.toActions(onClose: () -> Unit) = AddVehicleActions(
    onClose = onClose,
    onName = ::setName,
    onType = ::setType,
    onPowertrain = ::setPowertrain,
    onMake = ::setMake,
    onModel = ::setModel,
    onYear = ::setYear,
    onPlate = ::setPlate,
    onVin = ::setVin,
    onDistance = ::setDistance,
    onVolume = ::setVolume,
    onEconomy = ::setEconomy,
    onCurrency = ::setCurrency,
    onContinue = ::onContinue,
    onUnitsDone = ::onUnitsDone,
    onRegion = ::setRegion,
    onTogglePreset = ::togglePreset,
    onSkipPresets = ::skipPresets,
    onBack = ::onBack,
    onSave = ::save,
    onDismissLimit = ::dismissFreeLimit,
    onSeePro = ::seePro,
    onArchiveOpen = ::openArchivePicker,
    onArchive = ::archiveAndContinue,
)

class AddVehicleActions(
    val onClose: () -> Unit,
    val onName: (String) -> Unit,
    val onType: (VehicleType) -> Unit,
    val onPowertrain: (Powertrain) -> Unit,
    val onMake: (String) -> Unit,
    val onModel: (String) -> Unit,
    val onYear: (String) -> Unit,
    val onPlate: (String) -> Unit,
    val onVin: (String) -> Unit,
    val onDistance: (DistanceUnit) -> Unit,
    val onVolume: (VolumeUnit) -> Unit,
    val onEconomy: (com.postpole.wheelfile.data.db.EconomyUnit) -> Unit,
    val onCurrency: (String) -> Unit,
    val onContinue: () -> Unit,
    /** Step 2 to step 3 (region presets). */
    val onUnitsDone: () -> Unit,
    val onRegion: (Region) -> Unit,
    val onTogglePreset: (String) -> Unit,
    val onSkipPresets: () -> Unit,
    /** Returns false when nothing was handled and the screen should close. */
    val onBack: () -> Boolean,
    val onSave: () -> Unit,
    val onDismissLimit: () -> Unit,
    val onSeePro: () -> Unit,
    val onArchiveOpen: () -> Unit,
    val onArchive: (Long) -> Unit,
)

/** s01b-add-vehicle, s01c-units and s01d-region-presets as three steps of one full-screen form. No ads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(state: AddVehicleState, actions: AddVehicleActions) {
    val step = state.step
    BackHandler(enabled = step != AddVehicleStep.DETAILS) { actions.onBack() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(
                                when (step) {
                                    AddVehicleStep.DETAILS -> R.string.add_vehicle_title
                                    AddVehicleStep.UNITS -> R.string.units_title
                                    AddVehicleStep.REGION -> R.string.presets_title
                                },
                            ),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stringResource(R.string.add_vehicle_step, step.ordinal + 1, AddVehicleStep.entries.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (!actions.onBack()) actions.onClose() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                },
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                    .padding(horizontal = Sizes.screenMargin),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                when (step) {
                    AddVehicleStep.DETAILS -> DetailsStep(state, actions)
                    AddVehicleStep.UNITS -> UnitsStep(state, actions)
                    AddVehicleStep.REGION -> RegionStep(state, actions)
                }
                Spacer(Modifier.height(Spacing.s))
            }
            // Save bar pinned; the form scrolls above it (works at 200% font scale).
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = Sizes.screenMargin, vertical = Spacing.m)) {
                    Button(
                        onClick = when (step) {
                            AddVehicleStep.DETAILS -> actions.onContinue
                            AddVehicleStep.UNITS -> actions.onUnitsDone
                            AddVehicleStep.REGION -> actions.onSave
                        },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.saveButtonHeight),
                        shape = PillShape,
                    ) {
                        Text(
                            stringResource(if (step == AddVehicleStep.REGION) R.string.units_save_vehicle else R.string.continue_action),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (step == AddVehicleStep.REGION) {
                        TextButton(
                            onClick = actions.onSkipPresets,
                            enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetMin),
                        ) { Text(stringResource(R.string.presets_skip)) }
                    }
                }
            }
        }
    }
    if (state.showFreeLimit) FreeLimitDialog(actions.onDismissLimit, actions.onSeePro, actions.onArchiveOpen)
    if (state.showArchivePicker) {
        ArchivePickerDialog(state.archiveCandidates, actions.onArchive, actions.onDismissLimit)
    }
}

/** Lists active vehicles; picking one archives it to make room under the free cap. */
@Composable
fun ArchivePickerDialog(candidates: List<Vehicle>, onPick: (Long) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.archive_pick_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.archive_pick_body), style = MaterialTheme.typography.bodyMedium)
                candidates.forEach { v ->
                    TextButton(
                        onClick = { onPick(v.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetMin),
                    ) { Text(v.name, Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/** st-free-limit: adding a 3rd active vehicle. */
@Composable
fun FreeLimitDialog(onCancel: () -> Unit, onSeePro: () -> Unit, onArchive: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.free_limit_title)) },
        text = { Text(stringResource(R.string.free_limit_body)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onSeePro, Modifier.heightIn(min = Sizes.touchTargetMin)) {
                    Text(stringResource(R.string.free_limit_see_pro))
                }
                TextButton(onClick = onArchive, Modifier.heightIn(min = Sizes.touchTargetMin)) {
                    Text(stringResource(R.string.free_limit_archive))
                }
                TextButton(onClick = onCancel, Modifier.heightIn(min = Sizes.touchTargetMin)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun DetailsStep(state: AddVehicleState, actions: AddVehicleActions) {
    WfTextField(
        label = stringResource(R.string.field_name),
        value = state.name,
        onValueChange = actions.onName,
        error = if (state.nameMissing) stringResource(R.string.field_name_required) else null,
    )
    SectionLabel(stringResource(R.string.field_type))
    ChoiceChips(VehicleType.entries, state.type, { stringResource(it.label()) }, actions.onType)
    SectionLabel(stringResource(R.string.field_powertrain))
    ChoiceChips(Powertrain.entries, state.powertrain, { stringResource(it.label()) }, actions.onPowertrain)
    WfTextField(stringResource(R.string.field_make), state.make, actions.onMake)
    WfTextField(stringResource(R.string.field_model), state.model, actions.onModel)
    WfTextField(
        label = stringResource(R.string.field_year),
        value = state.year,
        onValueChange = actions.onYear,
        keyboardType = KeyboardType.Number,
        error = if (state.yearInvalid) stringResource(R.string.field_year_invalid) else null,
    )
    WfTextField(stringResource(R.string.field_plate), state.plate, actions.onPlate)
    WfTextField(stringResource(R.string.field_vin), state.vin, actions.onVin)
}

@Composable
private fun UnitsStep(state: AddVehicleState, actions: AddVehicleActions) {
    Text(
        stringResource(R.string.units_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.vinSoftWarning) {
        Text(stringResource(R.string.field_vin_soft), style = MaterialTheme.typography.bodySmall)
    }
    SectionLabel(stringResource(R.string.units_distance))
    ChoiceChips(DistanceUnit.entries, state.units.distance, { stringResource(it.label()) }, actions.onDistance)
    if (!state.isElectric) {
        val gas = state.powertrain == Powertrain.CNG || state.powertrain == Powertrain.HYDROGEN
        SectionLabel(stringResource(R.string.units_volume))
        ChoiceChips(
            if (gas) listOf(VolumeUnit.KG) else listOf(VolumeUnit.L, VolumeUnit.US_GAL, VolumeUnit.UK_GAL),
            state.units.volume,
            { stringResource(it.label()) },
            actions.onVolume,
        )
        if (!gas) {
            SectionLabel(stringResource(R.string.units_fuel_economy))
            ChoiceChips(LocaleDefaults.fuelEconomyOptions, state.units.economy, { stringResource(it.label()) }, actions.onEconomy)
        }
    }
    if (state.isElectric) {
        SectionLabel(stringResource(R.string.units_electric_economy))
        ChoiceChips(LocaleDefaults.electricEconomyOptions, state.units.economy, { stringResource(it.label()) }, actions.onEconomy)
    }
    SectionLabel(stringResource(R.string.units_currency))
    CurrencyPicker(state.units.currency, actions.onCurrency)
}

/** s01d-region-presets: suggested documents for the region of the device locale. The disclaimer is always visible. */
@Composable
private fun RegionStep(state: AddVehicleState, actions: AddVehicleActions) {
    Text(
        stringResource(R.string.presets_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PresetPicker(
        region = state.region,
        onRegion = actions.onRegion,
        selected = state.selectedPresets,
        onToggle = actions.onTogglePreset,
        vehicleYear = state.year.toIntOrNull(),
        today = LocalDate.now(),
    )
}

@Composable
private fun CurrencyPicker(selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val codes = remember { LocaleDefaults.currencyCodes() }
    Box(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.fieldMinHeight),
            shape = com.postpole.wheelfile.ui.theme.FieldShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selected, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            codes.forEach { code ->
                DropdownMenuItem(text = { Text(code) }, onClick = { onSelect(code); open = false })
            }
        }
    }
}
