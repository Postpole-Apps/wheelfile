package com.postpole.wheelfile.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.BuildConfig
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.ThemeMode
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val currency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val restoring by viewModel.restoring.collectAsStateWithLifecycle()
    val privacyOptionsRequired by viewModel.privacyOptionsRequired.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showCurrency by rememberSaveable { mutableStateOf(false) }

    val messages = rememberEventMessages()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(messages(it)) }
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

            SectionHeader(R.string.settings_section_pro)
            if (isPro) {
                ListItem(headlineContent = { Text(stringResource(R.string.pro_active)) })
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.upgrade_to_pro)) },
                    modifier = Modifier.clickable(onClick = onOpenPro),
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.pro_restore)) },
                trailingContent = {
                    if (restoring) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                },
                modifier = Modifier.clickable(enabled = !restoring, onClick = viewModel::restore),
            )
            HorizontalDivider()

            if (privacyOptionsRequired) {
                SectionHeader(R.string.settings_section_privacy)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy_options)) },
                    modifier = Modifier.clickable {
                        // The form needs the current Activity (not just any Context): unwrap it.
                        val activity = context.findActivity()
                        if (activity != null) viewModel.openPrivacyOptions(activity) else viewModel.onPrivacyOptionsUnavailable()
                    },
                )
                HorizontalDivider()
            }

            SectionHeader(R.string.settings_section_about)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy_policy)) },
                modifier = Modifier.clickable {
                    context.openLink(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url))))
                },
            )
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

private fun currencyLabel(code: String): String =
    runCatching { "$code — ${Currency.getInstance(code).displayName}" }.getOrDefault(code)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
    val none = stringResource(R.string.restore_none)
    val restoreFailed = stringResource(R.string.restore_failed)
    val privacyFailed = stringResource(R.string.privacy_options_unavailable)
    return { event ->
        when (event) {
            SettingsEvent.PurchasesRestored -> restored
            SettingsEvent.NoPurchasesFound -> none
            SettingsEvent.RestoreFailed -> restoreFailed
            SettingsEvent.PrivacyOptionsFailed -> privacyFailed
        }
    }
}
