package com.postpole.wheelfile.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.ads.AdScreen
import com.postpole.wheelfile.ads.AdSlot
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.domain.economy.StreamEconomy
import com.postpole.wheelfile.domain.units.EconomyUnits
import com.postpole.wheelfile.domain.vehicle.hasElectricStream
import com.postpole.wheelfile.domain.vehicle.hasLiquidStream
import com.postpole.wheelfile.ui.components.DeltaPill
import com.postpole.wheelfile.ui.components.FabAboveAd
import com.postpole.wheelfile.ui.components.SurfaceCard
import com.postpole.wheelfile.ui.format.Formatters
import com.postpole.wheelfile.ui.format.currentLocale
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import com.postpole.wheelfile.ui.theme.WheelfileTheme

/**
 * Garage (`s02-garage`, `st-empty-garage`, `st-loading`): the only top-level home.
 * The banner exists only with at least one vehicle; the empty first-run Garage is ad-free.
 */
@Composable
fun GarageScreen(
    onOpenSettings: () -> Unit,
    onOpenPro: () -> Unit,
    onAddVehicle: () -> Unit,
    onOpenVehicle: (Long, tab: Int) -> Unit,
    onAddEnergy: (Long, EnergyStream) -> Unit,
    viewModel: GarageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLimit by remember { mutableStateOf(false) }
    var showArchivePicker by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                GarageEvent.OpenAddVehicle -> {
                    showLimit = false
                    showArchivePicker = false
                    onAddVehicle()
                }
                GarageEvent.ShowFreeLimit -> showLimit = true
            }
        }
    }
    GarageContent(
        state = state,
        onOpenSettings = onOpenSettings,
        onAddVehicle = viewModel::onAddVehicle,
        onSelect = viewModel::select,
        onOpenVehicle = onOpenVehicle,
        onAddEnergy = onAddEnergy,
        onRestore = viewModel::unarchive,
    )
    if (showLimit) {
        FreeLimitDialog(
            onSeePro = { showLimit = false; onOpenPro() },
            onArchive = { showLimit = false; showArchivePicker = true },
            onCancel = { showLimit = false },
        )
    }
    if (showArchivePicker) {
        ArchivePickerDialog(
            vehicles = state.active.map { it.vehicle.id to it.vehicle.name },
            onPick = { id -> showArchivePicker = false; viewModel.archiveThenAdd(id) },
            onCancel = { showArchivePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageContent(
    state: GarageState,
    onOpenSettings: () -> Unit,
    onAddVehicle: () -> Unit,
    onSelect: (Long) -> Unit,
    onOpenVehicle: (Long, Int) -> Unit,
    onAddEnergy: (Long, EnergyStream) -> Unit,
    onRestore: (Long) -> Unit,
    banner: @Composable (onHeight: (Dp) -> Unit) -> Unit = { onHeight -> AdSlot(AdScreen.GARAGE, onHeightChange = onHeight) },
) {
    var adHeight by remember { mutableStateOf(0.dp) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.garage_title), style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = onAddVehicle, modifier = Modifier.heightIn(min = Sizes.touchTargetMin)) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.garage_add_vehicle))
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = Sizes.touchTargetMin)) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.active.isEmpty() && state.archived.isEmpty() -> EmptyGarage(onAddVehicle)
                else -> {
                    GarageList(
                        state, onSelect, onOpenVehicle, onAddEnergy, onRestore,
                        bottomPadding = adHeight + Sizes.fab + Sizes.fabGap * 2 + Sizes.gestureInset,
                    )
                    state.selected?.let { selected ->
                        val stream = EconomyUnits.defaultStream(selected.vehicle.powertrain)
                        FabAboveAd(adHeight) {
                            ExtendedFloatingActionButton(
                                onClick = { onAddEnergy(selected.vehicle.id, stream) },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = {
                                    Text(stringResource(if (stream == EnergyStream.ELECTRIC) R.string.quick_charge else R.string.quick_fillup))
                                },
                            )
                        }
                    }
                }
            }
            // Banner only with at least one vehicle; no AdSlot (and so no ad request) otherwise.
            if (state.showsBanner) {
                Box(Modifier.align(Alignment.BottomCenter)) { banner { adHeight = it } }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.padding(Spacing.l))
        Text(stringResource(R.string.garage_loading), Modifier.padding(top = 96.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyGarage(onAddVehicle: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Sizes.screenMargin * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.garage_empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.garage_empty_body),
            Modifier.padding(vertical = Spacing.l),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddVehicle, shape = PillShape, modifier = Modifier.heightIn(min = Sizes.saveButtonHeight)) {
            Text(stringResource(R.string.garage_add_vehicle), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GarageList(
    state: GarageState,
    onSelect: (Long) -> Unit,
    onOpenVehicle: (Long, Int) -> Unit,
    onAddEnergy: (Long, EnergyStream) -> Unit,
    onRestore: (Long) -> Unit,
    bottomPadding: Dp,
) {
    val selected = state.selected
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Sizes.screenMargin, end = Sizes.screenMargin, top = Spacing.m, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (state.active.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(state.active, key = { it.vehicle.id }) { card ->
                        FilterChip(
                            selected = card.vehicle.id == selected?.vehicle?.id,
                            onClick = { onSelect(card.vehicle.id) },
                            label = { Text(card.vehicle.name) },
                            modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
                        )
                    }
                }
            }
        }
        if (selected != null) {
            item(key = "hero-${selected.vehicle.id}") { HeroCard(selected, onOpenVehicle, onAddEnergy) }
            items(state.active.filter { it.vehicle.id != selected.vehicle.id }, key = { "card-${it.vehicle.id}" }) { card ->
                VehicleCard(card, onOpen = { onOpenVehicle(card.vehicle.id, 0) })
            }
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived") { ArchivedSection(state.archived, onOpenVehicle, onRestore) }
        }
    }
}

