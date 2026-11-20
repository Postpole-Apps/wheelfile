package com.postpole.template.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.template.analytics.Analytics
import com.postpole.template.analytics.AnalyticsEvents
import com.postpole.template.analytics.AnalyticsParams
import com.postpole.template.billing.BillingRepository
import com.postpole.template.data.BackupRepository
import com.postpole.template.data.InvalidBackupException
import com.postpole.template.data.SettingsRepository
import com.postpole.template.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One-shot results shown as a snackbar. */
sealed interface SettingsEvent {
    data class Exported(val count: Int) : SettingsEvent
    data object ExportFailed : SettingsEvent
    data class Imported(val count: Int) : SettingsEvent
    data object ImportInvalid : SettingsEvent
    data object ImportFailed : SettingsEvent
    data object RestoreChecked : SettingsEvent
    data object RestoreFailed : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    private val analytics: Analytics,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = billing.isPro

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val defaultCurrency: StateFlow<String?> = settings.defaultCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch { settings.setDefaultCurrency(code) }
    }

    fun restore() {
        viewModelScope.launch {
            _events.trySend(if (billing.restore()) SettingsEvent.RestoreChecked else SettingsEvent.RestoreFailed)
        }
    }

    fun exportJson(resolver: ContentResolver, uri: Uri) = export(resolver, uri, "json") { backup.exportJson(it) }

    fun exportCsv(resolver: ContentResolver, uri: Uri) = export(resolver, uri, "csv") { backup.exportItemsCsv(it) }

    private fun export(
        resolver: ContentResolver,
        uri: Uri,
        format: String,
        write: suspend (java.io.OutputStream) -> Int,
    ) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val out = resolver.openOutputStream(uri, "wt") ?: error("Could not open $uri for writing")
                    out.use { write(it) }
                }
                if (format == "json") {
                    analytics.logEvent(
                        AnalyticsEvents.BACKUP_EXPORTED,
                        mapOf(AnalyticsParams.FORMAT to format, AnalyticsParams.ITEM_COUNT to count),
                    )
                }
                _events.trySend(SettingsEvent.Exported(count))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analytics.recordNonFatal(BackupException("Export ($format) failed", e))
                _events.trySend(SettingsEvent.ExportFailed)
            }
        }
    }

    fun importJson(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: error("Could not open $uri for reading")
                    input.use { backup.importJson(it) }
                }
                analytics.logEvent(AnalyticsEvents.BACKUP_IMPORTED, mapOf(AnalyticsParams.ITEM_COUNT to count))
                _events.trySend(SettingsEvent.Imported(count))
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidBackupException) {
                // User picked a wrong/newer file: expected, not a crash-worthy event.
                _events.trySend(SettingsEvent.ImportInvalid)
            } catch (e: Exception) {
                analytics.recordNonFatal(BackupException("Import failed", e))
                _events.trySend(SettingsEvent.ImportFailed)
            }
        }
    }
}

/** Wraps a backup I/O failure so Crashlytics groups them as one non-fatal type. */
class BackupException(message: String, cause: Throwable) : Exception(message, cause)
