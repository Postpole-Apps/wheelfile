package com.postpole.wheelfile.ui.importer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.QuietAction
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.analytics.AnalyticsParams
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.units.LocaleDefaults
import com.postpole.wheelfile.importer.ImportException
import com.postpole.wheelfile.importer.ImportInput
import com.postpole.wheelfile.importer.ImportParser
import com.postpole.wheelfile.importer.ImportPlan
import com.postpole.wheelfile.importer.ImportResult
import com.postpole.wheelfile.importer.ImportService
import com.postpole.wheelfile.importer.ParsedFile
import com.postpole.wheelfile.importer.ParsedVehicle
import com.postpole.wheelfile.importer.VehicleTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/** What the user chose for one vehicle of the file. */
sealed interface MapChoice {
    data object New : MapChoice
    data class Existing(val vehicleId: Long) : MapChoice
    data object Skip : MapChoice
}

data class PreviewVehicle(
    val parsed: ParsedVehicle,
    val choice: MapChoice,
    val name: String,
    val distance: DistanceUnit,
    val volume: VolumeUnit,
)

enum class ImportFailure { UNRECOGNISED, TOO_LARGE, UNREADABLE }

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Reading : ImportUiState
    data class Failed(val reason: ImportFailure) : ImportUiState
    data class Preview(
        val file: ParsedFile,
        val vehicles: List<PreviewVehicle>,
        val existing: List<Vehicle>,
        val currency: String,
        /** True when importing the new vehicles would go past the free cap, so some land archived. */
        val willArchive: Boolean,
    ) : ImportUiState {
        val canImport: Boolean get() = vehicles.any { it.choice != MapChoice.Skip }
    }
    data object Importing : ImportUiState
    data class Done(val result: ImportResult) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: ImportService,
    private val vehicles: VehicleDao,
    private val settings: SettingsRepository,
    private val analytics: Analytics,
    private val adsPolicy: AdsPolicy,
    private val billing: com.postpole.wheelfile.billing.BillingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun reset() {
        _state.value = ImportUiState.Idle
    }

    /** Reads and parses the picked file off the main thread, then shows the preview. */
    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        _state.value = ImportUiState.Reading
        viewModelScope.launch {
            _state.value = try {
                val parsed = withContext(Dispatchers.IO) { read(uri) }
                if (parsed.vehicles.isEmpty()) {
                    ImportUiState.Failed(ImportFailure.UNRECOGNISED)
                } else {
                    preview(parsed)
                }
            } catch (e: ImportException) {
                ImportUiState.Failed(if (e.message?.contains("too large") == true) ImportFailure.TOO_LARGE else ImportFailure.UNRECOGNISED)
            } catch (e: java.io.IOException) {
                ImportUiState.Failed(ImportFailure.UNREADABLE)
            } catch (e: SecurityException) {
                ImportUiState.Failed(ImportFailure.UNREADABLE)
            }
        }
    }

    private fun read(uri: Uri): ParsedFile {
        val stream = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("no stream")
        val bytes = stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                out.write(chunk, 0, n)
                if (out.size() > MAX_FILE_BYTES) throw ImportException("The file is too large to import.")
            }
            out.toByteArray()
        }
        val name = uri.lastPathSegment ?: "import"
        return ImportParser.parse(ImportInput.fromBytes(name, bytes))
    }

    private suspend fun preview(file: ParsedFile): ImportUiState.Preview {
        val existing = vehicles.getAll().filter { !it.archived }
        val currency = settings.defaultCurrency.first()
        val defaults = LocaleDefaults.forLocale(Locale.getDefault())
        val rows = file.vehicles.map { v ->
            val match = existing.firstOrNull { it.name.equals(v.name, ignoreCase = true) }
            PreviewVehicle(
                parsed = v,
                choice = match?.let { MapChoice.Existing(it.id) } ?: MapChoice.New,
                name = v.name,
                distance = v.distanceUnit ?: defaults.distance,
                volume = v.volumeUnit ?: defaults.volume,
            )
        }
        return ImportUiState.Preview(file, rows, existing, currency, willArchive = wouldArchive(rows, existing.size))
    }

    private fun wouldArchive(rows: List<PreviewVehicle>, activeNow: Int): Boolean {
        if (billing.isPro.value) return false
        val newCount = rows.count { it.choice == MapChoice.New }
        return activeNow + newCount > com.postpole.wheelfile.domain.vehicle.VehicleLimit.FREE_ACTIVE_VEHICLES
    }

    private fun updatePreview(change: (ImportUiState.Preview) -> ImportUiState.Preview) {
        _state.update { s -> if (s is ImportUiState.Preview) change(s).let { it.copy(willArchive = wouldArchive(it.vehicles, it.existing.size)) } else s }
    }

    private fun edit(key: String, change: (PreviewVehicle) -> PreviewVehicle) = updatePreview { p ->
        p.copy(vehicles = p.vehicles.map { if (it.parsed.key == key) change(it) else it })
    }

    fun setChoice(key: String, choice: MapChoice) = edit(key) { it.copy(choice = choice) }
    fun setName(key: String, name: String) = edit(key) { it.copy(name = name) }
    fun setDistance(key: String, unit: DistanceUnit) = edit(key) { it.copy(distance = unit) }
    fun setVolume(key: String, unit: VolumeUnit) = edit(key) { it.copy(volume = unit) }
    fun setCurrency(code: String) = updatePreview { it.copy(currency = code) }

    fun import() {
        val preview = _state.value as? ImportUiState.Preview ?: return
        _state.value = ImportUiState.Importing
        viewModelScope.launch {
            val plan = ImportPlan(
                targets = preview.vehicles.associate { row ->
                    row.parsed.key to when (val c = row.choice) {
                        MapChoice.Skip -> VehicleTarget.Skip
                        is MapChoice.Existing -> VehicleTarget.Existing(c.vehicleId)
                        MapChoice.New -> VehicleTarget.New(row.name.trim().ifBlank { row.parsed.name }, row.distance, row.volume)
                    }
                },
                currency = preview.currency,
            )
            try {
                val result = service.import(preview.file, plan)
                // Like export, backup and restore: no interstitial after an import.
                adsPolicy.onQuietAction(QuietAction.RESTORE)
                analytics.logEvent(
                    AnalyticsEvents.IMPORT_COMPLETED,
                    // Counts only: no file names, no source app names, no vehicle data.
                    mapOf(AnalyticsParams.ITEM_COUNT to result.imported),
                )
                _state.value = ImportUiState.Done(result)
            } catch (e: Exception) {
                analytics.recordNonFatal(e)
                _state.value = ImportUiState.Failed(ImportFailure.UNREADABLE)
            }
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 100 * 1024 * 1024
    }
}
