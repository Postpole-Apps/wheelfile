package com.postpole.wheelfile.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.ReminderItem
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.domain.reminder.ReminderStatus
import com.postpole.wheelfile.ui.common.formatDate
import com.postpole.wheelfile.ui.common.formatNumber
import com.postpole.wheelfile.ui.common.label
import com.postpole.wheelfile.ui.theme.LocalWheelfileColors
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import kotlin.math.abs

/** s09-reminders. Opened from the Vehicle detail bell (one vehicle) or Settings › Reminders (all). 0 ads. */
@Composable
fun RemindersRoute(onBack: () -> Unit, onAdd: (vehicleId: Long) -> Unit, viewModel: RemindersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RemindersScreen(state, onBack, onAdd, viewModel::markDone, viewModel::delete)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    state: RemindersUiState,
    onBack: () -> Unit,
    onAdd: (Long) -> Unit,
    onDone: (ReminderItem) -> Unit,
    onDelete: (ReminderItem) -> Unit,
) {
    var pickVehicle by remember { mutableStateOf(false) }
    // Which vehicle a new reminder is for: the filtered one, the only active one, or a choice from the menu.
    val target = state.vehicle?.takeIf { !it.archived } ?: state.vehicles.singleOrNull()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.titleLarge)
                        state.vehicle?.let {
                            Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.loaded && state.vehicles.isNotEmpty() && (state.vehicle == null || target != null)) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { if (target != null) onAdd(target.id) else pickVehicle = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) { Text(stringResource(R.string.reminders_add)) }
                    DropdownMenu(expanded = pickVehicle, onDismissRequest = { pickVehicle = false }) {
                        state.vehicles.forEach { v ->
                            DropdownMenuItem(text = { Text(v.name) }, onClick = { pickVehicle = false; onAdd(v.id) })
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!state.loaded) return@Box
            if (state.items.isEmpty()) {
                Text(
                    stringResource(R.string.reminders_empty),
                    Modifier.align(Alignment.Center).padding(Spacing.xl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Box
            }
            val dueSoon = state.items.filter { it.state != null && it.state.status != ReminderStatus.UPCOMING }
            val upcoming = state.items.filter { it.state?.status == ReminderStatus.UPCOMING }
            val done = state.items.filter { it.reminder.done }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Sizes.screenMargin, end = Sizes.screenMargin, top = Spacing.m, bottom = Sizes.fab + Sizes.fabGap * 3,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                section(R.string.reminders_due_soon, dueSoon, state.vehicle == null, onDone, onDelete)
                section(R.string.reminders_upcoming, upcoming, state.vehicle == null, onDone, onDelete)
                section(R.string.reminders_done, done, state.vehicle == null, onDone, onDelete)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: Int,
    items: List<ReminderItem>,
    showVehicle: Boolean,
    onDone: (ReminderItem) -> Unit,
    onDelete: (ReminderItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "h$title") { Text(stringResource(title), style = MaterialTheme.typography.titleMedium) }
    items(items, key = { it.reminder.id }) { ReminderRow(it, showVehicle, onDone, onDelete) }
}

@Composable
private fun ReminderRow(item: ReminderItem, showVehicle: Boolean, onDone: (ReminderItem) -> Unit, onDelete: (ReminderItem) -> Unit) {
    val colors = LocalWheelfileColors.current
    val r = item.reminder
    val state = item.state
    val unit = stringResource(item.vehicle.distanceUnit.label())
    val container = when (state?.status) {
        ReminderStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        ReminderStatus.DUE_SOON -> colors.warningContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val content = when (state?.status) {
        ReminderStatus.OVERDUE -> MaterialTheme.colorScheme.onErrorContainer
        ReminderStatus.DUE_SOON -> colors.onWarningContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(shape = RoundedCornerShape(20.dp), color = container, contentColor = content, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleSmall)
                    if (showVehicle) Text(item.vehicle.name, style = MaterialTheme.typography.bodySmall)
                }
                state?.let { StatusPill(it.status, it.daysLeft, it.distanceLeft, unit) }
            }
            if (r.kind != ReminderKind.ODOMETER && r.dueDate != null) {
                Text(stringResource(R.string.reminder_due_on, formatDate(r.dueDate)), style = MaterialTheme.typography.bodySmall)
            }
            if (r.kind != ReminderKind.DATE && r.dueOdometer != null) {
                Text(
                    stringResource(R.string.reminder_at_odometer, formatNumber(r.dueOdometer, 0), unit),
                    style = MaterialTheme.typography.bodySmall,
                )
                state?.distanceLeft?.takeIf { it > 0 }?.let {
                    Text(
                        stringResource(R.string.reminder_distance_left, formatNumber(it, 0), unit) +
                            (state.estimatedDueDate?.let { d -> " · " + stringResource(R.string.reminder_estimated_around, formatDate(d)) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (r.kind == ReminderKind.FIRST_OF) {
                Text(stringResource(R.string.reminder_whichever_first), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!r.done) {
                    TextButton(onClick = { onDone(item) }, Modifier.heightIn(min = Sizes.touchTargetMin)) {
                        Text(stringResource(R.string.reminder_mark_done))
                    }
                }
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.reminder_delete))
                }
            }
        }
    }
}

private const val SHORT_DATE_DAYS = 60L

@Composable
private fun StatusPill(status: ReminderStatus, daysLeft: Long?, distanceLeft: Double?, unit: String) {
    val text = when {
        status == ReminderStatus.OVERDUE -> stringResource(R.string.reminder_overdue)
        daysLeft != null && daysLeft <= 0 -> stringResource(R.string.reminder_due_today)
        // Whichever-first: a close distance beats a far date.
        daysLeft != null && (distanceLeft == null || daysLeft <= SHORT_DATE_DAYS) ->
            pluralStringResource(R.plurals.reminder_in_days, daysLeft.toInt(), daysLeft.toInt())
        distanceLeft != null -> stringResource(R.string.reminder_about_left_short, formatNumber(abs(distanceLeft), 0), unit)
        else -> stringResource(R.string.reminder_due_soon)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Text(text, Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs), style = MaterialTheme.typography.labelMedium)
    }
}
