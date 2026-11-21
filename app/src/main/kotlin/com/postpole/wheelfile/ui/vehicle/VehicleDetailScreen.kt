package com.postpole.wheelfile.ui.vehicle

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.ads.AdScreen
import com.postpole.wheelfile.ads.AdSlot
import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.domain.report.ReportRange
import com.postpole.wheelfile.ui.document.DocumentsTab
import com.postpole.wheelfile.ui.report.ReportsInput
import com.postpole.wheelfile.ui.report.ReportsTab
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.economy.StreamEconomy
import com.postpole.wheelfile.domain.history.HistoryBuilder
import com.postpole.wheelfile.domain.history.HistoryFilter
import com.postpole.wheelfile.domain.history.HistoryKind
import com.postpole.wheelfile.domain.history.HistoryRow
import com.postpole.wheelfile.domain.units.UnitConversion
import com.postpole.wheelfile.ui.common.EconomyDelta
import com.postpole.wheelfile.ui.common.formatMoney
import com.postpole.wheelfile.ui.common.formatNumber
import com.postpole.wheelfile.ui.common.label
import com.postpole.wheelfile.ui.entry.defaultFormIsCharge
import com.postpole.wheelfile.ui.entry.label
import com.postpole.wheelfile.ui.entry.supportsCharge
import com.postpole.wheelfile.ui.entry.supportsFillUp
import com.postpole.wheelfile.ui.theme.HeroShape
import com.postpole.wheelfile.ui.theme.LocalWheelfileColors
import com.postpole.wheelfile.ui.theme.LocalWheelfileType
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class VehicleTab(val label: Int) {
    OVERVIEW(R.string.tab_overview),
    HISTORY(R.string.tab_history),
    DOCUMENTS(R.string.tab_documents),
    REPORTS(R.string.tab_reports),
}

/** At 200% font scale the four tabs no longer fit, so the row scrolls. */
internal const val SCROLLABLE_TABS_FONT_SCALE = 2f

class VehicleDetailActions(
    val onBack: () -> Unit,
    val onSwitch: (Long) -> Unit,
    val onAddEntry: (charge: Boolean) -> Unit,
    val onFilter: (HistoryFilter) -> Unit,
    val onArchive: () -> Unit,
    val onRestore: () -> Unit,
    val onDismissFreeLimit: () -> Unit,
    val onSeePro: () -> Unit,
    val onArchiveOpen: () -> Unit,
    val onArchivePick: (Long) -> Unit,
    /** Opens the service (false) or expense (true) form. */
    val onAddRecord: (expense: Boolean) -> Unit = {},
    /** The bell in the top app bar: this vehicle's Reminders. */
    val onOpenReminders: () -> Unit = {},
    val onOpenDocument: (Long) -> Unit = {},
    val onAddDocument: () -> Unit = {},
    val onOpenPresets: () -> Unit = {},
    val onReportRange: (ReportRange) -> Unit = {},
    val onCustomRange: (from: Long?, to: Long?) -> Unit = { _, _ -> },
    /** The History or Reports tab was opened: records the "reviewed" flag for the V7 interstitial. */
    val onReviewViewed: () -> Unit = {},
)

