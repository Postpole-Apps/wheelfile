package com.postpole.wheelfile.ui.vehicle

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.ads.AdScreen
import com.postpole.wheelfile.ads.AdSlot
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.economy.StreamEconomy
import com.postpole.wheelfile.domain.history.EntryFlag
import com.postpole.wheelfile.domain.history.HistoryEntry
import com.postpole.wheelfile.domain.history.HistoryFilter
import com.postpole.wheelfile.domain.history.MonthGroup
import com.postpole.wheelfile.domain.units.EconomyUnits
import com.postpole.wheelfile.domain.units.UnitConversion
import com.postpole.wheelfile.ui.components.DeltaPill
import com.postpole.wheelfile.ui.components.EmptyState
import com.postpole.wheelfile.ui.components.FabAboveAd
import com.postpole.wheelfile.ui.components.SurfaceCard
import com.postpole.wheelfile.ui.format.Formatters
import com.postpole.wheelfile.ui.format.currentLocale
import com.postpole.wheelfile.ui.garage.deltaText
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import com.postpole.wheelfile.ui.theme.WheelfileTheme
import java.time.format.TextStyle

/** The Reminders bell is wired in a later milestone; it stays hidden until then (issue V3 scope). */
const val REMINDERS_BELL_ENABLED = false

const val TAB_OVERVIEW = 0
const val TAB_HISTORY = 1
const val TAB_DOCUMENTS = 2
const val TAB_REPORTS = 3

