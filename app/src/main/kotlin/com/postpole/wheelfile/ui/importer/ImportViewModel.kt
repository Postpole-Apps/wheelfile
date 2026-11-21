package com.postpole.wheelfile.ui.importer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.domain.units.LocaleDefaults
import com.postpole.wheelfile.importer.FormatDetector
import com.postpole.wheelfile.importer.ImportFile
import com.postpole.wheelfile.importer.ImportOptions
import com.postpole.wheelfile.importer.ImportResult
import com.postpole.wheelfile.importer.ImportService
import com.postpole.wheelfile.importer.ParsedImport
import com.postpole.wheelfile.importer.VehicleTarget
import com.postpole.wheelfile.importer.readUpTo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Reading : ImportUiState

    /** The file is none of the supported formats, or could not be read. */
    data object Unsupported : ImportUiState

    data class Preview(
        val parsed: ParsedImport,
        val options: ImportOptions,
        val existing: List<Vehicle>,
    ) : ImportUiState

    data object Importing : ImportUiState
    data class Done(val result: ImportResult) : ImportUiState
    data object Failed : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: ImportService,
    private val vehicles: VehicleDao,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun onPicked(uri: Uri?) {
        if (uri == null) return
        _state.value = ImportUiState.Reading
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readUpTo(MAX_BYTES + 1) }
                    if (bytes == null || bytes.size > MAX_BYTES) {
                        null
                    } else {
                        val name = uri.lastPathSegment ?: "import"
                        FormatDetector.parse(ImportFile(name, bytes))
                    }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
            if (parsed == null || parsed.vehicles.isEmpty()) {
                _state.value = ImportUiState.Unsupported
                return@launch
            }
            val currency = settings.defaultCurrency.first().ifBlank { LocaleDefaults.forLocale(Locale.getDefault()).currency }
            _state.value = ImportUiState.Preview(parsed, ImportOptions(currency), vehicles.getAll())
        }
    }

    fun setCurrency(code: String) = update { it.copy(options = it.options.copy(currency = code)) }

    fun setTarget(sourceKey: String, target: VehicleTarget) =
        update { it.copy(options = it.options.copy(targets = it.options.targets + (sourceKey to target))) }

    fun setSkipped(sourceKey: String, skipped: Boolean) = update {
        val set = if (skipped) it.options.skipped + sourceKey else it.options.skipped - sourceKey
        it.copy(options = it.options.copy(skipped = set))
    }

    private fun update(block: (ImportUiState.Preview) -> ImportUiState.Preview) {
        (_state.value as? ImportUiState.Preview)?.let { _state.value = block(it) }
    }

    fun confirm() {
        val preview = _state.value as? ImportUiState.Preview ?: return
        _state.value = ImportUiState.Importing
        viewModelScope.launch {
            _state.value = try {
                val result = service.import(preview.parsed, preview.options)
                settings.setOnboardingDone()
                ImportUiState.Done(result)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                ImportUiState.Failed
            }
        }
    }

    fun reset() {
        _state.value = ImportUiState.Idle
    }

    private companion object {
        const val MAX_BYTES = 100 * 1024 * 1024
    }
}
