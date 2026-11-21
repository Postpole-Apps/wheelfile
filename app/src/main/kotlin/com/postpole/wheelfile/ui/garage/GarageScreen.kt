package com.postpole.wheelfile.ui.garage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.ads.AdScreen
import com.postpole.wheelfile.ads.AdSlot
import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.economy.StreamEconomy
import com.postpole.wheelfile.ui.common.EconomyDelta
import com.postpole.wheelfile.ui.common.formatNumber
import com.postpole.wheelfile.ui.common.label
import com.postpole.wheelfile.ui.entry.defaultFormIsCharge
import com.postpole.wheelfile.ui.entry.supportsCharge
import com.postpole.wheelfile.ui.entry.supportsFillUp
import com.postpole.wheelfile.ui.theme.HeroShape
import com.postpole.wheelfile.ui.theme.LocalWheelfileType
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing

class GarageActions(
    val onOpenSettings: () -> Unit,
    val onOpenPro: () -> Unit,
    val onAddVehicle: () -> Unit,
    val onOpenVehicle: (Long) -> Unit,
    val onAddEntry: (vehicleId: Long, charge: Boolean) -> Unit,
    val onSelect: (Long) -> Unit,
)

@Composable
fun GarageRoute(
    actions: GarageActions,
    /** False in the two-pane layout while a vehicle is open, so only one banner is on screen. */
    showBanner: Boolean = true,
    viewModel: GarageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GarageScreen(
        state = state,
        actions = GarageActions(
            actions.onOpenSettings, actions.onOpenPro, actions.onAddVehicle, actions.onOpenVehicle, actions.onAddEntry,
            onSelect = { viewModel.select(it); actions.onSelect(it) },
        ),
        adSlot = { if (showBanner) AdSlot(AdScreen.GARAGE) },
    )
}

