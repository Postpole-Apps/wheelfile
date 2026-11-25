package com.postpole.wheelfile.ui.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.QuietAction
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.backup.VehiclePdfExporter
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
import java.io.File
import javax.inject.Inject

sealed interface PdfEvent {
    /** The report is ready: hand [uri] to the share sheet. */
    data class Ready(val uri: Uri) : PdfEvent
    data object Failed : PdfEvent

    /** The report is Pro only: the UI opens the paywall. Nothing was built. */
    data object ProRequired : PdfEvent
}

/** Builds the Pro vehicle history PDF in the cache folder and offers it to the share sheet. */
@HiltViewModel
class PdfReportViewModel internal constructor(
    private val context: Context,
    private val exporter: VehiclePdfExporter,
    private val adsPolicy: AdsPolicy,
    private val isPro: () -> Boolean,
) : ViewModel() {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        exporter: VehiclePdfExporter,
        adsPolicy: AdsPolicy,
        billing: BillingRepository,
    ) : this(context, exporter, adsPolicy, { billing.isPro.value })

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<PdfEvent>(Channel.BUFFERED)
    val events: Flow<PdfEvent> = _events.receiveAsFlow()

    fun share(vehicleId: Long) {
        // The UI hides the button from free users, but the guard lives here too: a stale screen or a cached Pro flag
        // that Play has since cleared must never build the report.
        if (!isPro()) {
            _events.trySend(PdfEvent.ProRequired)
            return
        }
        if (_busy.value) return
        adsPolicy.onQuietAction(QuietAction.PDF_SHARE)
        _busy.value = true
        viewModelScope.launch {
            val uri = try {
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() } // only the latest report is kept
                    val file = File(dir, "vehicle-history-$vehicleId.pdf")
                    val pages = file.outputStream().use { exporter.export(vehicleId, it) }
                    if (pages == null) null else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _events.trySend(if (uri != null) PdfEvent.Ready(uri) else PdfEvent.Failed)
            _busy.value = false
        }
    }
}