@Composable
fun VehicleDetailRoute(
    onBack: () -> Unit,
    onSwitch: (Long) -> Unit,
    onAddEntry: (vehicleId: Long, charge: Boolean) -> Unit,
    onAddRecord: (vehicleId: Long, expense: Boolean) -> Unit,
    onOpenReminders: (vehicleId: Long) -> Unit,
    onOpenPro: () -> Unit,
    onOpenDocument: (vehicleId: Long, documentId: Long) -> Unit,
    onAddDocument: (vehicleId: Long) -> Unit,
    onOpenPresets: (vehicleId: Long) -> Unit,
    viewModel: VehicleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val id = state.vehicle?.id
    VehicleDetailScreen(
        state = state,
        actions = VehicleDetailActions(
            onBack = onBack,
            onSwitch = onSwitch,
            onAddEntry = { charge -> id?.let { onAddEntry(it, charge) } },
            onFilter = viewModel::setFilter,
            onArchive = { viewModel.archive() },
            onRestore = { viewModel.restore() },
            onDismissFreeLimit = viewModel::dismissFreeLimit,
            onSeePro = { viewModel.dismissFreeLimit(); onOpenPro() },
            onArchiveOpen = viewModel::openArchivePicker,
            onArchivePick = viewModel::archiveAndRestore,
            onAddRecord = { expense -> id?.let { onAddRecord(it, expense) } },
            onOpenReminders = { id?.let(onOpenReminders) },
            onOpenDocument = { doc -> id?.let { onOpenDocument(it, doc) } },
            onAddDocument = { id?.let(onAddDocument) },
            onOpenPresets = { id?.let(onOpenPresets) },
            onReportRange = viewModel::setReportRange,
            onCustomRange = viewModel::setCustomRange,
            onReviewViewed = viewModel::reviewViewed,
        ),
        adSlot = { AdSlot(it) },
    )
}