/**
 * s02-garage, st-empty-garage, st-loading. [adSlot] is placed in the bottom bar only when the Garage has at least
 * one vehicle, so the first-run Garage never requests an ad (DECISIONS.md item 5). The Scaffold keeps the FAB 16 dp
 * above the slot, and 16 dp above the gesture inset when the slot is collapsed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    state: GarageUiState,
    actions: GarageActions,
    adSlot: @Composable () -> Unit,
) {
    val content = state as? GarageUiState.Content
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.garage_title), style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    if (state !is GarageUiState.Loading) {
                        IconButton(onClick = actions.onAddVehicle, Modifier.heightIn(min = Sizes.touchTargetMin)) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.garage_add_vehicle_fab))
                        }
                    }
                    TextButton(onClick = actions.onOpenPro) { Text(stringResource(R.string.nav_pro)) }
                    TextButton(onClick = actions.onOpenSettings) { Text(stringResource(R.string.nav_settings)) }
                },
            )
        },
        bottomBar = { if (content != null) adSlot() },
        floatingActionButton = {
            val selected = content?.selected?.vehicle
            if (selected != null) {
                val charge = selected.defaultFormIsCharge()
                FloatingActionButton(
                    onClick = { actions.onAddEntry(selected.id, charge) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { Icon(Icons.Default.Add, contentDescription = stringResource(if (charge) R.string.fab_charge else R.string.fab_fillup)) }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                GarageUiState.Loading -> GarageSkeleton()
                GarageUiState.Empty -> EmptyGarage(actions.onAddVehicle)
                is GarageUiState.Content -> GarageContent(state, actions)
            }
        }
    }
}

@Composable
private fun EmptyGarage(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.garage_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Spacing.s))
        Text(
            stringResource(R.string.garage_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onAdd, Modifier.heightIn(min = Sizes.touchTargetMin)) {
            Text(stringResource(R.string.welcome_add_vehicle))
        }
    }
}

@Composable
private fun GarageSkeleton() {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    val loadingLabel = stringResource(R.string.garage_loading)
    Column(
        Modifier.fillMaxSize().padding(Sizes.screenMargin).semantics { contentDescription = loadingLabel },
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(Modifier.fillMaxWidth().height(42.dp).background(block, RoundedCornerShape(10.dp)))
        Box(Modifier.fillMaxWidth().height(178.dp).background(block, HeroShape))
        Box(Modifier.fillMaxWidth().height(112.dp).background(block, RoundedCornerShape(20.dp)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GarageContent(state: GarageUiState.Content, actions: GarageActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Sizes.screenMargin, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        val selected = state.selected
        if (state.active.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                state.active.forEach { d ->
                    FilterChip(
                        selected = d.vehicle.id == selected?.vehicle?.id,
                        onClick = { actions.onSelect(d.vehicle.id) },
                        label = { Text(d.vehicle.name) },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
        }
        if (selected != null) HeroCard(selected, actions)
        if (state.others.isNotEmpty()) {
            Text(
                stringResource(R.string.garage_other_vehicles),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.others.forEach { VehicleCard(it, onClick = { actions.onOpenVehicle(it.vehicle.id) }) }
        }
        if (state.archived.isNotEmpty()) {
            Text(
                stringResource(R.string.garage_archived),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.archived.forEach { ArchivedRow(it, onClick = { actions.onOpenVehicle(it.id) }) }
        }
        // Room for the FAB so the last card is never hidden behind it.
        Spacer(Modifier.height(Sizes.fab + Sizes.fabGap))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(data: VehicleData, actions: GarageActions) {
    val v = data.vehicle
    Surface(
        onClick = { actions.onOpenVehicle(v.id) },
        shape = HeroShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(v.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.garage_odometer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val odometer = data.stats.odometer
            if (odometer != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatNumber(odometer, 0), style = LocalWheelfileType.current.numberOdometer)
                    Text(
                        " ${stringResource(v.distanceUnit.label())}",
                        Modifier.padding(bottom = 6.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            } else {
                Text(stringResource(R.string.garage_no_readings), style = MaterialTheme.typography.titleMedium)
            }
            EconomyLine(data.stats.streams.firstOrNull())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                QuickActions(v, actions)
            }
        }
    }
}

@Composable
private fun QuickActions(v: Vehicle, actions: GarageActions) {
    if (v.supportsFillUp()) {
        AssistChip(
            onClick = { actions.onAddEntry(v.id, false) },
            label = { Text(stringResource(R.string.garage_action_fillup)) },
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        )
    }
    if (v.supportsCharge()) {
        AssistChip(
            onClick = { actions.onAddEntry(v.id, true) },
            label = { Text(stringResource(R.string.garage_action_charge)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (v.supportsFillUp()) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
            ),
        )
    }
    AssistChip(
        onClick = { actions.onOpenVehicle(v.id) },
        label = { Text(stringResource(R.string.garage_action_open)) },
        border = AssistChipDefaults.assistChipBorder(enabled = true),
    )
}

@Composable
private fun EconomyLine(stream: StreamEconomy?) {
    val numbers = LocalWheelfileType.current
    val shown = stream?.latest ?: stream?.average
    if (stream == null || shown == null) {
        Text(
            stringResource(R.string.garage_economy_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("${formatNumber(shown, 1)} ${stringResource(stream.unit.label())}", style = numbers.numberLarge)
        stream.deltaPercent?.let { EconomyDelta(it, stream.unit) }
    }
}

@Composable
private fun VehicleCard(data: VehicleData, onClick: () -> Unit) {
    val v = data.vehicle
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(v.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(v.make, v.model, v.year?.toString()).joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            data.stats.odometer?.let {
                Text(
                    "${formatNumber(it, 0)} ${stringResource(v.distanceUnit.label())}",
                    style = LocalWheelfileType.current.numberMedium,
                )
            }
            EconomyLine(data.stats.streams.firstOrNull())
        }
    }
}

@Composable
private fun ArchivedRow(v: Vehicle, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.listRowMin),
    ) {
        Row(Modifier.padding(Sizes.cardPadding), verticalAlignment = Alignment.CenterVertically) {
            Text(v.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.garage_archived),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
