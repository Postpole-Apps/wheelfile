package com.postpole.wheelfile.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.runtime.collectAsState
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.ads.InMemoryProPromptStateStore
import com.postpole.wheelfile.ads.ProAccess
import com.postpole.wheelfile.ads.ProPromptConfig
import com.postpole.wheelfile.ads.ProPromptCoordinator
import com.postpole.wheelfile.ads.ProPromptDismissMethod
import com.postpole.wheelfile.ads.ProPromptPolicy
import com.postpole.wheelfile.ads.ProPromptSurface
import com.postpole.wheelfile.ads.ProPromptTrigger
import com.postpole.wheelfile.ads.SaveKind
import com.postpole.wheelfile.analytics.NoOpAnalytics
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.ui.garage.GarageActions
import com.postpole.wheelfile.ui.garage.GarageScreen
import com.postpole.wheelfile.ui.garage.GarageViewModel
import com.postpole.wheelfile.ui.pro.PRO_PROMPT_CARD_TAG
import com.postpole.wheelfile.ui.pro.PRO_PROMPT_CLOSE_TAG
import com.postpole.wheelfile.ui.pro.PRO_PROMPT_CTA_TAG
import com.postpole.wheelfile.ui.pro.PRO_PROMPT_NOT_NOW_TAG
import com.postpole.wheelfile.ui.pro.PRO_PROMPT_SHEET_TAG
import com.postpole.wheelfile.ui.pro.ProPromptCard
import com.postpole.wheelfile.ui.pro.ProPromptSheet
import com.postpole.wheelfile.ui.vehicle.VehicleDetailViewModel
import com.postpole.wheelfile.ui.theme.WheelfileTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Card and sheet UI, the dismiss methods, the exact English copy, and the Garage rest point end to end. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProPromptUiTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: VehicleRepository
    private val day = 24L * 60 * 60 * 1_000
    private val install = 1_700_000_000_000L
    private val now = install + 10 * day
    private val dismissed = mutableListOf<ProPromptDismissMethod>()
    private var ctaTaps = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = VehicleRepository(db.vehicleDao(), db.energyLogDao(), db.serviceRecordDao(), db.expenseDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun str(id: Int, vararg args: Any) = ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun card(trigger: ProPromptTrigger, price: String? = "PRICE") = rule.setContent {
        WheelfileTheme(darkTheme = false) {
            ProPromptCard(trigger, count = 10, price = price, onCta = { ctaTaps++ }, onClose = { dismissed += ProPromptDismissMethod.CLOSE })
        }
    }

    private fun sheet(price: String? = "PRICE") = rule.setContent {
        WheelfileTheme(darkTheme = false) {
            ProPromptSheet(trigger = ProPromptTrigger.ENTRIES_MILESTONE, count = 10, price = price, onCta = { ctaTaps++ }, onDismiss = { dismissed += it })
        }
    }

    // ---- Copy (spec v0.2.2 table, exact) ----

    @Test fun entriesCard_copyIsExact() {
        card(ProPromptTrigger.ENTRIES_MILESTONE)
        rule.onNodeWithText("10 entries logged").assertIsDisplayed()
        rule.onNodeWithText("Pro adds unlimited vehicles, a PDF history report and all-time charts.").assertIsDisplayed()
        rule.onNodeWithText("See Pro · PRICE").assertIsDisplayed()
        rule.onNodeWithContentDescription("Dismiss").assertIsDisplayed()
    }

    @Test fun secondVehicleCard_copyIsFromTheMockup() {
        card(ProPromptTrigger.SECOND_VEHICLE_ADDED)
        rule.onNodeWithText("Compare your vehicles with Pro").assertIsDisplayed()
        rule.onNodeWithText("Pro unlocks vehicle comparison and unlimited active vehicles.").assertIsDisplayed()
        rule.onNodeWithText("See Pro · PRICE").assertIsDisplayed()
        rule.onNodeWithContentDescription("Dismiss").assertIsDisplayed()
    }

    @Test fun serviceSheet_copyIsFromTheMockup() {
        rule.setContent {
            WheelfileTheme(darkTheme = false) {
                ProPromptSheet(ProPromptTrigger.SERVICE_LOGGED, count = 10, price = null, onCta = { ctaTaps++ }, onDismiss = { dismissed += it })
            }
        }
        rule.onNodeWithText("PDF service history for resale").assertExists()
        rule.onNodeWithText("Share a clean record of services, costs and economy when you sell or visit the workshop.").assertExists()
        rule.onNodeWithText("PDF service history for resale or your mechanic").assertExists()
        rule.onNodeWithText("All-time charts and custom ranges").assertExists()
        rule.onNodeWithText("Unlimited vehicles").assertExists()
        rule.onNodeWithText("See Pro").assertExists() // no price loaded: no price on the CTA
        rule.onNodeWithText("Not now").assertExists()
        rule.onNodeWithContentDescription("Dismiss").assertExists()
    }

    @Test fun serviceCard_usesTheServiceCopy() {
        card(ProPromptTrigger.SERVICE_LOGGED)
        rule.onNodeWithText("PDF service history for resale").assertIsDisplayed()
        rule.onNodeWithText("See Pro · PRICE").assertIsDisplayed()
    }

    @Test fun entriesSheet_copyIsExact() {
        sheet()
        rule.onNodeWithText("10 entries in your logbook").assertExists()
        rule.onNodeWithText("Keep going with Pro: a one-time purchase that adds more to the logbook you already keep.").assertExists()
        rule.onNodeWithText("Unlimited vehicles").assertExists()
        rule.onNodeWithText("PDF history report").assertExists()
        rule.onNodeWithText("All-time charts").assertExists()
        rule.onNodeWithText("See Pro · PRICE").assertExists()
        rule.onNodeWithText("Not now").assertExists()
        rule.onNodeWithContentDescription("Close").assertExists()
    }

    @Test fun ctaWithoutAPriceReadsSeeProOnly() {
        card(ProPromptTrigger.ENTRIES_MILESTONE, price = null)
        rule.onNodeWithTag(PRO_PROMPT_CTA_TAG).assertExists()
        rule.onNodeWithText("See Pro").assertIsDisplayed()
    }

    @Test fun promptStringsNeverMentionAds() {
        val ids = listOf(
            com.postpole.wheelfile.R.string.pro_auto_entries_body,
            com.postpole.wheelfile.R.string.pro_auto_entries_sheet_body,
            com.postpole.wheelfile.R.string.pro_auto_second_vehicle_title,
            com.postpole.wheelfile.R.string.pro_auto_second_vehicle_body,
            com.postpole.wheelfile.R.string.pro_benefit_unlimited_vehicles,
            com.postpole.wheelfile.R.string.pro_benefit_pdf_history,
            com.postpole.wheelfile.R.string.pro_benefit_all_time_charts,
        )
        ids.forEach { assertEquals(false, Regex("\\bads?\\b", RegexOption.IGNORE_CASE).containsMatchIn(str(it))) }
    }

    // ---- Dismiss methods ----

    @Test fun card_closeXIsMethodClose_andCtaOpensThePaywall() {
        card(ProPromptTrigger.ENTRIES_MILESTONE)
        rule.onNodeWithTag(PRO_PROMPT_CTA_TAG).performClick()
        assertEquals(1, ctaTaps)
        rule.onNodeWithTag(PRO_PROMPT_CLOSE_TAG).performClick()
        assertEquals(listOf(ProPromptDismissMethod.CLOSE), dismissed)
    }

    @Test fun card_hasNoNotNowButton() {
        card(ProPromptTrigger.ENTRIES_MILESTONE)
        rule.onNodeWithTag(PRO_PROMPT_NOT_NOW_TAG).assertDoesNotExist()
    }

    @Test fun sheet_closeXIsMethodClose() {
        sheet()
        rule.onNodeWithTag(PRO_PROMPT_CLOSE_TAG).performClick()
        assertEquals(listOf(ProPromptDismissMethod.CLOSE), dismissed)
    }

    @Test fun sheet_notNowIsMethodNotNow() {
        sheet()
        // The sheet content scrolls (at large font sizes especially), so bring the button into view first.
        rule.onNodeWithTag(PRO_PROMPT_NOT_NOW_TAG).performScrollTo().performClick()
        assertEquals(listOf(ProPromptDismissMethod.NOT_NOW), dismissed)
    }

    @Test fun sheet_swipeDownIsMethodSwipe() {
        sheet()
        rule.onNodeWithTag(PRO_PROMPT_SHEET_TAG).performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(listOf(ProPromptDismissMethod.SWIPE), dismissed)
    }

    // ---- Database count: deleted entries do not count ----

    private fun vehicle(name: String) = Vehicle(
        name = name, type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", createdAt = 1, updatedAt = 1,
    )

    private fun expense(vehicleId: Long) = Expense(
        vehicleId = vehicleId, dateTime = now, category = ExpenseCategory.OTHER, amountMinor = 100, createdAt = 1, updatedAt = 1,
    )

    @Test fun deletedEntriesDoNotCountInTheDatabaseTotal() = runBlocking {
        val id = db.vehicleDao().insert(vehicle("A"))
        val ids = (1..4).map { db.expenseDao().insert(expense(id)) }
        assertEquals(4, repo.totalEntryCount())
        db.expenseDao().delete(expense(id).copy(id = ids.first()))
        assertEquals(3, repo.totalEntryCount())
    }

    // ---- Garage rest point end to end ----

    private fun coordinator(access: ProAccess, policy: ProPromptPolicy, entries: Int = 10) =
        ProPromptCoordinator(policy, { ProPromptConfig() }, { access }, { entries }, NoOpAnalytics)

    private fun readyPolicy(sessions: Int): ProPromptPolicy {
        val clock = install + 4 * day
        val policy = ProPromptPolicy({ clock }, InMemoryProPromptStateStore())
        runBlocking { repeat(sessions) { policy.startSession(install) } }
        return policy
    }

    private fun garageWith(vm: GarageViewModel) = rule.setContent {
        WheelfileTheme(darkTheme = false) {
            val state by vm.state.collectAsState()
            val prompt by vm.proPrompt.collectAsState()
            val price by vm.price.collectAsState()
            GarageScreen(
                state = state,
                actions = GarageActions({}, {}, {}, {}, { _, _ -> }, {}),
                adSlot = {},
                proPrompt = prompt,
                proPrice = price,
                onProPromptCta = { vm.onProPromptCta(); ctaTaps++ },
                onProPromptDismiss = vm::dismissProPrompt,
            )
        }
    }

    @Test fun garage_showsAPendingConvertedCardAndDismissCountsAsACardDismissal() {
        runBlocking { db.vehicleDao().insert(vehicle("A")) }
        val policy = readyPolicy(sessions = 3)
        // A remove_ads owner saved a service as the 10th entry: the sheet was converted to a card.
        val coordinator = coordinator(ProAccess.REMOVE_ADS, policy)
        runBlocking { coordinator.onEntrySaved(SaveKind.SERVICE) }
        val vm = GarageViewModel(repo, coordinator, kotlinx.coroutines.flow.MutableStateFlow("PRICE")) { 10 }
        garageWith(vm)
        assertNull(vm.proPrompt.value)
        vm.onRestPoint()
        rule.waitForIdle()
        rule.onNodeWithTag(PRO_PROMPT_CARD_TAG).assertIsDisplayed()
        rule.onNodeWithText("10 entries logged").assertIsDisplayed()
        rule.onNodeWithText("See Pro · PRICE").assertIsDisplayed()
        assertEquals(ProPromptSurface.CARD, vm.proPrompt.value?.prompt?.surface)

        rule.onNodeWithTag(PRO_PROMPT_CLOSE_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(PRO_PROMPT_CARD_TAG).assertDoesNotExist()
        assertEquals(1, policy.current.dismissedCounts["entries_milestone:card"])
        assertEquals(0, policy.current.sheetDismissals)
    }

    @Test fun garage_showsNothingWhenNothingIsPending_andNeverForProOwners() {
        runBlocking { db.vehicleDao().insert(vehicle("A")) }
        val policy = readyPolicy(sessions = 3)
        val pro = coordinator(ProAccess.PRO, policy)
        runBlocking { pro.onEntrySaved(SaveKind.FILL_UP) }
        val vm = GarageViewModel(repo, pro, kotlinx.coroutines.flow.MutableStateFlow(null)) { 10 }
        garageWith(vm)
        vm.onRestPoint()
        rule.waitForIdle()
        rule.onNodeWithTag(PRO_PROMPT_CARD_TAG).assertDoesNotExist()
        assertNotNull(policy.current.pending["entries_milestone"])
    }

    // ---- Rest points: only after a completed save, never at launch or on plain navigation (spec §5.4a) ----

    @Test fun garage_coldStartShowsNoPromptAndTheCardStaysPending() {
        runBlocking { db.vehicleDao().insert(vehicle("A")) }
        val policy = readyPolicy(sessions = 3)
        runBlocking { coordinator(ProAccess.FREE, policy).onVehicleAdded(2) }
        runBlocking { policy.startSession(install) } // the next session: the process restarted
        // A fresh process: a new coordinator has no completed save.
        val vm = GarageViewModel(repo, coordinator(ProAccess.FREE, policy), kotlinx.coroutines.flow.MutableStateFlow(null)) { 10 }
        garageWith(vm)
        vm.onResumed() // the launch resume
        rule.waitForIdle()
        rule.onNodeWithTag(PRO_PROMPT_CARD_TAG).assertDoesNotExist()
        assertNotNull(policy.current.pending["second_vehicle_added"])
        vm.onResumed() // plain navigation back to the Garage
        rule.waitForIdle()
        rule.onNodeWithTag(PRO_PROMPT_CARD_TAG).assertDoesNotExist()
        assertNotNull(policy.current.pending["second_vehicle_added"])
    }

    @Test fun garage_backAfterASaveShowsThePendingCard() {
        runBlocking { db.vehicleDao().insert(vehicle("A")) }
        val policy = readyPolicy(sessions = 3)
        runBlocking { coordinator(ProAccess.FREE, policy).onVehicleAdded(2) }
        runBlocking { policy.startSession(install) }
        val coordinator = coordinator(ProAccess.FREE, policy, entries = 3)
        val vm = GarageViewModel(repo, coordinator, kotlinx.coroutines.flow.MutableStateFlow(null)) { 10 }
        garageWith(vm)
        vm.onResumed() // launch: nothing
        rule.waitForIdle()
        rule.onNodeWithTag(PRO_PROMPT_CARD_TAG).assertDoesNotExist()
        runBlocking { coordinator.onEntrySaved(SaveKind.FILL_UP) } // a save in this session, then Back to the Garage
        vm.onResumed()
        rule.waitForIdle()
        rule.onNodeWithText("Compare your vehicles with Pro").assertIsDisplayed()
    }

    private fun overviewVm(coordinator: ProPromptCoordinator): VehicleDetailViewModel {
        val id = runBlocking { db.vehicleDao().insert(vehicle("A")) }
        return VehicleDetailViewModel(repo, id, { false }, { now }, proPrompts = coordinator, entriesThreshold = { 10 })
    }

    @Test fun overview_openedFromGarageWithoutASaveShowsNoSheet() {
        val policy = readyPolicy(sessions = 3)
        val coordinator = coordinator(ProAccess.FREE, policy)
        runBlocking { coordinator.onEntrySaved(SaveKind.SERVICE) } // the 10th entry earned a sheet...
        val fresh = coordinator(ProAccess.FREE, policy) // ...but this is a new process: no save in it
        val vm = overviewVm(fresh)
        var sheetCount: Int? = null
        rule.setContent { sheetCount = vm.state.collectAsState().value.proSheetCount }
        vm.onOverviewResumed()
        rule.waitForIdle()
        assertNull(sheetCount)
        assertNotNull(policy.current.pending["entries_milestone"])
    }

    @Test fun overview_backAfterAServiceSaveAtTheThresholdShowsTheSheet() {
        val policy = readyPolicy(sessions = 3)
        val coordinator = coordinator(ProAccess.FREE, policy)
        val vm = overviewVm(coordinator)
        var sheetCount: Int? = null
        rule.setContent { sheetCount = vm.state.collectAsState().value.proSheetCount }
        vm.onOverviewResumed() // opening Overview: nothing
        rule.waitForIdle()
        assertNull(sheetCount)
        runBlocking { coordinator.onEntrySaved(SaveKind.SERVICE) }
        vm.onOverviewResumed()
        // The state is built from Room flows on background threads, so wait for it to emit.
        rule.waitUntil(timeoutMillis = 5_000) { sheetCount != null }
        assertEquals(10, sheetCount)
    }

    @Test fun garage_ctaOpensThePaywallHook() {
        runBlocking { db.vehicleDao().insert(vehicle("A")) }
        val policy = readyPolicy(sessions = 3)
        val coordinator = coordinator(ProAccess.FREE, policy)
        runBlocking { coordinator.onVehicleAdded(2) }
        runBlocking { policy.startSession(install) } // the next session
        val vm = GarageViewModel(repo, coordinator, kotlinx.coroutines.flow.MutableStateFlow(null)) { 10 }
        garageWith(vm)
        vm.onRestPoint()
        rule.waitForIdle()
        rule.onNodeWithText("Compare your vehicles with Pro").assertIsDisplayed()
        rule.onNodeWithText("See Pro").assertIsDisplayed()
        rule.onNodeWithTag(PRO_PROMPT_CTA_TAG).performClick()
        assertEquals(1, ctaTaps)
    }
}
