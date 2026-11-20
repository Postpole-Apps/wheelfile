package com.postpole.template.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.template.data.Item
import com.postpole.template.data.ItemDao
import com.postpole.template.review.ReviewPrompter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dao: ItemDao,
    private val review: ReviewPrompter,
) : ViewModel() {

    val items: StateFlow<List<Item>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _reviewRequests = Channel<Unit>(Channel.CONFLATED)

    /** Emits when the UI should show the Play In-App Review sheet. */
    val reviewRequests: Flow<Unit> = _reviewRequests.receiveAsFlow()

    fun addItem(title: String, now: Long = System.currentTimeMillis()) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            dao.insert(Item(title = trimmed, createdAt = now))
            // Adding an item is the template's "positive action"; apps should call this from their own.
            if (review.recordPositiveAction()) _reviewRequests.trySend(Unit)
        }
    }
}
