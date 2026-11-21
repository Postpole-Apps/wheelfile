package com.postpole.wheelfile.ui.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.R
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.QuietAction
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.data.db.EnergyLogDao
import com.postpole.wheelfile.data.db.ExpenseDao
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.ServiceRecordDao
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.data.db.VehicleDocumentDao
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import com.postpole.wheelfile.export.PdfLabels
import com.postpole.wheelfile.export.PdfReportBuilder
import com.postpole.wheelfile.export.VehicleReportData
import com.postpole.wheelfile.ui.common.label
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class PdfUiState(
    val vehicleName: String? = null,
    val includeAttachments: Boolean = true,
    val creating: Boolean = false,
)

sealed interface PdfEvent {
    data class Share(val uri: Uri) : PdfEvent
    data object Failed : PdfEvent
}

@HiltViewModel
class PdfReportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
    private val vehicles: VehicleDao,
    private val energy: EnergyLogDao,
    private val services: ServiceRecordDao,
    private val expenses: ExpenseDao,
    private val documents: VehicleDocumentDao,
    private val attachments: AttachmentDao,
    private val billing: BillingRepository,
    private val adsPolicy: AdsPolicy,
    private val analytics: Analytics,
) : ViewModel() {
    private val vehicleId: Long = checkNotNull(savedState["vehicleId"])

    val isPro: StateFlow<Boolean> = billing.isPro

    private val _state = MutableStateFlow(PdfUiState())
    val state: StateFlow<PdfUiState> = _state.asStateFlow()

    private val _events = Channel<PdfEvent>(Channel.BUFFERED)
    val events: Flow<PdfEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(vehicleName = vehicles.get(vehicleId)?.name) } }
    }

    fun setIncludeAttachments(include: Boolean) = _state.update { it.copy(includeAttachments = include) }

    /** Builds the PDF in the cache folder and hands the share sheet a FileProvider URI. Pro only. */
    fun create() {
        if (!billing.isPro.value || _state.value.creating) return
        _state.update { it.copy(creating = true) }
        val includeAttachments = _state.value.includeAttachments
        viewModelScope.launch {
            val event = try {
                val file = withContext(Dispatchers.IO) { build(includeAttachments) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                adsPolicy.onQuietAction(QuietAction.PDF_SHARE)
                analytics.logEvent(AnalyticsEvents.PDF_REPORT_SHARED)
                PdfEvent.Share(uri)
            } catch (e: IOException) {
                PdfEvent.Failed
            } catch (e: IllegalStateException) {
                analytics.recordNonFatal(e)
                PdfEvent.Failed
            }
            _state.update { it.copy(creating = false) }
            _events.trySend(event)
        }
    }

    private suspend fun build(includeAttachments: Boolean): File {
        val vehicle = vehicles.get(vehicleId) ?: throw IOException("vehicle gone")
        val logs = energy.getAll(vehicleId)
        val serviceRows = services.getForVehicle(vehicleId)
        val expenseRows = expenses.getForVehicle(vehicleId)
        val docs = documents.getAll().filter { it.vehicleId == vehicleId }
        val serviceIds = serviceRows.map { it.id }.toSet()
        val docIds = docs.map { it.id }.toSet()
        val files = attachments.getAll().filter {
            (it.ownerType == OwnerType.SERVICE && it.ownerId in serviceIds) || (it.ownerType == OwnerType.DOCUMENT && it.ownerId in docIds)
        }
        val data = VehicleReportData(
            vehicle, logs, serviceRows, expenseRows, docs, files,
            VehicleStatsCalculator.compute(vehicle, logs, serviceRows, expenseRows),
        )
        val dir = File(context.cacheDir, "reports").also {
            it.mkdirs()
            it.listFiles()?.forEach(File::delete) // only the latest report is kept in the cache
        }
        val target = File(dir, "${slug(vehicle.name)}-history-${java.time.LocalDate.now()}.pdf")
        target.outputStream().use { PdfReportBuilder(labels(vehicle)).write(data, it, includeAttachments) }
        return target
    }

    private fun slug(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "vehicle" }

    private fun labels(v: Vehicle): PdfLabels {
        fun s(id: Int) = context.getString(id)
        val distance = s(v.distanceUnit.label())
        return PdfLabels(
            title = s(R.string.pdf_title),
            generated = { context.getString(R.string.pdf_generated, it) },
            vehicleSection = s(R.string.pdf_vehicle), plate = s(R.string.pdf_plate), vin = s(R.string.pdf_vin),
            makeModelYear = s(R.string.pdf_make_model_year), odometer = s(R.string.pdf_odometer),
            unitsLine = context.getString(R.string.pdf_units, distance, s(v.volumeUnit.label()), v.currency),
            costsSection = s(R.string.pdf_costs), fuelOrEnergy = s(R.string.pdf_fuel_energy), service = s(R.string.pdf_service),
            expenses = s(R.string.pdf_expenses), total = s(R.string.pdf_total),
            costPerDistance = { context.getString(R.string.pdf_cost_per_distance, it, distance) },
            economySection = s(R.string.pdf_economy), economyAverage = s(R.string.pdf_economy_average),
            economyEmpty = s(R.string.pdf_economy_empty), servicesSection = s(R.string.pdf_services),
            servicesEmpty = s(R.string.pdf_services_empty), colDate = s(R.string.pdf_col_date),
            colOdometer = s(R.string.pdf_col_odometer), colItems = s(R.string.pdf_col_items), colCost = s(R.string.pdf_col_cost),
            attachmentsSection = s(R.string.pdf_attachments),
            attachmentsLine = { title, n -> context.getString(R.string.pdf_attachments_line, title, n) },
            footer = { context.getString(R.string.pdf_footer, it) },
            economyUnit = { context.getString(it.label()) },
            distanceUnit = distance,
            serviceFallback = s(R.string.pdf_service_fallback),
        )
    }
}
