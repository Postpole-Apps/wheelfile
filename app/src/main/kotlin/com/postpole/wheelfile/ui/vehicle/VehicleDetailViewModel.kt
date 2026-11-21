package com.postpole.wheelfile.ui.vehicle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.DocumentItem
import com.postpole.wheelfile.data.DocumentRepository
import com.postpole.wheelfile.data.ReminderRepository
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.domain.report.ReportRange
import com.postpole.wheelfile.domain.report.ReportSource
import kotlinx.coroutines.flow.distinctUntilChanged
import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.vehicle.VehicleLimit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.postpole.wheelfile.domain.history.HistoryFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VehicleDetailUiState(
    /** Null while loading. */
    val data: VehicleData? = null,
    val switcher: List<Vehicle> = emptyList(),
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
    val showFreeLimit: Boolean = false,
    val showArchivePicker: Boolean = false,
    val loaded: Boolean = false,
    /** Open reminders that are due soon or overdue: the badge on the bell in the top bar. */
    val dueReminders: Int = 0,
    val documents: List<DocumentItem> = emptyList(),
    /** Every attachment on the device, for the free-cap counter on the Documents tab. */
    val attachmentCount: Int = 0,
    /** Receipts attached to this vehicle's entries (the Receipts card). */
    val receiptCount: Int = 0,
    val isPro: Boolean = false,
    /** The chip the user picked. A Pro range on the free plan shows the inline locked card, never a full-screen interrupt. */
    val reportRange: ReportRange = ReportRange.LAST_12_MONTHS,
    /** Custom range bounds as epoch millis at the start of the chosen day. */
    val customFrom: Long? = null,
    val customTo: Long? = null,
    /** Every active vehicle's rows, only filled while the Compare range is open for a Pro user. */
    val compareSources: List<ReportSource> = emptyList(),
) {
    val vehicle: Vehicle? get() = data?.vehicle
    val readOnly: Boolean get() = vehicle?.archived == true
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleDetailViewModel internal constructor(
    private val repo: VehicleRepository,
    private val vehicleId: Long,
    private val isPro: () -> Boolean,
    private val clock: () -> Long,
    private val dueCount: Flow<Int> = flowOf(0),
    private val documents: Flow<List<DocumentItem>> = flowOf(emptyList()),
    private val attachmentCount: Flow<Int> = flowOf(0),
    private val receiptCount: Flow<Int> = flowOf(0),
    private val proFlow: Flow<Boolean> = flowOf(false),
    private val onReviewViewed: () -> Unit = {},
) : ViewModel() {

    @Inject
    constructor(
        savedState: SavedStateHandle,
        repo: VehicleRepository,
        billing: BillingRepository,
        reminders: ReminderRepository,
        documents: DocumentRepository,
        attachments: AttachmentDao,
        adsPolicy: AdsPolicy,
    ) : this(
        repo, checkNotNull(savedState.get<Long>("vehicleId")), { billing.isPro.value }, System::currentTimeMillis,
        reminders.observeDueCount(checkNotNull(savedState.get<Long>("vehicleId"))),
        documents.observeItems(checkNotNull(savedState.get<Long>("vehicleId"))),
        attachments.observeCount(),
        documents.observeReceiptCount(checkNotNull(savedState.get<Long>("vehicleId"))),
        billing.isPro,
        adsPolicy::onReviewViewed,
    )

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val freeLimit = MutableStateFlow(false)
    private val picker = MutableStateFlow(false)
    private val range = MutableStateFlow(ReportRange.LAST_12_MONTHS)
    private val customFrom = MutableStateFlow<Long?>(null)
    private val customTo = MutableStateFlow<Long?>(null)

    /** Other vehicles' rows for Compare. Collected only while Compare is the chosen range and the user is Pro. */
    private val compareSources: Flow<List<ReportSource>> = combine(range, proFlow) { r, pro -> r == ReportRange.COMPARE && pro }
        .distinctUntilChanged()
        .flatMapLatest { wanted ->
            if (!wanted) {
                flowOf(emptyList())
            } else {
                repo.vehicles.observeAll().flatMapLatest { all ->
                    val active = all.filter { !it.archived }
                    if (active.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(active.map { repo.observeData(it) }) { rows -> rows.map { ReportSource(it.vehicle, it.logs, it.services, it.expenses) } }
                    }
                }
            }
        }

    val state: StateFlow<VehicleDetailUiState> = repo.vehicles.observe(vehicleId)
        .flatMapLatest { v -> if (v == null) flowOf<VehicleData?>(null) else repo.observeData(v) }
        .let { data ->
            combine(data, repo.vehicles.observeAll(), filter, freeLimit, picker) { d, all, f, limit, pick ->
                VehicleDetailUiState(
                    data = d,
                    switcher = all.filter { !it.archived },
                    historyFilter = f,
                    showFreeLimit = limit,
                    showArchivePicker = pick,
                    loaded = true,
                )
            }
        }
        .let { base -> combine(base, dueCount) { s, due -> s.copy(dueReminders = due) } }
        .let { base ->
            combine(base, documents, attachmentCount, receiptCount) { s, docs, files, receipts ->
                s.copy(documents = docs, attachmentCount = files, receiptCount = receipts)
            }
        }
        .let { base ->
            combine(base, proFlow, range, combine(customFrom, customTo, ::Pair), compareSources) { s, pro, r, custom, compare ->
                s.copy(isPro = pro, reportRange = r, customFrom = custom.first, customTo = custom.second, compareSources = compare)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleDetailUiState())

    fun setFilter(f: HistoryFilter) {
        filter.value = f
    }

    fun setReportRange(r: ReportRange) {
        range.value = r
    }

    fun setCustomRange(from: Long?, to: Long?) {
        customFrom.value = from
        customTo.value = to
    }

    /** The History or Reports tab was opened: records the "reviewed" flag the V7 interstitial reads. Shows nothing yet. */
    fun reviewViewed() = onReviewViewed()

    fun archive() {
        viewModelScope.launch { repo.setArchived(vehicleId, true, clock()) }
    }

    /** Restoring counts against the 2-vehicle free cap, so it can open the free-limit dialog. */
    fun restore() {
        viewModelScope.launch {
            if (VehicleLimit.canAddActive(repo.vehicles.activeCount(), isPro())) {
                repo.setArchived(vehicleId, false, clock())
            } else {
                freeLimit.update { true }
            }
        }
    }

    fun dismissFreeLimit() {
        freeLimit.value = false
        picker.value = false
    }

    /** "Archive a vehicle" on the free-limit dialog: swap to the picker of active vehicles. */
    fun openArchivePicker() {
        freeLimit.value = false
        picker.value = true
    }

    /** Archives the chosen active vehicle, then completes the restore of the vehicle being viewed. */
    fun archiveAndRestore(chosenId: Long) {
        viewModelScope.launch {
            val now = clock()
            repo.setArchived(chosenId, true, now)
            repo.setArchived(vehicleId, false, now)
            picker.value = false
            freeLimit.value = false
        }
    }
}
