package com.postpole.wheelfile.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.DocumentRepository
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.attachments.SourceOpener
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.document.Region
import com.postpole.wheelfile.ui.document.DOCUMENT_DETAIL_TAG
import com.postpole.wheelfile.ui.document.DocumentDetailScreen
import com.postpole.wheelfile.ui.document.DocumentDetailState
import com.postpole.wheelfile.ui.report.CHART_COST_TAG
import com.postpole.wheelfile.ui.report.REPORTS_LOCKED_TAG
import com.postpole.wheelfile.ui.theme.WheelfileTheme
import com.postpole.wheelfile.ui.vehicle.AddVehicleScreen
import com.postpole.wheelfile.ui.vehicle.AddVehicleStep
import com.postpole.wheelfile.ui.vehicle.AddVehicleViewModel
import com.postpole.wheelfile.ui.vehicle.VehicleDetailActions
import com.postpole.wheelfile.ui.vehicle.VehicleDetailScreen
import com.postpole.wheelfile.ui.vehicle.VehicleDetailViewModel
import com.postpole.wheelfile.ui.vehicle.VehicleTab
import com.postpole.wheelfile.ui.vehicle.toActions
import com.postpole.wheelfile.analytics.NoOpAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DocumentsReportsUiTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val temp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repo: VehicleRepository
    private lateinit var documents: DocumentRepository
    private val previousLocale = Locale.getDefault()
    private val zone = ZoneId.systemDefault()
    private val now = LocalDateTime.of(2026, 6, 15, 10, 0).atZone(zone).toInstant().toEpochMilli()
    private var vehicleId = 0L
    private var seePro = 0

    @Before
    fun setUp() {
        Locale.setDefault(Locale.GERMANY)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = VehicleRepository(db.vehicleDao(), db.energyLogDao(), db.serviceRecordDao(), db.expenseDao())
        val store = AttachmentStore(
            dir = File(temp.newFolder("files"), "attachments"), dao = db.attachmentDao(),
            opener = SourceOpener { ByteArrayInputStream(ByteArray(1)) }, mimeOf = { "application/pdf" }, isPro = { false }, clock = { 1L },
        )
        documents = DocumentRepository(
            db.vehicleDocumentDao(), db.reminderDao(), db.attachmentDao(), store,
            com.postpole.wheelfile.data.RoomTransactor(db), com.postpole.wheelfile.data.deleter(db, store),
        )
        vehicleId = runBlocking {
            val id = db.vehicleDao().insert(
                Vehicle(
                    name = "Golf", type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
                    volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", year = 2021, createdAt = 1, updatedAt = 1,
                ),
            )
            // A cost inside the 12-month window and one that is older than it.
            fun expense(at: LocalDateTime, minor: Long) = Expense(
                vehicleId = id, dateTime = at.atZone(zone).toInstant().toEpochMilli(), category = ExpenseCategory.PARKING,
                amountMinor = minor, createdAt = 1, updatedAt = 1,
            )
            db.expenseDao().insert(expense(LocalDateTime.of(2026, 4, 2, 9, 0), 4_000))
            db.expenseDao().insert(expense(LocalDateTime.of(2024, 1, 2, 9, 0), 9_000))
            documents.save(
                VehicleDocument(
                    vehicleId = id, type = DocumentType.INSURANCE, title = "Car insurance", number = "P-123",
                    expiryDate = LocalDateTime.of(2026, 6, 30, 0, 0).atZone(zone).toInstant().toEpochMilli(),
                ),
            )
            id
        }
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        Locale.setDefault(previousLocale)
    }

    private fun policy() = AdsPolicy(clock = { now }, installTimeMs = 0L, isFirstSession = false)

    private fun detail(initial: VehicleTab, pro: Boolean = false, policy: AdsPolicy = policy()) {
        val vm = VehicleDetailViewModel(
            repo, vehicleId, { pro }, { now }, documents = documents, proFlow = flowOf(pro), attachmentTotal = flowOf(3), adsPolicy = policy,
        )
        rule.setContent { Detail(vm, initial) }
    }

    @Composable
    private fun Detail(vm: VehicleDetailViewModel, initial: VehicleTab) {
        val state by vm.state.collectAsState()
        WheelfileTheme(darkTheme = false) {
            VehicleDetailScreen(
                state = state,
                actions = VehicleDetailActions(
                    {}, {}, {}, vm::setFilter, {}, {}, {}, { seePro++ }, {}, {},
                    onTabViewed = vm::onTabViewed, onRange = vm::setRange, onCustomRange = vm::setCustomRange,
                    onApplyPresets = vm::applyPresets,
                ),
                historyAdSlot = { Box(Modifier.testTag("AdSlot")) },
                documentsAdSlot = { Box(Modifier.testTag("AdSlot")) },
                reportsAdSlot = { Box(Modifier.testTag("AdSlot")) },
                initialTab = initial,
            )
        }
    }

    private fun waitFor(text: String) = rule.waitUntil(5_000) { rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    // ---- ads: banner on the list and Reports only ----

    @Test fun documentsListHasTheBanner() {
        detail(VehicleTab.DOCUMENTS)
        waitFor("Car insurance")
        rule.onNodeWithTag("AdSlot").assertExists()
        rule.onNodeWithText("Vehicle papers", ignoreCase = true).assertExists()
    }

    @Test fun documentDetailHasNoBanner() {
        val doc = runBlocking { db.vehicleDocumentDao().getAll().single() }
        rule.setContent {
            WheelfileTheme(darkTheme = false) {
                // The detail screen takes no ad slot at all: a banner here would be a code change, and this test would fail.
                Box(Modifier.testTag("Host")) {
                    DocumentDetailScreen(DocumentDetailState(loaded = true, document = doc), now, { File("none") }, {}, {}, {})
                }
            }
        }
        rule.onNodeWithTag(DOCUMENT_DETAIL_TAG).assertExists()
        rule.onNodeWithText("Car insurance").assertExists()
        rule.onNodeWithTag("AdSlot").assertDoesNotExist()
    }

    @Test fun overviewIsAdFreeAndReportsHasTheBanner() {
        detail(VehicleTab.OVERVIEW)
        rule.onNodeWithTag("AdSlot").assertDoesNotExist()
        rule.onNodeWithText("Reports").performClick()
        rule.onNodeWithTag("AdSlot").assertExists()
        rule.onNodeWithText("Documents").performClick()
        rule.onNodeWithTag("AdSlot").assertExists()
    }

    // ---- Documents list content ----

    @Test fun documentRowShowsAnExpiryPillAndTheFreeAttachmentCounter() {
        detail(VehicleTab.DOCUMENTS)
        waitFor("Car insurance")
        rule.onNodeWithText("Expires in 15 days").assertExists()
        rule.onNodeWithText("3 of 30 free attachments used").performScrollTo().assertIsDisplayed()
    }

    @Test fun proUsersSeeNoFreeCapCounter() {
        detail(VehicleTab.DOCUMENTS, pro = true)
        waitFor("Car insurance")
        rule.onNodeWithText("3 attachments").assertExists()
        rule.onNodeWithText("3 of 30 free attachments used").assertDoesNotExist()
    }

    @Test fun presetsSheetShowsTheRegionListAndTheDisclaimer() {
        detail(VehicleTab.DOCUMENTS)
        waitFor("Car insurance")
        rule.onNodeWithText("Suggested documents").performScrollTo().performClick()
        waitFor("HU (TÜV)") // Locale.GERMANY
        rule.onNodeWithText("Suggested schedule; check your local authority", substring = true).assertExists()
    }

    // ---- Reports ----

    @Test fun freeUsersSeeTheTwelveMonthChartAndLockedChipsShowAnInlineCard() {
        detail(VehicleTab.REPORTS)
        waitFor("Monthly cost")
        // Only the 4,000 cost in the window counts; the older 9,000 does not.
        rule.onNodeWithTag(CHART_COST_TAG).assertContentDescriptionContains("40", substring = true)
        rule.onNodeWithTag(REPORTS_LOCKED_TAG).assertDoesNotExist()

        rule.onNodeWithText("All time").performClick()
        rule.onNodeWithTag(REPORTS_LOCKED_TAG).assertExists()
        rule.onNodeWithText("See Pro").assertExists()
        // The 12-month chart stays visible below the card.
        rule.onNodeWithTag(CHART_COST_TAG).assertExists()
        rule.onNodeWithTag("AdSlot").assertExists()
        assertEquals(0, seePro)
        rule.onNodeWithText("See Pro").performClick()
        assertEquals(1, seePro)

        rule.onNodeWithText("Compare").performClick()
        rule.onNodeWithTag(REPORTS_LOCKED_TAG).assertExists()
        rule.onNodeWithText("Custom").performClick()
        rule.onNodeWithTag(REPORTS_LOCKED_TAG).assertExists()
        rule.onNodeWithText("12 months").performClick()
        rule.onNodeWithTag(REPORTS_LOCKED_TAG).assertDoesNotExist()
    }

    @Test fun proUsersSeeAllRangesWithoutALockedCard() {
        detail(VehicleTab.REPORTS, pro = true)
        waitFor("Monthly cost")
        rule.onNodeWithText("All time").performClick()
        rule.onNodeWithTag(REPORTS_LOCKED_TAG).assertDoesNotExist()
        rule.onNodeWithTag(CHART_COST_TAG).assertContentDescriptionContains("130", substring = true) // 40 + 90 across all time
        rule.onNodeWithText("Compare").performClick()
        waitFor("Compare vehicles")
        rule.onNodeWithTag(REPORTS_LOCKED_TAG).assertDoesNotExist()
        rule.onNodeWithText("Custom").performClick()
        waitFor("From")
        rule.onNodeWithTag(REPORTS_LOCKED_TAG).assertDoesNotExist()
    }

    @Test fun everyChartCardCarriesItsSummaryAsTheContentDescription() {
        detail(VehicleTab.REPORTS)
        waitFor("Monthly cost")
        rule.onNodeWithTag(CHART_COST_TAG).assertContentDescriptionContains("Total cost was", substring = true)
    }

    // ---- reviewed flag (V7 interstitial trigger; nothing is shown yet) ----

    @Test fun leavingAfterViewingHistoryOrReportsRecordsTheReviewedFlag() {
        val p = policy()
        val vm = VehicleDetailViewModel(repo, vehicleId, { false }, { now }, adsPolicy = p)
        vm.onTabViewed(VehicleTab.OVERVIEW)
        vm.onTabViewed(VehicleTab.DOCUMENTS)
        vm.onLeaving()
        assertFalse(p.reviewedBeforeGarage)

        vm.onTabViewed(VehicleTab.REPORTS)
        vm.onLeaving()
        assertTrue(p.reviewedBeforeGarage)

        val history = policy()
        val vm2 = VehicleDetailViewModel(repo, vehicleId, { false }, { now }, adsPolicy = history)
        vm2.onTabViewed(VehicleTab.HISTORY)
        vm2.onLeaving()
        assertTrue(history.reviewedBeforeGarage)
    }

    // ---- onboarding step 3 ----

    @Test fun onboardingStepThreeOffersTheRegionPresetsWithTheDisclaimer() {
        val vm = AddVehicleViewModel(repo, { false }, {}, NoOpAnalytics, Locale.FRANCE) { now }
        assertEquals(Region.FR, vm.state.value.region)
        vm.setName("Clio")
        vm.onContinue()
        rule.waitUntil(5_000) { vm.state.value.step == AddVehicleStep.UNITS }
        vm.onUnitsDone()
        assertEquals(AddVehicleStep.REGION, vm.state.value.step)
        rule.setContent {
            val state by vm.state.collectAsState()
            WheelfileTheme(darkTheme = false) { AddVehicleScreen(state, vm.toActions(onClose = {})) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Step 3 of 3").assertExists()
        rule.onNodeWithText("Contrôle technique").assertExists()
        rule.onNodeWithText("Suggested schedule; check your local authority", substring = true).assertExists()
        rule.onNodeWithText("Skip").assertExists()
    }

    @Test fun tickedPresetsBecomeDocumentsWhenTheVehicleIsSaved() {
        var applied = 0
        val vm = AddVehicleViewModel(
            repo, { false }, {}, NoOpAnalytics, Locale.FRANCE,
            { v, plans -> applied = documents.applyPlans(v, plans) },
        ) { now }
        vm.setName("Clio")
        vm.setYear("2022")
        vm.onContinue()
        rule.waitUntil(5_000) { vm.state.value.step == AddVehicleStep.UNITS }
        vm.onUnitsDone()
        vm.togglePreset("FR_CT")
        vm.save()
        rule.waitUntil(5_000) { applied == 1 }
        val clio = runBlocking { db.vehicleDao().getAll().first { it.name == "Clio" } }
        val doc = runBlocking { db.vehicleDocumentDao().getAll().first { it.vehicleId == clio.id } }
        assertEquals("Contrôle technique", doc.title)
        // Registration is assumed on 1 January 2022 (the model year), so the first deadline (4th anniversary,
        // 1 January 2026) has passed on 15 June 2026 and the next one, two years on, is suggested.
        assertEquals(LocalDateTime.of(2028, 1, 1, 0, 0).atZone(zone).toInstant().toEpochMilli(), doc.expiryDate)
    }

    @Test fun skippingThePresetsSavesTheVehicleWithoutDocuments() {
        var applied = false
        val vm = AddVehicleViewModel(repo, { false }, {}, NoOpAnalytics, Locale.FRANCE, { _, _ -> applied = true }) { now }
        vm.setName("Clio")
        vm.onContinue()
        rule.waitUntil(5_000) { vm.state.value.step == AddVehicleStep.UNITS }
        vm.onUnitsDone()
        vm.togglePreset("FR_CT")
        vm.skipPresets()
        rule.waitUntil(5_000) { runBlocking { db.vehicleDao().getAll().any { it.name == "Clio" } } }
        assertFalse(applied)
        assertEquals(0, runBlocking { db.vehicleDocumentDao().getAll().count { it.title == "Contrôle technique" } })
    }
}
