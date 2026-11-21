package com.postpole.wheelfile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.postpole.wheelfile.data.DocumentItem
import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseCategory
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import com.postpole.wheelfile.domain.report.ReportRange
import com.postpole.wheelfile.ui.document.DocumentDetailScreen
import com.postpole.wheelfile.ui.document.DocumentDetailUiState
import com.postpole.wheelfile.ui.document.PresetsScreen
import com.postpole.wheelfile.ui.document.PresetsUiState
import com.postpole.wheelfile.ui.document.PresetRow
import com.postpole.wheelfile.ui.theme.WheelfileTheme
import com.postpole.wheelfile.ui.vehicle.VehicleDetailActions
import com.postpole.wheelfile.ui.vehicle.VehicleDetailScreen
import com.postpole.wheelfile.ui.vehicle.VehicleDetailUiState
import com.postpole.wheelfile.ui.vehicle.VehicleTab
import com.postpole.wheelfile.domain.document.PresetDue
import com.postpole.wheelfile.domain.document.PresetRegion
import com.postpole.wheelfile.domain.document.RegionPresets
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class DocumentsAndReportsUiTest {
    @get:Rule val rule = createComposeRule()

    private val previousLocale = Locale.getDefault()
    private val dayMs = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    @Before fun setUp() = Locale.setDefault(Locale.US)

    @After fun tearDown() = Locale.setDefault(previousLocale)

    private fun vehicle() = Vehicle(
        id = 1, name = "Golf", type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", createdAt = 1, updatedAt = 1,
    )

    private fun fillUp(id: Long, daysAgo: Long, odometer: Double, quantity: Double) = EnergyLog(
        id = id, vehicleId = 1, stream = EnergyStream.LIQUID, dateTime = now - daysAgo * dayMs, odometer = odometer,
        quantity = quantity, pricePerUnitMinor = 178_900, totalMinor = (quantity * 179).toLong(), isFull = true, createdAt = 1, updatedAt = 1,
    )

    private fun data(): VehicleData {
        val v = vehicle()
        val logs = listOf(fillUp(1, 60, 1_000.0, 40.0), fillUp(2, 30, 1_500.0, 30.0), fillUp(3, 5, 2_000.0, 35.0))
        val expenses = listOf(
            Expense(id = 1, vehicleId = 1, dateTime = now - 10 * dayMs, category = ExpenseCategory.INSURANCE, amountMinor = 40_000, createdAt = 1, updatedAt = 1),
        )
        return VehicleData(v, logs, emptyList(), expenses, VehicleStatsCalculator.compute(v, logs, emptyList(), expenses))
    }

    private val document = VehicleDocument(
        id = 7, vehicleId = 1, type = DocumentType.INSURANCE, title = "Car insurance policy",
        expiryDate = now + 10 * dayMs,
    )

    private fun documentsState(items: List<DocumentItem> = listOf(DocumentItem(document, 1))) =
        VehicleDetailUiState(data = data(), switcher = listOf(vehicle()), loaded = true, documents = items)

    private fun detailScreen(state: VehicleDetailUiState, tab: VehicleTab) = rule.setContent {
        WheelfileTheme(darkTheme = false) {
            VehicleDetailScreen(
                state = state,
                actions = VehicleDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                adSlot = { Box(Modifier.testTag("AdSlot")) },
                initialTab = tab,
            )
        }
    }

    // ---- Ads: Documents list yes, Document detail never ----

    @Test fun documentsListHasTheBannerSlot() {
        detailScreen(documentsState(), VehicleTab.DOCUMENTS)
        rule.onNodeWithTag("AdSlot").assertExists()
        rule.onNodeWithText("Car insurance policy").assertExists()
    }

    @Test fun documentDetailHasNoBannerSlot() {
        rule.setContent {
            WheelfileTheme(darkTheme = false) {
                DocumentDetailScreen(
                    state = DocumentDetailUiState(loaded = true, document = document, vehicle = vehicle()),
                    now = now,
                    onBack = {}, onEdit = {}, onDelete = {},
                )
            }
        }
        rule.onNodeWithText("Car insurance policy").assertExists()
        rule.onNodeWithTag("AdSlot").assertDoesNotExist()
    }

    // ---- Documents list content ----

    @Test fun expiringDocumentShowsAWarningPillWithTheDaysLeft() {
        detailScreen(documentsState(), VehicleTab.DOCUMENTS)
        rule.onNodeWithText("Expires in 10 days").assertExists()
    }

    @Test fun expiredDocumentShowsTheExpiredPill() {
        val expired = document.copy(expiryDate = now - 3 * dayMs)
        detailScreen(documentsState(listOf(DocumentItem(expired, 0))), VehicleTab.DOCUMENTS)
        rule.onNodeWithText("Expired").assertExists()
    }

    @Test fun documentsOfferPresetsWithTheDisclaimerWhenEmpty() {
        detailScreen(documentsState(emptyList()), VehicleTab.DOCUMENTS)
        rule.onNodeWithText("No documents yet").assertExists()
        rule.onNodeWithText("Add suggested documents").assertExists()
        rule.onNodeWithText("Suggested schedule; check your local authority", substring = true).assertExists()
    }

    @Test fun freeUsersSeeTheAttachmentCounterAndProUsersDont() {
        detailScreen(documentsState().copy(attachmentCount = 13), VehicleTab.DOCUMENTS)
        rule.onNodeWithText("Attachments used: 13 of 30 on the free plan").assertExists()
    }

    @Test fun proUsersDoNotSeeTheAttachmentCounter() {
        detailScreen(documentsState().copy(attachmentCount = 13, isPro = true), VehicleTab.DOCUMENTS)
        rule.onNodeWithText("Attachments used: 13 of 30 on the free plan").assertDoesNotExist()
    }

    // ---- Presets ----

    @Test fun presetsScreenShowsTheDisclaimerAndTheRegionPresets() {
        val rows = RegionPresets.forRegion(PresetRegion.FR).map { PresetRow(it, it.region == PresetRegion.FR, PresetDue(null)) }
        rule.setContent {
            WheelfileTheme(darkTheme = false) {
                PresetsScreen(
                    state = PresetsUiState(vehicle = vehicle(), region = PresetRegion.FR, rows = rows),
                    onboarding = true,
                    onRegion = {}, onToggle = {}, onFirstRegistration = {}, onClearFirstRegistration = {}, onAdd = {}, onSkip = {},
                )
            }
        }
        rule.onNodeWithText("Step 3 of 3").assertExists()
        rule.onNodeWithText("Suggested schedule; check your local authority", substring = true).assertExists()
        rule.onNodeWithText("Contrôle technique").assertExists()
    }

    // ---- Reports ----

    @Composable
    private fun ReportsHarness(isPro: Boolean) {
        var range by remember { mutableStateOf(ReportRange.LAST_12_MONTHS) }
        WheelfileTheme(darkTheme = false) {
            VehicleDetailScreen(
                state = documentsState().copy(isPro = isPro, reportRange = range),
                actions = VehicleDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onReportRange = { range = it }),
                adSlot = { Box(Modifier.testTag("AdSlot")) },
                initialTab = VehicleTab.REPORTS,
            )
        }
    }

    @Test fun reportsTabHasTheBannerAndAllRangeChipsAreVisible() {
        rule.setContent { ReportsHarness(isPro = false) }
        rule.onNodeWithTag("AdSlot").assertExists()
        listOf("12 months", "All time", "Compare", "Custom").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText("See Pro").assertDoesNotExist()
    }

    @Test fun everyChartHasAOneSentenceSummaryAsItsContentDescription() {
        rule.setContent { ReportsHarness(isPro = false) }
        rule.onNode(hasContentDescription("Economy chart", substring = true)).assertExists()
        rule.onNode(hasContentDescription("Monthly cost chart in EUR", substring = true)).assertExists()
        rule.onNode(hasContentDescription("Price per unit chart in EUR", substring = true)).assertExists()
    }

    @Test fun freeUserTappingAllTimeSeesTheInlineProCardAndKeepsTheTwelveMonthCharts() {
        rule.setContent { ReportsHarness(isPro = false) }
        rule.onNodeWithText("All time").performClick()
        rule.onNodeWithText("Longer ranges are part of Pro").assertExists()
        rule.onNodeWithText("See Pro").assertExists()
        rule.onNode(hasContentDescription("Monthly cost chart in EUR", substring = true)).assertExists()
        rule.onNodeWithTag("AdSlot").assertExists()
    }

    @Test fun compareAndCustomAreLockedOnTheFreePlanToo() {
        rule.setContent { ReportsHarness(isPro = false) }
        rule.onNodeWithText("Compare").performClick()
        rule.onNodeWithText("See Pro").assertExists()
        rule.onNodeWithText("Custom").performClick()
        rule.onNodeWithText("See Pro").assertExists()
    }

    @Test fun proUserSeesEveryRangeWithoutTheLockedCard() {
        rule.setContent { ReportsHarness(isPro = true) }
        rule.onNodeWithText("All time").performClick()
        rule.onNodeWithText("See Pro").assertDoesNotExist()
        rule.onNode(hasContentDescription("Monthly cost chart in EUR", substring = true)).assertExists()
        rule.onNodeWithText("Custom").performClick()
        rule.onNodeWithText("See Pro").assertDoesNotExist()
        rule.onNodeWithText("Pick a start and an end date.").assertExists()
    }
}
