package com.postpole.wheelfile.ui.importer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.importer.ImportResult
import com.postpole.wheelfile.importer.SourceVehicle
import com.postpole.wheelfile.importer.VehicleTarget
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import java.text.DateFormat
import java.util.Currency
import java.util.Date

/** Import flow (S14). No ads on any step; this screen never hosts an ad slot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportRoute(onBack: () -> Unit, onOpenPro: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onPicked(it) }
    val pick = { picker.launch(arrayOf("*/*")) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = Sizes.screenMargin)) {
            when (val s = state) {
                ImportUiState.Idle -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.import_intro), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.import_formats_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { pick() },
                        modifier = Modifier.fillMaxWidth().height(Sizes.saveButtonHeight),
                        shape = PillShape,
                    ) { Text(stringResource(R.string.import_choose_file)) }
                }
                ImportUiState.Reading, ImportUiState.Importing -> Progress(
                    stringResource(if (s == ImportUiState.Reading) R.string.import_reading else R.string.import_importing),
                )
                ImportUiState.Unsupported -> Column {
                    Text(stringResource(R.string.import_unsupported_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.import_unsupported_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { pick() }, modifier = Modifier.fillMaxWidth(), shape = PillShape) {
                        Text(stringResource(R.string.import_choose_another))
                    }
                }
                ImportUiState.Failed -> Column {
                    Text(stringResource(R.string.import_failed), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth(), shape = PillShape) {
                        Text(stringResource(R.string.import_try_again))
                    }
                }
                is ImportUiState.Preview -> PreviewContent(s, viewModel)
                is ImportUiState.Done -> ResultContent(s.result, onDone = onBack, onOpenPro = onOpenPro)
            }
        }
    }
}

@Composable
private fun Progress(text: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text)
    }
}

@Composable
private fun PreviewContent(s: ImportUiState.Preview, vm: ImportViewModel) {
    var showCurrency by rememberSaveable { mutableStateOf(false) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        if (s.parsed.format.beta) {
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.import_beta)) })
            Text(
                stringResource(R.string.import_beta_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(stringResource(R.string.import_preview_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.import_currency)) },
            supportingContent = { Text(stringResource(R.string.import_currency_hint)) },
            trailingContent = { Text(s.options.currency) },
            modifier = Modifier.clickable { showCurrency = true },
        )
        HorizontalDivider()
        s.parsed.vehicles.forEach { v ->
            VehicleCard(v, s, vm, dateFormat)
            Spacer(Modifier.height(8.dp))
        }
        if (s.parsed.errors.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.import_preview_unreadable, s.parsed.errors.size, s.parsed.errors.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::confirm,
            enabled = s.parsed.vehicles.any { it.key !in s.options.skipped },
            modifier = Modifier.fillMaxWidth().height(Sizes.saveButtonHeight),
            shape = PillShape,
        ) { Text(stringResource(R.string.import_start)) }
        Spacer(Modifier.height(16.dp))
    }
    if (showCurrency) {
        val codes = remember { Currency.getAvailableCurrencies().map { it.currencyCode }.sorted() }
        AlertDialog(
            onDismissRequest = { showCurrency = false },
            title = { Text(stringResource(R.string.import_currency)) },
            text = {
                LazyColumn {
                    items(codes, key = { it }) { code ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                vm.setCurrency(code)
                                showCurrency = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = code == s.options.currency, onClick = null)
                            Text(code, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCurrency = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun VehicleCard(v: SourceVehicle, s: ImportUiState.Preview, vm: ImportViewModel, dateFormat: DateFormat) {
    val skipped = v.key in s.options.skipped
    val target = s.options.targets[v.key] ?: VehicleTarget.New
    var menu by remember { mutableStateOf(false) }
    val newLabel = stringResource(R.string.import_target_new)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = !skipped, onCheckedChange = { vm.setSkipped(v.key, !it) })
            }
            Text(
                stringResource(R.string.import_vehicle_counts, v.fills.size, v.services.size, v.expenses.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            val first = v.firstDate
            val last = v.lastDate
            if (first != null && last != null) {
                Text(
                    stringResource(R.string.import_vehicle_range, dateFormat.format(Date(first)), dateFormat.format(Date(last))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val units = listOfNotNull(v.distanceUnit?.name?.lowercase(), v.volumeUnit?.name?.lowercase()?.replace('_', ' '))
            if (units.isNotEmpty()) {
                Text(
                    stringResource(R.string.import_vehicle_units, units.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!skipped) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { menu = true }) {
                    Text(
                        stringResource(
                            R.string.import_target_label,
                            (target as? VehicleTarget.Existing)?.let { t -> s.existing.firstOrNull { it.id == t.vehicleId }?.name } ?: newLabel,
                        ),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(newLabel) },
                        onClick = {
                            vm.setTarget(v.key, VehicleTarget.New)
                            menu = false
                        },
                    )
                    s.existing.forEach { e ->
                        DropdownMenuItem(
                            text = { Text(e.name) },
                            onClick = {
                                vm.setTarget(v.key, VehicleTarget.Existing(e.id))
                                menu = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** "Imported 312 fill-ups, 41 services; skipped 3 duplicates; 2 rows couldn't be read" plus the unreadable rows. */
@Composable
private fun ResultContent(result: ImportResult, onDone: () -> Unit, onOpenPro: () -> Unit) {
    val context = LocalContext.current
    val report = importErrorReport(result)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        if (result.beta) AssistChip(onClick = {}, label = { Text(stringResource(R.string.import_beta)) })
        Text(stringResource(R.string.import_done_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                R.string.import_done_summary,
                pluralStringResource(R.plurals.import_n_fillups, result.fills, result.fills),
                pluralStringResource(R.plurals.import_n_services, result.services, result.services),
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (result.expenses > 0) {
            Text(pluralStringResource(R.plurals.import_n_expenses, result.expenses, result.expenses))
        }
        Text(pluralStringResource(R.plurals.import_n_duplicates, result.skippedDuplicates, result.skippedDuplicates))
        if (result.errors.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                pluralStringResource(R.plurals.import_n_unreadable, result.errors.size, result.errors.size),
                color = MaterialTheme.colorScheme.error,
            )
            result.errors.forEach {
                Text(
                    stringResource(R.string.import_error_row, it.file, it.line, it.reason),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.import_error_report_label), report))
                },
            ) { Text(stringResource(R.string.import_copy_report)) }
        }
        if (result.archivedVehicles.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.import_archived_note, result.archivedVehicles.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onOpenPro) { Text(stringResource(R.string.import_archived_cta)) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(Sizes.saveButtonHeight), shape = PillShape) {
            Text(stringResource(R.string.import_done_button))
        }
    }
}

/** Plain-text report of unreadable rows, for the "Copy error report" button. */
fun importErrorReport(result: ImportResult): String = buildString {
    appendLine("Import report")
    appendLine("Imported ${result.fills} fill-ups, ${result.services} services, ${result.expenses} expenses; skipped ${result.skippedDuplicates} duplicates")
    result.errors.forEach { appendLine("${it.file}, line ${it.line}: ${it.reason}") }
}
