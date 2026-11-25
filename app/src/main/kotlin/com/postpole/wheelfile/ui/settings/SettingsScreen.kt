package com.postpole.wheelfile.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.postpole.wheelfile.ui.common.findActivity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.BuildConfig
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.ThemeMode
import java.util.Currency

const val DELETE_ALL_TAG = "settings-delete-all"
const val DELETE_ALL_CONFIRM_TAG = "settings-delete-all-confirm"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenAdPreview: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    /** After Delete all data: leave Settings and return to onboarding. */
    onDataDeleted: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    dataViewModel: DataTransferViewModel = hiltViewModel(),
) {
    val dataBusy by dataViewModel.busy.collectAsStateWithLifecycle()
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        dataViewModel.exportCsv(it)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        dataViewModel.backup(it)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        pendingRestore = it?.toString()
    }
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val restoring by viewModel.restoring.collectAsStateWithLifecycle()
    val privacyOptionsRequired by viewModel.privacyOptionsRequired.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val currency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val quiet by viewModel.quietHours.collectAsStateWithLifecycle()
    var hourPicker by rememberSaveable { mutableStateOf<Boolean?>(null) } // true = start, false = end
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showCurrency by rememberSaveable { mutableStateOf(false) }

    val messages = rememberEventMessages()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(messages(it)) }
    }
    val dataMessages = rememberDataMessages()
    LaunchedEffect(dataViewModel) {
        dataViewModel.events.collect {
            if (it == DataTransferEvent.DataDeleted) onDataDeleted() else snackbarHostState.showSnackbar(dataMessages(it))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(R.string.settings_section_appearance)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                supportingContent = { Text(stringResource(themeMode.label())) },
                modifier = Modifier.clickable { showTheme = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_currency)) },
                supportingContent = { Text(currency?.let(::currencyLabel) ?: "") },
                modifier = Modifier.clickable { showCurrency = true },
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_reminders)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reminders)) },
                supportingContent = { Text(stringResource(R.string.settings_reminders_summary)) },
                modifier = Modifier.clickable(onClick = onOpenReminders),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_quiet_hours)) },
                supportingContent = { Text(stringResource(R.string.settings_quiet_hours_summary)) },
                trailingContent = {
                    Switch(checked = quiet.enabled, onCheckedChange = { viewModel.setQuietHours(quiet.copy(enabled = it)) })
                },
            )
            if (quiet.enabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_quiet_from)) },
                    supportingContent = { Text(hourLabel(quiet.startHour)) },
                    modifier = Modifier.clickable { hourPicker = true },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_quiet_until)) },
                    supportingContent = { Text(hourLabel(quiet.endHour)) },
                    modifier = Modifier.clickable { hourPicker = false },
                )
            }
            HorizontalDivider()

            // Import, export, backup and restore are free on every tier and never show an ad.
            SectionHeader(R.string.settings_section_data)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import)) },
                supportingContent = { Text(stringResource(R.string.settings_import_summary)) },
                modifier = Modifier.clickable(onClick = onOpenImport),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export_csv)) },
                supportingContent = { Text(stringResource(R.string.settings_export_csv_summary)) },
                modifier = Modifier.clickable(enabled = !dataBusy) { exportLauncher.launch(dataViewModel.csvFileName()) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_backup)) },
                supportingContent = { Text(stringResource(R.string.settings_backup_summary)) },
                modifier = Modifier.clickable(enabled = !dataBusy) { backupLauncher.launch(dataViewModel.backupFileName()) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_restore_backup)) },
                supportingContent = { Text(stringResource(R.string.settings_restore_backup_summary)) },
                trailingContent = if (dataBusy) {
                    { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
                modifier = Modifier.clickable(enabled = !dataBusy) { restoreLauncher.launch(arrayOf("*/*")) },
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.settings_delete_all), color = MaterialTheme.colorScheme.error)
                },
                supportingContent = { Text(stringResource(R.string.settings_delete_all_summary)) },
                modifier = Modifier.testTag(DELETE_ALL_TAG).clickable(enabled = !dataBusy) { confirmDelete = true },
            )
            Text(
                stringResource(R.string.settings_transfer_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_pro)
            if (isPro) {
                ListItem(headlineContent = { Text(stringResource(R.string.pro_active)) })
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_pro_entry)) },
                    modifier = Modifier.clickable(onClick = onOpenPro),
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.pro_restore)) },
                supportingContent = if (restoring) {
                    { Text(stringResource(R.string.restore_in_progress)) }
                } else {
                    null
                },
                trailingContent = if (restoring) {
                    { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
                modifier = Modifier.clickable(enabled = !restoring, onClick = viewModel::restore),
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_privacy)
            if (privacyOptionsRequired) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy_options)) },
                    supportingContent = { Text(stringResource(R.string.settings_privacy_options_summary)) },
                    // Needs the current Activity to host the UMP form (Spoonfile #43: the tap did nothing).
                    modifier = Modifier.clickable { viewModel.openPrivacyOptions(context.findActivity()) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
                modifier = Modifier.clickable {
                    context.openLink(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url))))
                },
            )
            HorizontalDivider()

            SectionHeader(R.string.settings_section_about)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
            if (BuildConfig.DEBUG) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_debug_ad_preview)) },
                    modifier = Modifier.clickable(onClick = onOpenAdPreview),
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_feedback)) },
                supportingContent = { Text(stringResource(R.string.support_email)) },
                modifier = Modifier.clickable {
                    val email = context.getString(R.string.support_email)
                    context.openLink(
                        Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                context.getString(R.string.settings_feedback_subject, context.getString(R.string.app_name)),
                            )
                        },
                    )
                },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.data_delete_confirm_title)) },
            text = { Text(stringResource(R.string.data_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        dataViewModel.deleteAll()
                    },
                    modifier = Modifier.testTag(DELETE_ALL_CONFIRM_TAG),
                ) { Text(stringResource(R.string.data_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.data_restore_confirm_title)) },
            text = { Text(stringResource(R.string.data_restore_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        dataViewModel.restore(Uri.parse(uri))
                    },
                ) { Text(stringResource(R.string.data_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    hourPicker?.let { isStart ->
        AlertDialog(
            onDismissRequest = { hourPicker = null },
            title = { Text(stringResource(if (isStart) R.string.settings_quiet_from else R.string.settings_quiet_until)) },
            text = {
                LazyColumn {
                    items((0..23).toList(), key = { it }) { hour ->
                        val selected = hour == if (isStart) quiet.startHour else quiet.endHour
                        Row(
                            Modifier.fillMaxWidthClickable {
                                viewModel.setQuietHours(if (isStart) quiet.copy(startHour = hour) else quiet.copy(endHour = hour))
                                hourPicker = null
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(hourLabel(hour), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { hourPicker = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showTheme) {
        ChoiceDialog(
            title = R.string.settings_theme,
            onDismiss = { showTheme = false },
        ) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidthClickable {
                        viewModel.setThemeMode(mode)
                        showTheme = false
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == themeMode, onClick = null)
                    Text(stringResource(mode.label()), Modifier.padding(start = 12.dp))
                }
            }
        }
    }

    if (showCurrency) {
        val codes = remember { Currency.getAvailableCurrencies().map { it.currencyCode }.sorted() }
        AlertDialog(
            onDismissRequest = { showCurrency = false },
            title = { Text(stringResource(R.string.settings_currency)) },
            text = {
                LazyColumn {
                    items(codes, key = { it }) { code ->
                        Row(
                            Modifier.fillMaxWidthClickable {
                                viewModel.setDefaultCurrency(code)
                                showCurrency = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = code == currency, onClick = null)
                            Text(currencyLabel(code), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrency = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ChoiceDialog(title: Int, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Column { content() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun Modifier.fillMaxWidthClickable(onClick: () -> Unit): Modifier =
    fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** An hour of the day in the device's time format (for example 21:00 or 9 PM). */
private fun hourLabel(hour: Int): String =
    java.time.LocalTime.of(hour, 0).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

private fun currencyLabel(code: String): String =
    runCatching { "$code — ${Currency.getInstance(code).displayName}" }.getOrDefault(code)

private fun Context.openLink(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser / mail app installed: nothing sensible to do.
    }
}

/** Resolves [SettingsEvent]s to localized text. */
@Composable
private fun rememberEventMessages(): (SettingsEvent) -> String {
    val restored = stringResource(R.string.restore_done)
    val nothing = stringResource(R.string.restore_none)
    val restoreFailed = stringResource(R.string.restore_failed)
    val privacyUnavailable = stringResource(R.string.privacy_options_unavailable)
    return { event ->
        when (event) {
            SettingsEvent.Restored -> restored
            SettingsEvent.NothingToRestore -> nothing
            SettingsEvent.RestoreFailed -> restoreFailed
            SettingsEvent.PrivacyOptionsUnavailable -> privacyUnavailable
        }
    }
}

@Composable
private fun rememberDataMessages(): (DataTransferEvent) -> String {
    val exported = stringResource(R.string.data_export_done)
    val exportFailed = stringResource(R.string.data_export_failed)
    val backedUp = stringResource(R.string.data_backup_done)
    val backupFailed = stringResource(R.string.data_backup_failed)
    val restored = stringResource(R.string.data_restore_done)
    val restoreFailed = stringResource(R.string.data_restore_failed)
    val deleteFailed = stringResource(R.string.data_delete_failed)
    val context = LocalContext.current
    return { event ->
        when (event) {
            DataTransferEvent.ExportDone -> exported
            DataTransferEvent.ExportFailed -> exportFailed
            DataTransferEvent.BackupDone -> backedUp
            DataTransferEvent.BackupFailed -> backupFailed
            is DataTransferEvent.Restored ->
                if (event.archivedForCap > 0) context.getString(R.string.data_restore_done_archived, event.archivedForCap) else restored
            DataTransferEvent.RestoreFailed -> restoreFailed
            DataTransferEvent.DataDeleted -> ""
            DataTransferEvent.DeleteFailed -> deleteFailed
        }
    }
}
