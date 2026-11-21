package com.postpole.wheelfile.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.importer.ImportResult
import com.postpole.wheelfile.ui.common.label
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

@Composable
fun ImportRoute(
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImportScreen(
        state = state,
        onBack = onBack,
        onOpenPro = onOpenPro,
        onPick = viewModel::onFilePicked,
        onReset = viewModel::reset,
        onChoice = viewModel::setChoice,
        onName = viewModel::setName,
        onDistance = viewModel::setDistance,
        onVolume = viewModel::setVolume,
        onCurrency = viewModel::setCurrency,
        onImport = viewModel::import,
    )
}

/** s14 import flow: pick a file, preview and map vehicles, then the result report (`st-error-import`). No ads here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    state: ImportUiState,
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    onPick: (android.net.Uri?) -> Unit,
    onReset: () -> Unit,
    onChoice: (String, MapChoice) -> Unit,
    onName: (String, String) -> Unit,
    onDistance: (String, DistanceUnit) -> Unit,
    onVolume: (String, VolumeUnit) -> Unit,
    onCurrency: (String) -> Unit,
    onImport: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onPick)
    val pick = { launcher.launch(arrayOf("*/*")) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ImportUiState.Idle -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.import_intro), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = pick, Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_choose_file)) }
                }
                ImportUiState.Reading -> Progress(R.string.import_reading)
                ImportUiState.Importing -> Progress(R.string.import_importing)
                is ImportUiState.Failed -> FailedView(state.reason, onChooseAnother = { onReset(); pick() })
                is ImportUiState.Preview -> PreviewView(state, onChoice, onName, onDistance, onVolume, onCurrency, onImport)
                is ImportUiState.Done -> ResultView(state.result, onOpenPro, onDone = onBack)
            }
        }
    }
}

@Composable
private fun Progress(message: Int) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(message))
    }
}