@Composable
fun VehicleDetailScreen(
    initialTab: Int,
    onBack: () -> Unit,
    onAddEnergy: (Long, EnergyStream) -> Unit,
    onSwitchVehicle: (Long) -> Unit,
    viewModel: VehicleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VehicleDetailContent(
        state = state,
        initialTab = initialTab,
        onBack = onBack,
        onAddEnergy = onAddEnergy,
        onSwitchVehicle = onSwitchVehicle,
        onFilter = viewModel::setFilter,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailContent(
    state: VehicleDetailState,
    initialTab: Int,
    onBack: () -> Unit,
    onAddEnergy: (Long, EnergyStream) -> Unit,
    onSwitchVehicle: (Long) -> Unit,
    onFilter: (HistoryFilter) -> Unit,
    historyBanner: @Composable (onHeight: (Dp) -> Unit) -> Unit = { onHeight -> AdSlot(AdScreen.HISTORY, onHeightChange = onHeight) },
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(TAB_OVERVIEW, TAB_REPORTS)) }
    var adHeight by remember { mutableStateOf(0.dp) }
    val vehicle = state.vehicle
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { VehicleSwitcher(vehicle, state.switchTargets, onSwitchVehicle) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (REMINDERS_BELL_ENABLED) {
                            IconButton(onClick = {}) {
                                Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.reminders_bell))
                            }
                        }
                    },
                )
                VehicleTabs(tab) { tab = it }
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (vehicle == null) {
                if (!state.loading) EmptyState(stringResource(R.string.garage_empty_title))
            } else {
                when (tab) {
                    TAB_OVERVIEW -> {
                        OverviewTab(state, vehicle)
                        // Overview is ad-free: no AdSlot here.
                        if (!state.readOnly) FabAboveAd(0.dp) { EnergyFab(vehicle, onAddEnergy) }
                    }
                    TAB_HISTORY -> {
                        HistoryTab(state, vehicle, onFilter, bottomPadding = adHeight + Sizes.fab + Sizes.fabGap * 2 + Sizes.gestureInset)
                        if (!state.readOnly) FabAboveAd(adHeight) { EnergyFab(vehicle, onAddEnergy) }
                        Box(Modifier.align(Alignment.BottomCenter)) { historyBanner { adHeight = it } }
                    }
                    TAB_DOCUMENTS -> EmptyState(stringResource(R.string.documents_empty), Modifier.align(Alignment.Center))
                    else -> EmptyState(stringResource(R.string.reports_empty), Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun VehicleSwitcher(vehicle: Vehicle?, others: List<Vehicle>, onSwitch: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            enabled = others.isNotEmpty(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
            modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(vehicle?.name ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                if (others.isNotEmpty()) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.switch_vehicle))
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            others.forEach { other ->
                DropdownMenuItem(text = { Text(other.name) }, onClick = { open = false; onSwitch(other.id) })
            }
        }
    }
}

/** Tabs scroll at 200% font scale and above (`lt-garage-200`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.tab_overview, R.string.tab_history, R.string.tab_documents, R.string.tab_reports)
    val tabs: @Composable () -> Unit = {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selected == index,
                onClick = { onSelect(index) },
                text = { Text(stringResource(label), style = MaterialTheme.typography.titleSmall, maxLines = 1) },
                modifier = Modifier.heightIn(min = Sizes.tabRow),
            )
        }
    }
    if (LocalConfiguration.current.fontScale >= 2f) {
        PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 0.dp, tabs = tabs)
    } else {
        PrimaryTabRow(selectedTabIndex = selected, tabs = tabs)
    }
}

@Composable
private fun EnergyFab(vehicle: Vehicle, onAddEnergy: (Long, EnergyStream) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val phev = vehicle.powertrain == Powertrain.PHEV
    val stream = EconomyUnits.defaultStream(vehicle.powertrain)
    Box {
        ExtendedFloatingActionButton(
            onClick = { if (phev) menu = true else onAddEnergy(vehicle.id, stream) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(if (stream == EnergyStream.ELECTRIC) R.string.quick_charge else R.string.quick_fillup)) },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quick_fillup)) },
                onClick = { menu = false; onAddEnergy(vehicle.id, EnergyStream.LIQUID) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quick_charge)) },
                onClick = { menu = false; onAddEnergy(vehicle.id, EnergyStream.ELECTRIC) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Overview (`s04-vehicle-overview`): 0 ads
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewTab(state: VehicleDetailState, vehicle: Vehicle) {
    val locale = currentLocale()
    var how by remember { mutableStateOf<StreamEconomy?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Sizes.screenMargin, end = Sizes.screenMargin, top = Spacing.m, bottom = Sizes.fab + Sizes.fabGap * 3 + Sizes.gestureInset),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (state.readOnly) {
            Text(stringResource(R.string.overview_archived_banner), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val streams = state.streams.filter { it.last100Km != null }
        if (streams.isEmpty()) {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.overview_no_economy), style = MaterialTheme.typography.bodyMedium)
            }
        }
        streams.forEach { stream ->
            EconomyCard(stream, vehicle, onHow = { how = stream })
        }
        state.costs?.let { costs ->
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.overview_costs), style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val perDistance = costs.costPerKmMinor?.let { perKm ->
                            val perUnit = if (vehicle.distanceUnit == com.postpole.wheelfile.data.db.DistanceUnit.MI) {
                                perKm * UnitConversion.toKm(1.0, vehicle.distanceUnit)
                            } else {
                                perKm
                            }
                            Formatters.moneyPrecise(perUnit, vehicle.currency, locale)
                        }
                        Stat(stringResource(R.string.overview_cost_per_distance, EconomyUnits.distanceLabel(vehicle.distanceUnit)), perDistance ?: "—")
                        Stat(
                            stringResource(R.string.overview_cost_per_month),
                            costs.perMonthMinor?.let { Formatters.money(it, vehicle.currency, locale) } ?: "—",
                        )
                        Stat(stringResource(R.string.overview_cost_total), Formatters.money(costs.totalMinor, vehicle.currency, locale))
                    }
                }
            }
        }
        Text(stringResource(R.string.overview_recent), style = MaterialTheme.typography.titleMedium)
        if (state.recent.isEmpty()) {
            Text(stringResource(R.string.overview_recent_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                Column {
                    state.recent.forEachIndexed { index, log ->
                        EnergyRow(log, vehicle, flag = null)
                        if (index < state.recent.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
    how?.let { stream ->
        ModalBottomSheet(onDismissRequest = { how = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            HowCalculatedSheet(stream, vehicle, state.logs, onClose = { how = null })
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = WheelfileTheme.type.numberMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EconomyCard(stream: StreamEconomy, vehicle: Vehicle, onHow: () -> Unit) {
    val locale = currentLocale()
    val unit = EconomyUnits.forStream(vehicle, stream.stream)
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                stringResource(
                    when (stream.stream) {
                        EnergyStream.ELECTRIC -> R.string.overview_economy_electric
                        EnergyStream.GAS_KG -> R.string.overview_economy_gas
                        EnergyStream.LIQUID -> R.string.overview_economy
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Column {
                    Text(stringResource(R.string.economy_last), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.economy(stream.last100Km!!, unit, locale) ?: "—", style = WheelfileTheme.type.numberLarge)
                }
                Column {
                    Text(stringResource(R.string.economy_average), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stream.average100Km?.let { Formatters.economy(it, unit, locale) } ?: "—", style = WheelfileTheme.type.numberLarge)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                stream.delta?.let { DeltaPill(deltaText(it.percent, it.better, locale), it.better) } ?: Box {}
                TextButton(onClick = onHow, modifier = Modifier.heightIn(min = Sizes.touchTargetMin)) {
                    Text(stringResource(R.string.overview_how_calculated))
                }
            }
        }
    }
}

/** "How calculated?" sheet: explains the full-to-full window behind the latest number. No ads. */
@Composable
private fun HowCalculatedSheet(stream: StreamEconomy, vehicle: Vehicle, logs: List<EnergyLog>, onClose: () -> Unit) {
    val locale = currentLocale()
    val unit = EconomyUnits.forStream(vehicle, stream.stream)
    val distanceUnit = EconomyUnits.distanceLabel(vehicle.distanceUnit)
    val quantityUnit = EconomyUnits.quantityLabel(vehicle, stream.stream)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(stringResource(R.string.how_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.how_body), style = MaterialTheme.typography.bodyMedium)
        val segment = stream.lastValidSegment
        val start = logs.firstOrNull { it.id == segment?.startEntryId }
        val end = logs.firstOrNull { it.id == segment?.endEntryId }
        if (segment != null && start != null && end != null) {
            val driven = end.odometer - start.odometer
            Text(
                stringResource(
                    R.string.how_window,
                    Formatters.number(start.odometer, 1, locale), Formatters.number(end.odometer, 1, locale),
                    distanceUnit, Formatters.number(driven, 1, locale),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            val quantityInUnits = if (stream.stream == EnergyStream.LIQUID) {
                UnitConversion.fromLitres(segment.quantity, vehicle.volumeUnit)
            } else {
                segment.quantity
            }
            Text(
                stringResource(R.string.how_quantity, Formatters.number(quantityInUnits, 2, locale), quantityUnit),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.how_result, Formatters.economy(segment.per100Km!!, unit, locale) ?: "—"),
                style = WheelfileTheme.type.numberMedium,
            )
        } else {
            Text(stringResource(R.string.how_none), style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(R.string.how_average), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.how_skipped), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClose, modifier = Modifier.heightIn(min = Sizes.touchTargetMin)) { Text(stringResource(R.string.how_close)) }
    }
}

// ---------------------------------------------------------------------------------------------
// History (`s05-history`): filter chips, month cards, entry rows
// ---------------------------------------------------------------------------------------------

@Composable
private fun HistoryTab(state: VehicleDetailState, vehicle: Vehicle, onFilter: (HistoryFilter) -> Unit, bottomPadding: Dp) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Sizes.screenMargin, end = Sizes.screenMargin, top = Spacing.m, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                items(HistoryFilter.entries) { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { onFilter(filter) },
                        label = { Text(stringResource(filterLabel(filter))) },
                        modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
                    )
                }
            }
        }
        if (state.history.isEmpty()) {
            item { EmptyState(stringResource(R.string.history_empty)) }
        }
        items(state.history, key = { it.month.toString() }) { group -> MonthCard(group, vehicle) }
    }
}

