package com.postpole.wheelfile.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.QuietAction
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.analytics.AnalyticsParams
import com.postpole.wheelfile.data.db.BackupDao
import com.postpole.wheelfile.export.BackupException
import com.postpole.wheelfile.export.BackupService
import com.postpole.wheelfile.export.CsvExporter
import com.postpole.wheelfile.export.RestoreSummary
import com.postpole.wheelfile.export.snapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

sealed interface DataTransferEvent {
    data object CsvSaved : DataTransferEvent
    data object BackupSaved : DataTransferEvent
    data object SaveFailed : DataTransferEvent
    data class Restored(val summary: RestoreSummary) : DataTransferEvent
    data class RestoreFailed(val reason: String?) : DataTransferEvent
}

/**
 * Settings: CSV export, `.wheelfile` backup and restore through the Storage Access Framework. All free on every tier,
 * and none of them is followed by an interstitial ([QuietAction]).
 */
@HiltViewModel
class DataTransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupService,
    private val dao: BackupDao,
    private val adsPolicy: AdsPolicy,
    private val analytics: Analytics,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<DataTransferEvent>(Channel.BUFFERED)
    val events: Flow<DataTransferEvent> = _events.receiveAsFlow()

    fun exportCsv(target: Uri?) = run(target) { out ->
        val bytes = CsvExporter.export(dao.snapshot())
        out.write(bytes)
        adsPolicy.onQuietAction(QuietAction.EXPORT)
        analytics.logEvent(AnalyticsEvents.CSV_EXPORTED)
        DataTransferEvent.CsvSaved
    }

    fun backup(target: Uri?) = run(target) { out ->
        val manifest = backup.write(out)
        adsPolicy.onQuietAction(QuietAction.BACKUP)
        analytics.logEvent(AnalyticsEvents.BACKUP_EXPORTED, mapOf(AnalyticsParams.ITEM_COUNT to manifest.vehicleCount))
        DataTransferEvent.BackupSaved
    }

    private fun run(target: Uri?, block: suspend (java.io.OutputStream) -> DataTransferEvent) {
        if (target == null || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val event = try {
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException("no stream")
                    out.use { block(it) }
                }
            } catch (e: IOException) {
                DataTransferEvent.SaveFailed
            } catch (e: SecurityException) {
                DataTransferEvent.SaveFailed
            }
            _busy.value = false
            _events.trySend(event)
        }
    }

    fun restore(source: Uri?) {
        if (source == null || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val event = try {
                val summary = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(source) ?: throw IOException("no stream")
                    input.use { backup.restore(it) }
                }
                adsPolicy.onQuietAction(QuietAction.RESTORE)
                analytics.logEvent(AnalyticsEvents.BACKUP_IMPORTED, mapOf(AnalyticsParams.ITEM_COUNT to summary.vehicles))
                DataTransferEvent.Restored(summary)
            } catch (e: BackupException) {
                DataTransferEvent.RestoreFailed(e.message)
            } catch (e: IOException) {
                DataTransferEvent.RestoreFailed(null)
            } catch (e: SecurityException) {
                DataTransferEvent.RestoreFailed(null)
            }
            _busy.value = false
            _events.trySend(event)
        }
    }
}
