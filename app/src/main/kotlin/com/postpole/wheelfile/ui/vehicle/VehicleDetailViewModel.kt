package com.postpole.wheelfile.ui.vehicle

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.ads.ActiveProPrompt
import com.postpole.wheelfile.ads.AdsConfigSource
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.ProPromptCoordinator
import com.postpole.wheelfile.ads.ProPromptDismissMethod
import com.postpole.wheelfile.ads.ProPromptRestPoint
import com.postpole.wheelfile.ui.pro.PaywallTrigger
import com.postpole.wheelfile.ads.InterstitialCoordinator
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.DocumentRepository
import com.postpole.wheelfile.data.ReminderRepository
import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.document.DocumentPreset
import com.postpole.wheelfile.domain.document.RegionPresets
import com.postpole.wheelfile.domain.report.CostSource
import com.postpole.wheelfile.domain.report.ReportBuilder
import com.postpole.wheelfile.domain.report.ReportRange
import com.postpole.wheelfile.domain.report.ReportWindow
import com.postpole.wheelfile.domain.vehicle.VehicleLimit
import com.postpole.wheelfile.ui.document.DocumentsUiState
import com.postpole.wheelfile.ui.report.ReportsUiState
import java.time.Instant
import java.time.ZoneId
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
    val documents: DocumentsUiState = DocumentsUiState(),
    val reports: ReportsUiState = ReportsUiState(),
    /** The clock the state was built with, for expiry pills. */
    val now: Long = 0L,
    /** The `entries_milestone` sheet on Overview: the threshold that fired, or null when none shows. */
    val proSheetCount: Int? = null,
    /** Play's formatted Pro price for the sheet CTA; null until loaded. */
    val proPrice: String? = null,
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
    private val documents: DocumentRepository? = null,
    private val proFlow: Flow<Boolean> = flowOf(false),
    private val attachmentTotal: Flow<Int> = flowOf(0),
    private val adsPolicy: AdsPolicy? = null,
    private val interstitials: InterstitialCoordinator? = null,
    private val proPrompts: ProPromptCoordinator? = null,
    private val proPrice: Flow<String?> = flowOf(null),
    private val entriesThreshold: () -> Int = { 0 },
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
        interstitials: InterstitialCoordinator,
        proPrompts: ProPromptCoordinator,
        configSource: AdsConfigSource,
    ) : this(
        repo, checkNotNull(savedState.get<Long>("vehicleId")), { billing.isPro.value }, System::currentTimeMillis,
        reminders.observeDueCount(checkNotNull(savedState.get<Long>("vehicleId"))),
        documents, billing.isPro, attachments.observeCount(), adsPolicy, interstitials,
        proPrompts, billing.price, { configSource.config.value.proPrompt.coerced().entriesThreshold },
    )

    private val proSheet = MutableStateFlow<ActiveProPrompt?>(null)
    private var proSheetCount = 0
    private var proEvaluating = false

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val freeLimit = MutableStateFlow(false)
    private val picker = MutableStateFlow(false)
    private val range = MutableStateFlow(ReportRange.LAST_12_MONTHS)
    private val customRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    private var viewedReview = false

    private val documentsState: Flow<DocumentsUiState> = combine(
        documents?.observeItems(vehicleId) ?: flowOf(emptyList()),
        documents?.observeReceiptCount(vehicleId) ?: flowOf(0),
        attachmentTotal,
        proFlow,
    ) { items, receipts, total, pro -> DocumentsUiState(items, receipts, total, pro) }

    /** Every active vehicle's data, only while the Compare chip is selected. */
    private val comparisonData: Flow<List<VehicleData>> = range.flatMapLatest { r ->
        if (r != ReportRange.COMPARE) {
            flowOf(emptyList())
        } else {
            repo.vehicles.observeAll().flatMapLatest { all ->
                val active = all.filter { !it.archived }
                if (active.isEmpty()) flowOf(emptyList()) else combine(active.map { repo.observeData(it) }) { it.toList() }
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
            combine(base, documentsState) { s, d -> s.copy(documents = d.copy(vehicleYear = s.vehicle?.year, readOnly = s.readOnly)) }
        }
        .let { base ->
            combine(base, range, customRange, proFlow, comparisonData) { s, r, custom, pro, compare ->
                val now = clock()
                val reports = s.data?.let { buildReports(it, r, custom.first, custom.second, pro, compare, now) }
                    ?: ReportsUiState(range = r, isPro = pro)
                s.copy(reports = reports, now = now)
            }
        }
        .let { base ->
            combine(base, proSheet, proPrice) { s, sheet, price ->
                s.copy(proSheetCount = sheet?.let { proSheetCount }, proPrice = price)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleDetailUiState())

    fun setFilter(f: HistoryFilter) {
        filter.value = f
    }

    /**
     * Overview settled (a rest point: after a save the user is back here, with the new entry already in Recent
     * entries). This screen hosts the sheet but has no card slot, so the policy gets `hasCardSlot = false` and a card
     * (pump-session fallback, `remove_ads` owner) waits for the Garage.
     */
    fun onOverviewRestPoint() {
        val coordinator = proPrompts ?: return
        if (proSheet.value != null || proEvaluating) return
        proEvaluating = true
        viewModelScope.launch {
            try {
                val shown = coordinator.onRestPoint(ProPromptRestPoint.VEHICLE_OVERVIEW)
                if (shown != null) {
                    proSheetCount = entriesThreshold()
                    proSheet.value = shown
                }
            } finally {
                proEvaluating = false
            }
        }
    }

    /** Swipe, scrim tap or Back is `swipe`; the X is `close`; "Not now" is `not_now`. Counts toward the 3 sheet dismissals. */
    fun dismissProSheet(method: ProPromptDismissMethod) {
        val shown = proSheet.value ?: return
        proSheet.value = null
        viewModelScope.launch { proPrompts?.onDismissed(shown, method) }
    }

    /** The CTA: logs the tap and returns the paywall trigger to open, or null when no sheet is showing. */
    fun onProSheetCta(): PaywallTrigger? {
        val shown = proSheet.value ?: return null
        proPrompts?.onCtaTapped(shown)
        return ProPromptCoordinator.paywallTrigger(shown.trigger)
    }

    /** The user opened [tab]. History and Reports count as having reviewed the vehicle (DECISIONS.md item 9). */
    fun onTabViewed(tab: VehicleTab) {
        if (tab == VehicleTab.HISTORY || tab == VehicleTab.REPORTS) {
            viewedReview = true
            interstitials?.preloadIfEligible()
        }
    }

    /**
     * Leaving Vehicle detail for the Garage. Records whether History or Reports was viewed and, when [activity] is
     * given, lets the policy decide on `wf_interstitial_after_review`. Call it after the Garage navigation was started.
     */
    fun onLeaving(activity: Activity? = null) {
        adsPolicy?.onLeftVehicleDetail(viewedReview)
        if (activity != null) interstitials?.onBackToGarage(activity)
    }

    fun setRange(r: ReportRange) {
        range.value = r
        if (r == ReportRange.CUSTOM && customRange.value.first == null) {
            val now = clock()
            customRange.value = ReportWindow.last12Months(now).startMillis to now
        }
    }

    fun setCustomRange(from: Long?, to: Long?) {
        customRange.value = from to to
    }

    /** Creates the documents for the chosen presets. Alerts come from the reminder engine as for any document. */
    fun applyPresets(presets: List<DocumentPreset>) {
        val repository = documents ?: return
        viewModelScope.launch {
            val vehicle = repo.vehicles.get(vehicleId) ?: return@launch
            val today = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate()
            repository.applyPlans(vehicle, RegionPresets.plans(presets, vehicle.year, today))
        }
    }

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

    internal companion object {
        /**
         * The Reports tab for [data]. A Pro range picked on the free plan falls back to the 12-month window (the screen
         * shows the locked card above it). Amounts are never converted between currencies.
         */
        fun buildReports(
            data: VehicleData,
            range: ReportRange,
            customFrom: Long?,
            customTo: Long?,
            isPro: Boolean,
            others: List<VehicleData>,
            now: Long,
            zone: ZoneId = ZoneId.systemDefault(),
        ): ReportsUiState {
            val effective = if (range.isPro && !isPro) ReportRange.LAST_12_MONTHS else range
            val window = when (effective) {
                ReportRange.LAST_12_MONTHS, ReportRange.COMPARE -> ReportWindow.last12Months(now, zone)
                ReportRange.ALL_TIME -> ReportWindow.ALL
                ReportRange.CUSTOM -> {
                    val last12 = ReportWindow.last12Months(now, zone)
                    fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
                    val from = day(customFrom ?: last12.startMillis!!)
                    val to = day(customTo ?: now)
                    ReportWindow.custom(from, to, zone)
                }
            }
            val v = data.vehicle
            return ReportsUiState(
                range = range,
                isPro = isPro,
                customFrom = customFrom,
                customTo = customTo,
                costs = ReportBuilder.monthlyCosts(listOf(CostSource(v.currency, data.logs, data.services, data.expenses)), window, zone),
                economy = ReportBuilder.economySeries(v, data.logs, window),
                prices = ReportBuilder.priceSeries(v, data.logs, window),
                comparisons = if (effective == ReportRange.COMPARE) {
                    others.map { ReportBuilder.compare(it.vehicle, it.logs, it.services, it.expenses, ReportWindow.ALL) }
                } else {
                    emptyList()
                },
            )
        }
    }
}