@Composable
private fun HeroCard(card: VehicleCardData, onOpenVehicle: (Long, Int) -> Unit, onAddEnergy: (Long, EnergyStream) -> Unit) {
    val locale = currentLocale()
    val vehicle = card.vehicle
    SurfaceCard(
        Modifier.fillMaxWidth(),
        onClick = { onOpenVehicle(vehicle.id, 0) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.garage_odometer), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                card.odometer?.let { Formatters.number(it, 0, locale) + " " + EconomyUnits.distanceLabel(vehicle.distanceUnit) } ?: "—",
                style = WheelfileTheme.type.numberOdometer,
            )
            EconomyLines(card)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (vehicle.powertrain.hasLiquidStream()) {
                    QuickAction(stringResource(R.string.quick_fillup), filled = true) {
                        onAddEnergy(vehicle.id, EconomyUnits.fuelStream(vehicle.powertrain))
                    }
                }
                if (vehicle.powertrain.hasElectricStream()) {
                    QuickAction(stringResource(R.string.quick_charge), filled = vehicle.powertrain == Powertrain.ELECTRIC) {
                        onAddEnergy(vehicle.id, EnergyStream.ELECTRIC)
                    }
                }
                QuickAction(stringResource(R.string.quick_history), filled = false) { onOpenVehicle(vehicle.id, 1) }
            }
        }
    }
}

@Composable
private fun QuickAction(text: String, filled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = filled,
        onClick = onClick,
        label = { Text(text) },
        modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
    )
}

/** Last economy per stream with the delta pill (arrow and text); PHEV shows both streams. */
@Composable
private fun EconomyLines(card: VehicleCardData) {
    val locale = currentLocale()
    val withValue = card.streams.filter { it.last100Km != null }
    if (withValue.isEmpty()) {
        Text(stringResource(R.string.garage_no_economy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    withValue.forEach { stream ->
        val unit = EconomyUnits.forStream(card.vehicle, stream.stream)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                Formatters.economy(stream.last100Km!!, unit, locale) ?: "—",
                style = WheelfileTheme.type.numberLarge,
            )
            stream.delta?.let { delta ->
                DeltaPill(
                    text = deltaText(delta.percent, delta.better, locale),
                    better = delta.better,
                )
            }
        }
    }
}

@Composable
internal fun deltaText(percent: Double, better: Boolean, locale: java.util.Locale): String =
    stringResource(if (better) R.string.delta_better else R.string.delta_worse, Formatters.number(percent, 1, locale))

@Composable
private fun VehicleCard(card: VehicleCardData, onOpen: () -> Unit) {
    val locale = currentLocale()
    val vehicle = card.vehicle
    SurfaceCard(Modifier.fillMaxWidth().heightIn(min = Sizes.listRowMin), outlined = true, onClick = onOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
            Text(
                card.odometer?.let { Formatters.number(it, 0, locale) + " " + EconomyUnits.distanceLabel(vehicle.distanceUnit) } ?: "—",
                style = WheelfileTheme.type.numberLarge,
            )
            EconomyLines(card)
        }
    }
}

@Composable
private fun ArchivedSection(archived: List<VehicleCardData>, onOpenVehicle: (Long, Int) -> Unit, onRestore: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = Sizes.touchTargetMin)) {
            Text(stringResource(R.string.garage_archived_section, archived.size))
        }
        if (expanded) {
            Text(stringResource(R.string.garage_archived_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            archived.forEach { card ->
                SurfaceCard(Modifier.fillMaxWidth(), outlined = true, onClick = { onOpenVehicle(card.vehicle.id, 0) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(card.vehicle.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { onRestore(card.vehicle.id) }, modifier = Modifier.heightIn(min = Sizes.touchTargetMin)) {
                            Text(stringResource(R.string.garage_restore))
                        }
                    }
                }
            }
        }
    }
}

/** `st-free-limit`: See Pro / Archive a vehicle / Cancel. The paywall itself is a later milestone. */
@Composable
fun FreeLimitDialog(onSeePro: () -> Unit, onArchive: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.free_limit_title)) },
        text = { Text(stringResource(R.string.free_limit_body)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onSeePro, modifier = Modifier.heightIn(min = Sizes.touchTargetMin)) { Text(stringResource(R.string.free_limit_see_pro)) }
                TextButton(onClick = onArchive, modifier = Modifier.heightIn(min = Sizes.touchTargetMin)) { Text(stringResource(R.string.free_limit_archive)) }
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = Sizes.touchTargetMin)) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun ArchivePickerDialog(vehicles: List<Pair<Long, String>>, onPick: (Long) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.archive_pick_title)) },
        text = {
            Column {
                Text(stringResource(R.string.archive_pick_body))
                vehicles.forEach { (id, name) ->
                    TextButton(onClick = { onPick(id) }, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetMin)) { Text(name) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