private fun filterLabel(filter: HistoryFilter): Int = when (filter) {
    HistoryFilter.ALL -> R.string.filter_all
    HistoryFilter.FILL_UPS -> R.string.filter_fillups
    HistoryFilter.CHARGES -> R.string.filter_charges
    HistoryFilter.SERVICE -> R.string.filter_service
    HistoryFilter.EXPENSES -> R.string.filter_expenses
}

@Composable
private fun MonthCard(group: MonthGroup, vehicle: Vehicle) {
    val locale = currentLocale()
    SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.m)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = Sizes.cardPadding), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    group.month.month.getDisplayName(TextStyle.FULL, locale) + " " + group.month.year,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(Formatters.money(group.totalMinor, vehicle.currency, locale), style = WheelfileTheme.type.numberMedium)
            }
            group.entries.forEach { entry ->
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                when (entry) {
                    is HistoryEntry.Energy -> EnergyRow(entry.log, vehicle, entry.flag)
                    is HistoryEntry.Service -> SimpleRow(
                        stringResource(R.string.entry_service), entry.record.items.joinToString(), entry.record.dateTime, entry.record.totalMinor, vehicle,
                    )
                    is HistoryEntry.ExpenseItem -> SimpleRow(
                        stringResource(R.string.entry_expense), entry.expense.category.name.lowercase().replace('_', ' '),
                        entry.expense.dateTime, entry.expense.amountMinor, vehicle,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleRow(title: String, subtitle: String, dateTime: Long, totalMinor: Long?, vehicle: Vehicle) {
    val locale = currentLocale()
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.listRowMin).padding(horizontal = Sizes.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(Formatters.date(dateTime, locale), subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        totalMinor?.let { Text(Formatters.money(it, vehicle.currency, locale), style = WheelfileTheme.type.numberMedium) }
    }
}

@Composable
private fun EnergyRow(log: EnergyLog, vehicle: Vehicle, flag: EntryFlag?) {
    val locale = currentLocale()
    val unit = EconomyUnits.quantityLabel(vehicle, log.stream)
    val distance = EconomyUnits.distanceLabel(vehicle.distanceUnit)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.listRowMin).padding(horizontal = Sizes.cardPadding, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (log.stream == EnergyStream.ELECTRIC) R.string.entry_charge else R.string.entry_fillup) +
                    " · " + Formatters.number(log.quantity, 2, locale) + " " + unit,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                Formatters.date(log.dateTime, locale) + " · " + Formatters.number(log.odometer, 0, locale) + " " + distance +
                    if (!log.isFull) " · " + stringResource(R.string.history_partial) else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            flag?.let {
                Text(
                    stringResource(if (it == EntryFlag.CHECK_ODOMETER) R.string.history_check_odometer else R.string.history_missed),
                    style = MaterialTheme.typography.labelMedium, color = WheelfileTheme.colors.warning,
                )
            }
        }
        log.totalMinor?.let { Text(Formatters.money(it, vehicle.currency, locale), style = WheelfileTheme.type.numberMedium) }
    }
}
