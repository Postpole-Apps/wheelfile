package com.postpole.wheelfile.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.QuietAction
import com.postpole.wheelfile.data.backup.BackupManager
import com.postpole.wheelfile.data.backup.CsvExporter
import com.postpole.wheelfile.data.backup.RestoreOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
import java.time.LocalDate
import javax.inject.Inject

sealed interface DataTransferEvent {
    data object ExportDone : DataTransferEvent
    data object ExportFailed : DataTransferEvent
    data object BackupDone : DataTransferEvent
    data object BackupFailed : DataTransferEvent
    data class Restored(val archivedForCap: Int) : DataTransferEvent
    data object RestoreFailed : DataTransferEvent
}

/** CSV export, `.wheelfile` backup and restore through the Storage Access Framework. All free, on every tier. */
@HiltViewModel
class DataTransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val csv: CsvExporter,
    private val backups: BackupManager,
    private val adsPolicy: AdsPolicy,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<DataTransferEvent>(Channel.BUFFERED)
    val events: Flow<DataTransferEvent> = _events.receiveAsFlow()

    fun csvFileName() = "wheelfile-export-${LocalDate.now()}.zip"
    fun backupFileName() = "wheelfile-backup-${LocalDate.now()}.${BackupManager.EXTENSION}"

    fun exportCsv(uri: Uri?) = run(uri, QuietAction.EXPORT, DataTransferEvent.ExportDone, DataTransferEvent.ExportFailed) { out ->
        csv.export(out)
    }

    fun backup(uri: Uri?) = run(uri, QuietAction.BACKUP, DataTransferEvent.BackupDone, DataTransferEvent.BackupFailed) { out ->
        backups.backup(out)
    }

    private fun run(
        uri: Uri?,
        quiet: QuietAction,
        done: DataTransferEvent,
        failed: DataTransferEvent,
        block: suspend (java.io.OutputStream) -> Unit,
    ) {
        if (uri == null || _busy.value) return
        adsPolicy.onQuietAction(quiet) // no interstitial for the rest of the session after an export or backup
        _busy.value = true
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no stream")
                    out.use { block(it) }
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            _events.trySend(if (ok) done else failed)
            _busy.value = false
        }
    }

    fun restore(uri: Uri?) {
        if (uri == null || _busy.value) return
        adsPolicy.onQuietAction(QuietAction.RESTORE)
        _busy.value = true
        viewModelScope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { backups.restore(it) } ?: RestoreOutcome.Invalid
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                RestoreOutcome.Invalid
            }
            _events.trySend(
                when (outcome) {
                    is RestoreOutcome.Restored -> DataTransferEvent.Restored(outcome.archivedForCap)
                    RestoreOutcome.Invalid -> DataTransferEvent.RestoreFailed
                },
            )
            _busy.value = false
        }
    }
}