/**
 * s04-vehicle-overview, s05-history, s11-documents and s13-reports inside one Vehicle detail. Overview is ad-free;
 * the History, Documents and Reports tabs each carry one banner ([adSlot] is told which screen). The bell in the top
 * bar opens this vehicle's Reminders (badge = due soon or overdue).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    state: VehicleDetailUiState,
    actions: VehicleDetailActions,
    adSlot: @Composable (AdScreen) -> Unit,
    initialTab: VehicleTab = VehicleTab.OVERVIEW,
) {
    var tabIndex by rememberSaveable { mutableStateOf(initialTab.ordinal) }
    val tab = VehicleTab.entries[tabIndex]
    val vehicle = state.vehicle
    // Viewing History or Reports is what makes the later "back to Garage" interstitial possible (V7). Record it only.
    LaunchedEffect(tab) {
        if (tab == VehicleTab.HISTORY || tab == VehicleTab.REPORTS) actions.onReviewViewed()
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { VehicleSwitcher(state, actions.onSwitch) },
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (vehicle != null) {
                            RemindersBell(state.dueReminders, actions.onOpenReminders)
                            OverflowMenu(vehicle, actions)
                        }
                    },
                )
                VehicleTabs(tabIndex) { tabIndex = it }
            }
        },
        bottomBar = {
            if (vehicle != null) {
                when (tab) {
                    VehicleTab.HISTORY -> adSlot(AdScreen.HISTORY)
                    VehicleTab.DOCUMENTS -> adSlot(AdScreen.DOCUMENTS)
                    VehicleTab.REPORTS -> adSlot(AdScreen.REPORTS)
                    VehicleTab.OVERVIEW -> Unit
                }
            }
        },
        floatingActionButton = {
            if (vehicle != null && !vehicle.archived && tab == VehicleTab.DOCUMENTS) {
                ExtendedFloatingActionButton(
                    onClick = actions.onAddDocument,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { Text(stringResource(R.string.documents_add)) }
            } else if (vehicle != null && !vehicle.archived && (tab == VehicleTab.OVERVIEW || tab == VehicleTab.HISTORY)) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (vehicle.supportsCharge() && vehicle.supportsFillUp()) {
                        ExtendedFloatingActionButton(
                            onClick = { actions.onAddEntry(true) },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) { Text(stringResource(R.string.fab_charge)) }
                    }
                    val charge = vehicle.defaultFormIsCharge()
                    ExtendedFloatingActionButton(
                        onClick = { actions.onAddEntry(charge) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) { Text(stringResource(if (charge) R.string.fab_charge else R.string.fab_fillup)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.data == null) return@Box
            when (tab) {
                VehicleTab.OVERVIEW -> OverviewTab(state.data, state.readOnly)
                VehicleTab.HISTORY -> HistoryTab(state.data, state.historyFilter, actions.onFilter)
                VehicleTab.DOCUMENTS -> DocumentsTab(
                    items = state.documents,
                    attachmentCount = state.attachmentCount,
                    receiptCount = state.receiptCount,
                    isPro = state.isPro,
                    readOnly = state.readOnly,
                    now = System.currentTimeMillis(),
                    onOpen = actions.onOpenDocument,
                    onPresets = actions.onOpenPresets,
                )
                VehicleTab.REPORTS -> ReportsTab(
                    data = state.data,
                    input = ReportsInput(
                        range = state.reportRange,
                        customFrom = state.customFrom,
                        customTo = state.customTo,
                        isPro = state.isPro,
                        compareSources = state.compareSources,
                        now = System.currentTimeMillis(),
                    ),
                    onRange = actions.onReportRange,
                    onCustom = actions.onCustomRange,
                    onSeePro = actions.onSeePro,
                )
            }
        }
    }
    if (state.showFreeLimit) {
        FreeLimitDialog(onCancel = actions.onDismissFreeLimit, onSeePro = actions.onSeePro, onArchive = actions.onArchiveOpen)
    }
    if (state.showArchivePicker) {
        ArchivePickerDialog(state.switcher, actions.onArchivePick, actions.onDismissFreeLimit)
    }
}

@Composable
private fun VehicleTabs(selected: Int, onSelect: (Int) -> Unit) {
    val scrollable = LocalDensity.current.fontScale >= SCROLLABLE_TABS_FONT_SCALE
    val tabs: @Composable () -> Unit = {
        VehicleTab.entries.forEachIndexed { i, t ->
            Tab(
                selected = i == selected,
                onClick = { onSelect(i) },
                modifier = Modifier.heightIn(min = Sizes.tabRow),
                text = { Text(stringResource(t.label), style = MaterialTheme.typography.titleSmall, maxLines = 1) },
            )
        }
    }
    if (scrollable) {
        ScrollableTabRow(selectedTabIndex = selected, edgePadding = Spacing.l, tabs = tabs)
    } else {
        TabRow(selectedTabIndex = selected, tabs = tabs)
    }
}

@Composable
private fun VehicleSwitcher(state: VehicleDetailUiState, onSwitch: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = state.vehicle?.name.orEmpty()
    val canSwitch = state.switcher.size > 1
    Box {
        Surface(
            onClick = { if (canSwitch) open = true },
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                if (canSwitch) Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.switch_vehicle))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.switcher.forEach { v ->
                DropdownMenuItem(text = { Text(v.name) }, onClick = { open = false; onSwitch(v.id) })
            }
        }
    }
}

/** The Reminders bell (48 dp target, 24 dp icon). The badge shows the due-soon count and is absent when nothing is due. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindersBell(due: Int, onClick: () -> Unit) {
    val description = if (due > 0) {
        pluralStringResource(R.plurals.reminders_bell_due, due, due)
    } else {
        stringResource(R.string.reminders_title)
    }
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (due > 0) Badge { Text(due.toString()) } }) {
            Icon(Icons.Default.Notifications, contentDescription = description)
        }
    }
}

@Composable
private fun OverflowMenu(vehicle: Vehicle, actions: VehicleDetailActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!vehicle.archived) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_add_service)) },
                    onClick = { open = false; actions.onAddRecord(false) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_add_expense)) },
                    onClick = { open = false; actions.onAddRecord(true) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (vehicle.archived) R.string.menu_restore else R.string.menu_archive)) },
                onClick = {
                    open = false
                    if (vehicle.archived) actions.onRestore() else actions.onArchive()
                },
            )
        }
    }
}

// ---- Overview ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewTab(data: VehicleData, readOnly: Boolean) {
    var showHow by remember { mutableStateOf(false) }
    val v = data.vehicle
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Sizes.screenMargin, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (readOnly) Text(stringResource(R.string.archived_notice), style = MaterialTheme.typography.bodyMedium)
        EconomyCard(data, onHow = { showHow = true })
        CostCard(data)
        Text(stringResource(R.string.overview_recent), style = MaterialTheme.typography.titleMedium)
        val recent = HistoryBuilder.build(data, HistoryFilter.ALL).flatMap { it.rows }.take(5)
        if (recent.isEmpty()) {
            Text(
                stringResource(R.string.overview_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        recent.forEach { EntryRow(it, v) }
        Spacer(Modifier.height(Sizes.fab * 2 + Sizes.fabGap * 2))
    }
    if (showHow) {
        ModalBottomSheet(onDismissRequest = { showHow = false }) { HowCalculated(data) }
    }
}

@Composable
private fun EconomyCard(data: VehicleData, onHow: () -> Unit) {
    val numbers = LocalWheelfileType.current
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.overview_economy), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onHow, Modifier.heightIn(min = Sizes.touchTargetMin)) {
                    Text(stringResource(R.string.overview_how_calculated))
                }
            }
            val streams = data.stats.streams.filter { it.average != null }
            if (streams.isEmpty()) {
                val ev = data.vehicle.powertrain == com.postpole.wheelfile.data.db.Powertrain.ELECTRIC
                Text(
                    stringResource(if (ev) R.string.overview_economy_empty_ev else R.string.overview_economy_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A PHEV has two streams, each with its own economy.
            streams.forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (data.stats.streams.size > 1) {
                        Text(stringResource(streamLabel(s.stream)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text("${formatNumber(s.average!!, 1)} ${stringResource(s.unit.label())}", style = numbers.numberLarge)
                        s.deltaPercent?.let { EconomyDelta(it, s.unit) }
                    }
                    Text(
                        stringResource(R.string.overview_economy_average) +
                            (s.latest?.let { " · ${stringResource(R.string.overview_economy_latest)} ${formatNumber(it, 1)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun streamLabel(s: EnergyStream) = when (s) {
    EnergyStream.LIQUID -> R.string.stream_liquid
    EnergyStream.ELECTRIC -> R.string.stream_electric
    EnergyStream.GAS_KG -> R.string.stream_gas
}

@Composable
private fun CostCard(data: VehicleData) {
    val v = data.vehicle
    val c = data.stats.costs
    val numbers = LocalWheelfileType.current
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.overview_costs), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Stat(stringResource(R.string.overview_cost_total), formatMoney(c.totalMinor, v.currency), numbers.numberMedium)
                Stat(stringResource(R.string.overview_cost_energy), formatMoney(c.energyMinor, v.currency), numbers.numberMedium)
                Stat(
                    stringResource(R.string.overview_cost_per_distance, stringResource(v.distanceUnit.label())),
                    c.costPerDistanceMinor?.let { formatMoney(Math.round(it), v.currency) } ?: "–",
                    numbers.numberMedium,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, style: androidx.compose.ui.text.TextStyle) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = style)
    }
}

/** The "How calculated?" sheet: explains the full-to-full window with the vehicle's own numbers. */
@Composable
private fun HowCalculated(data: VehicleData) {
    val v = data.vehicle
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(stringResource(R.string.how_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.how_body), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.how_missed), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.how_average), style = MaterialTheme.typography.bodyMedium)
        data.stats.streams.filter { it.validSegments > 0 }.forEach { s: StreamEconomy ->
            val distance = UnitConversion.fromKm(s.totalDistanceKm, v.distanceUnit)
            val quantity = if (s.stream == EnergyStream.LIQUID) UnitConversion.fromLitres(s.totalQuantity, v.volumeUnit) else s.totalQuantity
            val quantityUnit = when (s.stream) {
                EnergyStream.ELECTRIC -> stringResource(R.string.unit_kwh)
                EnergyStream.GAS_KG -> stringResource(R.string.unit_kg)
                EnergyStream.LIQUID -> stringResource(v.volumeUnit.label())
            }
            Text(
                stringResource(
                    R.string.how_your_numbers,
                    "${formatNumber(quantity, 1)} $quantityUnit",
                    "${formatNumber(distance, 0)} ${stringResource(v.distanceUnit.label())}",
                    s.validSegments,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (s.invalidSegments > 0) {
                Text(stringResource(R.string.how_invalid, s.invalidSegments), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---- History ----

@Composable
private fun HistoryTab(data: VehicleData, filter: HistoryFilter, onFilter: (HistoryFilter) -> Unit) {
    val months = remember(data, filter) { HistoryBuilder.build(data, filter) }
    val v = data.vehicle
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Sizes.screenMargin, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            HistoryFilter.entries.forEach { f ->
                FilterChip(
                    selected = f == filter,
                    onClick = { onFilter(f) },
                    label = { Text(stringResource(filterLabel(f))) },
                    shape = RoundedCornerShape(10.dp),
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Sizes.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (months.isEmpty()) {
                Text(
                    stringResource(R.string.history_empty),
                    Modifier.padding(vertical = Spacing.xl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val monthFmt = remember { DateTimeFormatter.ofPattern("LLLL yyyy") }
            months.forEach { group ->
                Surface(shape = HeroShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(group.month.format(monthFmt), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            Text(formatMoney(group.totalMinor, v.currency), style = LocalWheelfileType.current.numberMedium)
                        }
                        group.rows.forEach { EntryRow(it, v) }
                    }
                }
            }
            Spacer(Modifier.height(Sizes.fab + Sizes.fabGap * 2))
        }
    }
}

private fun filterLabel(f: HistoryFilter) = when (f) {
    HistoryFilter.ALL -> R.string.filter_all
    HistoryFilter.FUEL -> R.string.filter_fuel
    HistoryFilter.CHARGING -> R.string.filter_charging
    HistoryFilter.SERVICE -> R.string.filter_service
    HistoryFilter.EXPENSES -> R.string.filter_expenses
}

@Composable
internal fun EntryRow(row: HistoryRow, vehicle: Vehicle) {
    val colors = LocalWheelfileColors.current
    val numbers = LocalWheelfileType.current
    val date = Instant.ofEpochMilli(row.dateTime).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val title = when (row.kind) {
        HistoryKind.FILL_UP -> stringResource(R.string.history_fillup)
        HistoryKind.CHARGE -> stringResource(R.string.history_charge)
        HistoryKind.SERVICE -> row.serviceItems.joinToString().ifEmpty { stringResource(R.string.history_service) }
        HistoryKind.EXPENSE -> row.expenseCategory?.let { stringResource(it.label()) } ?: stringResource(R.string.history_expense)
    }
    val meta = buildList {
        add(date)
        row.odometer?.let { add("${formatNumber(it, 0)} ${stringResource(vehicle.distanceUnit.label())}") }
        row.quantity?.let {
            val unit = if (row.kind == HistoryKind.CHARGE) stringResource(R.string.unit_kwh) else stringResource(vehicle.volumeUnit.label())
            add("${formatNumber(it, 2)} $unit")
        }
        if ((row.kind == HistoryKind.FILL_UP || row.kind == HistoryKind.CHARGE) && !row.isFull) add(stringResource(R.string.history_partial))
    }.joinToString(" · ")
    Column(Modifier.fillMaxWidth().heightIn(min = Sizes.listRowMin).padding(vertical = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            row.totalMinor?.let { Text(formatMoney(it, vehicle.currency), style = numbers.numberMedium) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            row.economy?.let {
                val unit = com.postpole.wheelfile.domain.economy.VehicleStatsCalculator.displayUnit(
                    vehicle, row.stream ?: EnergyStream.LIQUID,
                )
                Text("${formatNumber(it, 1)} ${stringResource(unit.label())}", style = MaterialTheme.typography.labelMedium)
                row.deltaPercent?.let { d -> EconomyDelta(d, unit) }
            }
            if (row.checkOdometer) {
                Text(stringResource(R.string.history_check_odometer), style = MaterialTheme.typography.labelMedium, color = colors.warning)
            }
            if (row.missedPrevious) {
                Text(stringResource(R.string.history_missed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
