package com.postpole.wheelfile.ui.document

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.data.DocumentRepository
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.data.db.VehicleDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentDetailUiState(
    val loaded: Boolean = false,
    /** Null once loaded means the document no longer exists (deleted). */
    val document: VehicleDocument? = null,
    val vehicle: Vehicle? = null,
    val attachments: List<Attachment> = emptyList(),
)

/** S12: expiry card, attachment preview, detail rows. 0 ads. */
@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: DocumentRepository,
    vehicles: VehicleDao,
) : ViewModel() {
    private val vehicleId = checkNotNull(savedState.get<Long>("vehicleId"))
    private val documentId = checkNotNull(savedState.get<Long>("documentId"))

    val state: StateFlow<DocumentDetailUiState> = combine(
        repo.observe(documentId),
        vehicles.observe(vehicleId),
        repo.observeAttachments(documentId),
    ) { doc, vehicle, files -> DocumentDetailUiState(loaded = true, document = doc, vehicle = vehicle, attachments = files) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentDetailUiState())

    fun delete(onDone: () -> Unit) {
        val doc = state.value.document ?: return
        viewModelScope.launch {
            repo.delete(doc)
            onDone()
        }
    }
}
