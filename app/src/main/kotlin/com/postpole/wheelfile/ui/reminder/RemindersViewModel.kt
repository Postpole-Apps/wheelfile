package com.postpole.wheelfile.ui.reminder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.data.ReminderItem
import com.postpole.wheelfile.data.ReminderRepository
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemindersUiState(
    val loaded: Boolean = false,
    val items: List<ReminderItem> = emptyList(),
    /** Set when the list is filtered to one vehicle (opened from the Vehicle detail bell). */
    val vehicle: Vehicle? = null,
    /** Vehicles that can get a new reminder (active ones). */
    val vehicles: List<Vehicle> = emptyList(),
)

/** S9. [vehicleId] filters the list to one vehicle; null lists every vehicle (Settings › Reminders). */
@HiltViewModel
class RemindersViewModel internal constructor(
    private val repo: ReminderRepository,
    vehicles: VehicleDao,
    private val vehicleId: Long?,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(savedState: SavedStateHandle, repo: ReminderRepository, vehicles: VehicleDao) :
        this(repo, vehicles, savedState.get<Long>("vehicleId")?.takeIf { it > 0 }, System::currentTimeMillis)

    val state: StateFlow<RemindersUiState> = combine(
        repo.observeItems(vehicleId, clock),
        vehicles.observeAll(),
    ) { items, all ->
        RemindersUiState(
            loaded = true,
            items = items,
            vehicle = vehicleId?.let { id -> all.firstOrNull { it.id == id } },
            vehicles = all.filter { !it.archived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    fun markDone(item: ReminderItem) {
        viewModelScope.launch { repo.markDone(item.reminder.id, clock()) }
    }

    fun delete(item: ReminderItem) {
        viewModelScope.launch { repo.delete(item.reminder) }
    }
}
