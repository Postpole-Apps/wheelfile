package com.postpole.wheelfile.ui.vehicle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.units.EconomyUnits
import com.postpole.wheelfile.ui.components.ChoiceChips
import com.postpole.wheelfile.ui.components.SaveBar
import com.postpole.wheelfile.ui.components.WfTextField
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing

/** Add vehicle (`s01b-add-vehicle`, `s01c-units`). Two steps; no ads on any form. */
@Composable
fun AddVehicleScreen(
    onClose: () -> Unit,
    onSaved: (vehicleId: Long, onboarding: Boolean) -> Unit,
    onLimitReached: () -> Unit,
    viewModel: VehicleFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is VehicleFormEvent.Saved -> onSaved(event.vehicleId, event.onboarding)
                VehicleFormEvent.LimitReached -> onLimitReached()
            }
        }
    }
    AddVehicleContent(
        state = state,
        onClose = onClose,
        actions = VehicleFormActions(
            onName = viewModel::onName, onType = viewModel::onType, onPowertrain = viewModel::onPowertrain,
            onMake = viewModel::onMake, onModel = viewModel::onModel, onYear = viewModel::onYear,
            onPlate = viewModel::onPlate, onVin = viewModel::onVin, onDistance = viewModel::onDistance,
            onVolume = viewModel::onVolume, onEconomy = viewModel::onEconomy, onCurrency = viewModel::onCurrency,
            onNext = viewModel::next, onBack = viewModel::back, onSave = viewModel::save,
        ),
    )
}

class VehicleFormActions(
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
    val onNext: () -> Unit,
    val onBack: () -> Unit,
    val onSave: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleContent(state: VehicleFormState, onClose: () -> Unit, actions: VehicleFormActions) {
    val onUnits = state.step == VehicleFormStep.UNITS
    BackHandler(enabled = onUnits) { actions.onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(if (onUnits) R.string.units_title else R.string.add_vehicle_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.add_vehicle_step, if (onUnits) 2 else 1, 2),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = if (onUnits) actions.onBack else onClose, modifier = Modifier.padding(0.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
        bottomBar = {
            SaveBar(
                text = stringResource(if (onUnits) R.string.save else R.string.next),
                onClick = if (onUnits) actions.onSave else actions.onNext,
                enabled = !state.saving,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Sizes.screenMargin, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (onUnits) UnitsStep(state, actions) else DetailsStep(state, actions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsStep(state: VehicleFormState, actions: VehicleFormActions) {
    WfTextField(
        value = state.name, onValueChange = actions.onName, label = stringResource(R.string.vehicle_name),
        modifier = Modifier.testTag("vehicle_name"),
        isError = state.nameError, supporting = if (state.nameError) stringResource(R.string.vehicle_name_error) else null,
    )
    Section(stringResource(R.string.vehicle_type)) {
        val types = VehicleType.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = state.type == type,
                    onClick = { actions.onType(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, types.size),
                ) { Text(vehicleTypeLabel(type), maxLines = 1) }
            }
        }
    }
    Section(stringResource(R.string.vehicle_powertrain)) {
        ChoiceChips(
            options = Powertrain.entries,
            selected = state.powertrain,
            label = { powertrainLabel(it) },
            onSelect = actions.onPowertrain,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        WfTextField(state.make, actions.onMake, stringResource(R.string.vehicle_make), Modifier.weight(1f))
        WfTextField(state.model, actions.onModel, stringResource(R.string.vehicle_model), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        WfTextField(
            state.year, actions.onYear, stringResource(R.string.vehicle_year), Modifier.weight(1f), number = true,
            isError = state.yearError, supporting = if (state.yearError) stringResource(R.string.vehicle_year_error) else null,
        )
        WfTextField(state.plate, actions.onPlate, stringResource(R.string.vehicle_plate), Modifier.weight(1f))
    }
    WfTextField(
        state.vin, actions.onVin, stringResource(R.string.vehicle_vin),
        supporting = if (state.vinLooksOdd) stringResource(R.string.vehicle_vin_hint) else null,
        isWarning = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UnitsStep(state: VehicleFormState, actions: VehicleFormActions) {
    Section(stringResource(R.string.units_distance)) {
        ChoiceChips(
            options = DistanceUnit.entries, selected = state.distance, onSelect = actions.onDistance,
            label = { stringResource(if (it == DistanceUnit.KM) R.string.unit_km else R.string.unit_mi) },
        )
    }
    val needsVolume = state.powertrain != Powertrain.ELECTRIC && state.powertrain != Powertrain.HYDROGEN && state.powertrain != Powertrain.CNG
    if (needsVolume) {
        Section(stringResource(R.string.units_volume)) {
            ChoiceChips(
                options = listOf(VolumeUnit.L, VolumeUnit.US_GAL, VolumeUnit.UK_GAL), selected = state.volume, onSelect = actions.onVolume,
                label = {
                    stringResource(
                        when (it) {
                            VolumeUnit.L -> R.string.unit_litres
                            VolumeUnit.US_GAL -> R.string.unit_us_gal
                            VolumeUnit.UK_GAL -> R.string.unit_uk_gal
                            VolumeUnit.KG -> R.string.unit_kg
                        },
                    )
                },
            )
        }
    }
    val options = EconomyUnits.optionsFor(state.powertrain)
    if (options.size > 1) {
        Section(stringResource(R.string.units_economy)) {
            ChoiceChips(options = options, selected = state.economy, onSelect = actions.onEconomy, label = { EconomyUnits.label(it) })
            if (state.powertrain == Powertrain.PHEV) {
                Text(
                    stringResource(R.string.units_electric_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    WfTextField(
        value = state.currency,
        onValueChange = { actions.onCurrency(it.uppercase().filter(Char::isLetter).take(3)) },
        label = stringResource(R.string.units_currency),
        supporting = stringResource(R.string.units_currency_hint),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
internal fun vehicleTypeLabel(type: VehicleType): String = stringResource(
    when (type) {
        VehicleType.CAR -> R.string.vehicle_type_car
        VehicleType.MOTORBIKE -> R.string.vehicle_type_motorbike
        VehicleType.VAN -> R.string.vehicle_type_van
        VehicleType.OTHER -> R.string.vehicle_type_other
    },
)

@Composable
internal fun powertrainLabel(p: Powertrain): String = stringResource(
    when (p) {
        Powertrain.PETROL -> R.string.powertrain_petrol
        Powertrain.DIESEL -> R.string.powertrain_diesel
        Powertrain.LPG -> R.string.powertrain_lpg
        Powertrain.CNG -> R.string.powertrain_cng
        Powertrain.ELECTRIC -> R.string.powertrain_electric
        Powertrain.PHEV -> R.string.powertrain_phev
        Powertrain.HYBRID -> R.string.powertrain_hybrid
        Powertrain.HYDROGEN -> R.string.powertrain_hydrogen
    },
)