@Composable
private fun FailedView(reason: ImportFailure, onChooseAnother: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.import_unrecognised_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(
                when (reason) {
                    ImportFailure.UNRECOGNISED -> R.string.import_unrecognised_body
                    ImportFailure.TOO_LARGE -> R.string.import_too_large
                    ImportFailure.UNREADABLE -> R.string.import_read_failed
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onChooseAnother, Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_choose_another)) }
        Text(stringResource(R.string.support_email), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun day(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)

@Composable
private fun BetaBadge() {
    Text(
        stringResource(R.string.import_beta_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun PreviewView(
    state: ImportUiState.Preview,
    onChoice: (String, MapChoice) -> Unit,
    onName: (String, String) -> Unit,
    onDistance: (String, DistanceUnit) -> Unit,
    onVolume: (String, VolumeUnit) -> Unit,
    onCurrency: (String) -> Unit,
    onImport: () -> Unit,
) {
    var pickCurrency by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_preview_title), style = MaterialTheme.typography.titleLarge)
                if (state.file.beta) BetaBadge()
            }
        }
        if (state.file.beta) {
            item { Text(stringResource(R.string.import_beta_note, stringResource(R.string.support_email)), style = MaterialTheme.typography.bodyMedium) }
        }
        if (state.file.issues.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.import_unreadable_preview, state.file.issues.size),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
            }
        }
        items(state.vehicles, key = { it.parsed.key }) { row ->
            VehicleCard(row, state, onChoice, onName, onDistance, onVolume)
        }
        item {
            Text(stringResource(R.string.import_currency_note), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { pickCurrency = true }, Modifier.fillMaxWidth()) {
                Text("${stringResource(R.string.import_currency_pick)}: ${currencyLabel(state.currency)}")
            }
        }
        if (state.willArchive) {
            item { Text(stringResource(R.string.import_over_cap_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
        }
        item {
            Button(onClick = onImport, enabled = state.canImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_button))
            }
        }
    }
    if (pickCurrency) {
        val codes = remember { Currency.getAvailableCurrencies().map { it.currencyCode }.sorted() }
        AlertDialog(
            onDismissRequest = { pickCurrency = false },
            title = { Text(stringResource(R.string.import_currency_pick)) },
            text = {
                LazyColumn {
                    items(codes, key = { it }) { code ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onCurrency(code); pickCurrency = false }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = code == state.currency, onClick = null)
                            Text(currencyLabel(code), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickCurrency = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun VehicleCard(
    row: PreviewVehicle,
    state: ImportUiState.Preview,
    onChoice: (String, MapChoice) -> Unit,
    onName: (String, String) -> Unit,
    onDistance: (String, DistanceUnit) -> Unit,
    onVolume: (String, VolumeUnit) -> Unit,
) {
    val key = row.parsed.key
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.parsed.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.import_vehicle_counts, row.parsed.energy.size, row.parsed.services.size, row.parsed.expenses.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                row.parsed.dateRange?.let { (a, b) -> stringResource(R.string.import_date_range, day(a), day(b)) }
                    ?: stringResource(R.string.import_no_entries),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val detectedDistance = row.parsed.distanceUnit
            val detectedVolume = row.parsed.volumeUnit
            if (detectedDistance != null && detectedVolume != null) {
                Text(
                    stringResource(R.string.import_units_detected, stringResource(detectedDistance.label()), stringResource(detectedVolume.label())),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            MapPicker(row, state, onChoice)

            if (row.choice == MapChoice.New) {
                OutlinedTextField(
                    value = row.name, onValueChange = { onName(key, it) }, singleLine = true,
                    label = { Text(stringResource(R.string.import_vehicle_name)) }, modifier = Modifier.fillMaxWidth(),
                )
                if (detectedDistance == null || detectedVolume == null) {
                    Text(stringResource(R.string.import_units_ask), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.import_distance_unit), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DistanceUnit.entries.forEach {
                            FilterChip(selected = row.distance == it, onClick = { onDistance(key, it) }, label = { Text(stringResource(it.label())) })
                        }
                    }
                    Text(stringResource(R.string.import_volume_unit), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(VolumeUnit.L, VolumeUnit.US_GAL, VolumeUnit.UK_GAL).forEach {
                            FilterChip(selected = row.volume == it, onClick = { onVolume(key, it) }, label = { Text(stringResource(it.label())) })
                        }
                    }
                }
            } else if (row.choice is MapChoice.Existing) {
                Text(stringResource(R.string.import_existing_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MapPicker(row: PreviewVehicle, state: ImportUiState.Preview, onChoice: (String, MapChoice) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when (val c = row.choice) {
        MapChoice.New -> stringResource(R.string.import_map_new)
        MapChoice.Skip -> stringResource(R.string.import_map_skip)
        is MapChoice.Existing -> state.existing.firstOrNull { it.id == c.vehicleId }?.name.orEmpty()
    }
    Box {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) { Text("${stringResource(R.string.import_map_to)}: $label") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.import_map_new)) }, onClick = { open = false; onChoice(row.parsed.key, MapChoice.New) })
            state.existing.forEach { v ->
                DropdownMenuItem(text = { Text(v.name) }, onClick = { open = false; onChoice(row.parsed.key, MapChoice.Existing(v.id)) })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.import_map_skip)) }, onClick = { open = false; onChoice(row.parsed.key, MapChoice.Skip) })
        }
    }
}

@Composable
private fun ResultView(result: ImportResult, onOpenPro: () -> Unit, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.import_report_copied)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // A plain-text report for support or the user's own notes; it contains row positions and reasons, never row contents.
    val summary = stringResource(R.string.import_result_summary, result.fillUps, result.services, result.expenses)
    val skipped = stringResource(R.string.import_result_skipped, result.skippedDuplicates)
    val rowFormat = stringResource(R.string.import_result_row)
    val report = buildString {
        appendLine(summary)
        appendLine(skipped)
        result.unreadable.forEach { appendLine(String.format(rowFormat, it.line, it.file, it.reason)) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_result_title), style = MaterialTheme.typography.titleLarge)
                if (result.beta) BetaBadge()
            }
        }
        item { Text(stringResource(R.string.import_result_summary, result.fillUps, result.services, result.expenses), style = MaterialTheme.typography.bodyLarge) }
        item { Text(stringResource(R.string.import_result_skipped, result.skippedDuplicates), style = MaterialTheme.typography.bodyLarge) }
        if (result.unreadable.isNotEmpty()) {
            item { Text(stringResource(R.string.import_result_unreadable, result.unreadable.size), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
            items(result.unreadable) {
                Text(
                    stringResource(R.string.import_result_row, it.line, it.file, it.reason),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (result.archivedVehicles > 0) {
            item { Text(stringResource(R.string.import_result_archived, result.archivedVehicles), style = MaterialTheme.typography.bodyMedium) }
            item { OutlinedButton(onClick = onOpenPro, Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_see_pro)) } }
        }
        if (result.unreadable.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(report))
                        scope.launch { snackbar.showSnackbar(copiedMessage) }
                    },
                    Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.import_copy_report)) }
            }
        }
        item { Button(onClick = onDone, Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_done)) } }
        item { SnackbarHost(snackbar) }
    }
}

private fun currencyLabel(code: String): String =
    runCatching { "$code — ${Currency.getInstance(code).displayName}" }.getOrDefault(code)
