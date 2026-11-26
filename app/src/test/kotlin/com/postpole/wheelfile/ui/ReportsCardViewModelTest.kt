package com.postpole.wheelfile.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.ads.InMemoryProPromptStateStore
import com.postpole.wheelfile.ads.ProAccess
import com.postpole.wheelfile.ads.ProPromptConfig
import com.postpole.wheelfile.ads.ProPromptCoordinator
import com.postpole.wheelfile.ads.ProPromptDismissMethod
import com.postpole.wheelfile.ads.ProPromptPolicy
import com.postpole.wheelfile.ads.ProPromptTrigger
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.report.ReportRange
import com.postpole.wheelfile.ui.pro.PRO_PROMPT_CARD_TAG
import com.postpole.wheelfile.ui.pro.PaywallTrigger
import com.postpole.wheelfile.ui.report.REPORTS_LOCKED_TAG
import com.postpole.wheelfile.ui.report.ReportsProCard
import com.postpole.wheelfile.ui.report.ReportsTab
import com.postpole.wheelfile.ui.report.ReportsUiState
import com.postpole.wheelfile.ui.theme.WheelfileTheme
import com.postpole.wheelfile.ui.vehicle.VehicleDetailUiState
import com.postpole.wheelfile.ui.vehicle.VehicleDetailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * `reports_3_months` through the real [VehicleDetailViewModel] path (injected clock, thread-safe analytics fake),
 * plus the ReportsTab rule that the card and the locked `st-pro-upsell` card never show together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReportsCardViewModelTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: VehicleRepository
    private val day = 24L * 60 * 60 * 1_000
    private val install = 1_700_000_000_000L
    private val now = install + 120 * day
    private val events = CopyOnWriteArrayList<Pair<String, Map<String, Any>>>()
    private val analytics = object : Analytics {
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = throw throwable
    }
    private val scope = CoroutineScope(Dispatchers.Default)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = VehicleRepository(db.vehicleDao(), db.energyLogDao(), db.serviceRecordDao(), db.expenseDao())
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun vehicle() = Vehicle(
        name = "A", type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", createdAt = 1, updatedAt = 1,
    )

    private fun fillUp(id: Long, at: Long) = EnergyLog(
        vehicleId = id, stream = EnergyStream.LIQUID, dateTime = at, odometer = 1000.0, quantity = 40.0, isFull = true,
        createdAt = 1, updatedAt = 1,
    )

    private fun service(id: Long, at: Long) = ServiceRecord(vehicleId = id, dateTime = at, createdAt = 1, updatedAt = 1)

    private fun expense(id: Long, at: Long) = Expense(
        vehicleId = id, dateTime = at, category = ExpenseCategory.OTHER, amountMinor = 100, createdAt = 1, updatedAt = 1,
    )

    /** Past the first session and the first 3 days, so the shared caps allow a prompt. */
    private fun policy(sessions: Int = 3): ProPromptPolicy {
        val clock = install + 4 * day
        val p = ProPromptPolicy({ clock }, InMemoryProPromptStateStore())
        runBlocking { repeat(sessions) { p.startSession(install) } }
        return p
    }

    private fun coordinator(policy: ProPromptPolicy, access: ProAccess = ProAccess.FREE) =
        ProPromptCoordinator(policy, { ProPromptConfig() }, { access }, { 0 }, analytics)

    private class Harness(val vm: VehicleDetailViewModel, private val latest: AtomicReference<VehicleDetailUiState>) {
        val state: VehicleDetailUiState get() = latest.get()
    }

    /** A view model with an active subscriber, so its state is built from Room. Waits until the data is loaded. */
    private fun harness(id: Long, coordinator: ProPromptCoordinator, subscribe: Boolean = true): Harness {
        val vm = VehicleDetailViewModel(
            repo, id, { false }, { now }, proPrompts = coordinator, proPrice = flowOf("PRICE"),
        )
        val latest = AtomicReference(VehicleDetailUiState())
        if (subscribe) {
            scope.launch { vm.state.collect { latest.set(it) } }
            waitUntil { latest.get().data != null }
        }
        return Harness(vm, latest)
    }

    private fun waitUntil(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun named(name: String) = events.filter { it.first == name }

    private fun vehicleWith(vararg entries: (Long) -> Any): Long = runBlocking {
        val id = db.vehicleDao().insert(vehicle())
        entries.forEach {
            when (val e = it(id)) {
                is EnergyLog -> db.energyLogDao().insert(e)
                is ServiceRecord -> db.serviceRecordDao().insert(e)
                is Expense -> db.expenseDao().insert(e)
            }
        }
        id
    }

    // ---- view-model path ----

    @Test fun threeMonthsOfData_on12MonthRange_setsTheCardAndLogsShownOnce() {
        val id = vehicleWith({ fillUp(it, now - 120 * day) })
        val h = harness(id, coordinator(policy()))
        h.vm.onReportsOpened()
        waitUntil { h.state.reportsCard != null }
        assertEquals(ProPromptTrigger.REPORTS_3_MONTHS, h.state.reportsCard?.trigger)
        h.vm.onReportsOpened() // a re-run (e.g. a configuration change) while the card is up does nothing more
        val shown = named("pro_prompt_shown").single()
        assertEquals("reports_3_months", shown.second["trigger"])
        assertEquals("card", shown.second["surface"])
    }

    @Test fun lessThanThreeMonthsOfData_setsNoCard() {
        val id = vehicleWith({ fillUp(it, now - 60 * day) })
        val p = policy()
        val h = harness(id, coordinator(p))
        h.vm.onReportsOpened()
        assertNull(h.state.reportsCard)
        assertTrue(named("pro_prompt_shown").isEmpty())
        assertTrue(p.current.pending.isEmpty()) // nothing qualified
    }

    @Test fun otherRangesNeverEvaluate_andTheCardAppearsOnReturnTo12Months() {
        val id = vehicleWith({ fillUp(it, now - 120 * day) })
        val p = policy()
        val h = harness(id, coordinator(p))
        for (r in listOf(ReportRange.ALL_TIME, ReportRange.COMPARE, ReportRange.CUSTOM)) {
            h.vm.setRange(r)
            waitUntil { h.state.reports.range == r }
            assertTrue(h.state.reports.locked) // free user on a Pro range
            h.vm.onReportsOpened()
            assertNull(h.state.reportsCard)
        }
        assertTrue(named("pro_prompt_shown").isEmpty())
        assertTrue(p.current.pending.isEmpty()) // not even qualified
        h.vm.setRange(ReportRange.LAST_12_MONTHS)
        h.vm.onReportsOpened()
        waitUntil { h.state.reportsCard != null }
    }

    @Test fun dataNotLoaded_neverEvaluates() {
        val id = vehicleWith({ fillUp(it, now - 120 * day) })
        val p = policy()
        val h = harness(id, coordinator(p), subscribe = false) // no subscriber: the state is still the empty initial one
        assertNull(h.vm.state.value.data)
        h.vm.onReportsOpened()
        assertTrue(named("pro_prompt_shown").isEmpty())
        assertTrue(p.current.pending.isEmpty())
    }

    @Test fun firstEntryIsTakenAcrossFillUpsServicesAndExpenses() {
        // Recent fill-up and service; the oldest entry is an expense, 100 days ago.
        val id = vehicleWith(
            { fillUp(it, now - 10 * day) },
            { service(it, now - 20 * day) },
            { expense(it, now - 100 * day) },
        )
        val h = harness(id, coordinator(policy()))
        h.vm.onReportsOpened()
        waitUntil { h.state.reportsCard != null }
    }

    @Test fun firstEntryAcrossStreams_tooRecentOldestDoesNotQualify() {
        val id = vehicleWith(
            { fillUp(it, now - 10 * day) },
            { service(it, now - 20 * day) },
            { expense(it, now - 60 * day) },
        )
        val h = harness(id, coordinator(policy()))
        h.vm.onReportsOpened()
        assertNull(h.state.reportsCard)
    }

    @Test fun dismissLogsCloseAndClearsTheCard() {
        val id = vehicleWith({ expense(it, now - 120 * day) })
        val h = harness(id, coordinator(policy()))
        h.vm.onReportsOpened()
        waitUntil { h.state.reportsCard != null }
        h.vm.dismissReportsCard()
        waitUntil { h.state.reportsCard == null }
        waitUntil { named("pro_prompt_dismissed").isNotEmpty() }
        val ev = named("pro_prompt_dismissed").single()
        assertEquals("reports_3_months", ev.second["trigger"])
        assertEquals("card", ev.second["surface"])
        assertEquals(ProPromptDismissMethod.CLOSE.id, ev.second["method"])
    }

    @Test fun ctaReturnsTheReportsPaywallTriggerAndLogsCta() {
        val id = vehicleWith({ expense(it, now - 120 * day) })
        val h = harness(id, coordinator(policy()))
        assertNull(h.vm.onReportsCardCta()) // no card yet: nothing to open
        h.vm.onReportsOpened()
        waitUntil { h.state.reportsCard != null }
        assertEquals(PaywallTrigger.AUTO_REPORTS_3_MONTHS, h.vm.onReportsCardCta())
        val ev = named("pro_prompt_cta_tap").single()
        assertEquals("reports_3_months", ev.second["trigger"])
        assertEquals("card", ev.second["surface"])
        assertNotNull(h.state.reportsCard) // the card stays for the return from the paywall
    }

    @Test fun proOwnersGetNoCard() {
        val id = vehicleWith({ expense(it, now - 120 * day) })
        val h = harness(id, coordinator(policy(), ProAccess.PRO))
        h.vm.onReportsOpened()
        assertNull(h.state.reportsCard)
        assertTrue(named("pro_prompt_shown").isEmpty())
    }

    @Test fun removeAdsOwnersStillGetACard() {
        val id = vehicleWith({ expense(it, now - 120 * day) })
        val h = harness(id, coordinator(policy(), ProAccess.REMOVE_ADS))
        h.vm.onReportsOpened()
        waitUntil { h.state.reportsCard != null }
    }

    // ---- ReportsTab: never together with the locked card ----

    private val proCard = ReportsProCard(price = "PRICE", onCta = {}, onClose = {})

    private fun count(tag: String): Int = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    @Test fun reportsTab_12MonthViewShowsTheCardAndNotTheLockedCard() {
        rule.setContent {
            WheelfileTheme(darkTheme = false) {
                ReportsTab(
                    vehicle = vehicle(), state = ReportsUiState(range = ReportRange.LAST_12_MONTHS),
                    onRange = {}, onCustom = { _, _ -> }, onSeePro = {}, proCard = proCard,
                )
            }
        }
        rule.onNodeWithTag(PRO_PROMPT_CARD_TAG).assertExists()
        rule.onNodeWithTag(REPORTS_LOCKED_TAG).assertDoesNotExist()
    }

    @Test fun reportsTab_switchingRangesNeverStacksTheTwoCards() {
        val range = mutableStateOf(ReportRange.LAST_12_MONTHS)
        rule.setContent {
            WheelfileTheme(darkTheme = false) {
                ReportsTab(
                    vehicle = vehicle(), state = ReportsUiState(range = range.value),
                    onRange = {}, onCustom = { _, _ -> }, onSeePro = {}, proCard = proCard,
                )
            }
        }
        for (r in ReportRange.entries + ReportRange.LAST_12_MONTHS) {
            range.value = r
            rule.waitForIdle()
            val card = count(PRO_PROMPT_CARD_TAG)
            val locked = count(REPORTS_LOCKED_TAG)
            assertTrue("range $r: card=$card locked=$locked", card + locked <= 1)
            assertEquals("range $r card", if (r == ReportRange.LAST_12_MONTHS) 1 else 0, card)
            assertEquals("range $r locked", if (r == ReportRange.LAST_12_MONTHS) 0 else 1, locked)
        }
    }

    @Test fun reportsTab_proUserOnAProRangeIsNotLockedAndTheCardStaysOffThatRange() {
        rule.setContent {
            WheelfileTheme(darkTheme = false) {
                ReportsTab(
                    vehicle = vehicle(), state = ReportsUiState(range = ReportRange.ALL_TIME, isPro = true),
                    onRange = {}, onCustom = { _, _ -> }, onSeePro = {}, proCard = proCard,
                )
            }
        }
        rule.onNodeWithTag(REPORTS_LOCKED_TAG).assertDoesNotExist()
        rule.onNodeWithTag(PRO_PROMPT_CARD_TAG).assertDoesNotExist()
    }
}
