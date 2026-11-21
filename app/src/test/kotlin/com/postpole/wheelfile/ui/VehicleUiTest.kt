package com.postpole.wheelfile.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.analytics.NoOpAnalytics
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import com.postpole.wheelfile.ui.entry.EntryEvent
import com.postpole.wheelfile.ui.entry.EntryFormScreen
import com.postpole.wheelfile.ui.entry.EntryFormViewModel
import com.postpole.wheelfile.ui.entry.toActions
import com.postpole.wheelfile.ui.garage.GarageActions
import com.postpole.wheelfile.ui.garage.GarageScreen
import com.postpole.wheelfile.ui.garage.GarageUiState
import com.postpole.wheelfile.ui.theme.WheelfileTheme
import com.postpole.wheelfile.ui.vehicle.AddVehicleEvent
import com.postpole.wheelfile.ui.vehicle.AddVehicleScreen
import com.postpole.wheelfile.ui.vehicle.AddVehicleViewModel
import com.postpole.wheelfile.ui.vehicle.VehicleDetailActions
import com.postpole.wheelfile.ui.vehicle.VehicleDetailScreen
import com.postpole.wheelfile.ui.vehicle.VehicleDetailViewModel
import com.postpole.wheelfile.ui.vehicle.toActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VehicleUiTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: VehicleRepository
    private val previousLocale = Locale.getDefault()
    private val now = 1_700_000_000_000L + 10L * 24 * 60 * 60 * 1000

    @Before
    fun setUp() {
        Locale.setDefault(Locale.GERMANY)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = VehicleRepository(db.vehicleDao(), db.energyLogDao(), db.serviceRecordDao(), db.expenseDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        Locale.setDefault(previousLocale)
    }

    private val noGarageActions = GarageActions({}, {}, {}, {}, { _, _ -> }, {})

    private fun vehicle() = Vehicle(
        id = 1, name = "Golf", type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", createdAt = 1, updatedAt = 1,
    )

    private fun vehicleData(): VehicleData {
        val v = vehicle()
        return VehicleData(v, emptyList(), emptyList(), emptyList(), VehicleStatsCalculator.compute(v, emptyList()))
    }

    private fun garage(state: GarageUiState) = rule.setContent {
        WheelfileTheme(darkTheme = false) {
            GarageScreen(state, noGarageActions, adSlot = { Box(Modifier.testTag("AdSlot")) })
        }
    }

    // ---- Ads placement ----

    @Test fun emptyGarageShowsNoAdSlot() {
        garage(GarageUiState.Empty)
        rule.onNodeWithTag("AdSlot").assertDoesNotExist()
        rule.onNodeWithText("No vehicles yet").assertExists()
    }

    @Test fun loadingGarageShowsNoAdSlot() {
        garage(GarageUiState.Loading)
        rule.onNodeWithTag("AdSlot").assertDoesNotExist()
    }

    @Test fun garageWithAVehicleShowsTheAdSlot() {
        garage(GarageUiState.Content(listOf(vehicleData()), emptyList(), 1))
        rule.onNodeWithTag("AdSlot").assertExists()
    }

    @Test fun overviewIsAdFreeAndHistoryDocumentsAndReportsHaveTheBanner() {
        rule.setContent {
            WheelfileTheme(darkTheme = false) {
                VehicleDetailScreen(
                    state = com.postpole.wheelfile.ui.vehicle.VehicleDetailUiState(data = vehicleData(), switcher = listOf(vehicle()), loaded = true),
                    actions = VehicleDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                    adSlot = { Box(Modifier.testTag("AdSlot")) },
                )
            }
        }
        rule.onNodeWithTag("AdSlot").assertDoesNotExist()
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithTag("AdSlot").assertExists()
        rule.onNodeWithText("Documents").performClick()
        rule.onNodeWithTag("AdSlot").assertExists()
        rule.onNodeWithText("Reports").performClick()
        rule.onNodeWithTag("AdSlot").assertExists()
        rule.onNodeWithText("Overview").performClick()
        rule.onNodeWithTag("AdSlot").assertDoesNotExist()
    }

    // ---- Add vehicle -> add fill-ups -> Overview economy card updates ----

    private sealed interface Screen {
        data object Add : Screen
        data class Overview(val id: Long) : Screen
        data class Entry(val id: Long) : Screen
    }

    @Composable
    private fun Harness() {
        var screen by remember { mutableStateOf<Screen>(Screen.Add) }
        WheelfileTheme(darkTheme = false) {
            when (val s = screen) {
                Screen.Add -> {
                    val vm = remember {
                        AddVehicleViewModel(repo, { false }, {}, NoOpAnalytics, Locale.GERMANY) { now }
                    }
                    LaunchedEffect(vm) {
                        vm.events.collect { if (it is AddVehicleEvent.Saved) screen = Screen.Overview(it.vehicleId) }
                    }
                    val state by vm.state.collectAsState()
                    AddVehicleScreen(state, vm.toActions(onClose = {}))
                }
                is Screen.Overview -> {
                    val vm = remember(s.id) { VehicleDetailViewModel(repo, s.id, { false }, { now }) }
                    val state by vm.state.collectAsState()
                    VehicleDetailScreen(
                        state = state,
                        actions = VehicleDetailActions({}, {}, { screen = Screen.Entry(s.id) }, vm::setFilter, {}, {}, {}, {}, {}, {}),
                        adSlot = {},
                    )
                }
                is Screen.Entry -> {
                    val vm = remember(s.id) {
                        EntryFormViewModel(repo, AdsPolicy({ now }, 0, false), NoOpAnalytics, s.id, false) { now }
                    }
                    LaunchedEffect(vm) {
                        vm.events.collect { if (it == EntryEvent.Saved) screen = Screen.Overview(s.id) }
                    }
                    val state by vm.state.collectAsState()
                    EntryFormScreen(state, SnackbarHostState(), vm.toActions(onClose = {}))
                }
            }
        }
    }

    private fun typeInto(label: String, text: String) {
        rule.onNodeWithText(label).performScrollTo().performTextInput(text)
    }

    private fun logFillUp(odometer: String, volume: String, total: String) {
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Odometer (km)")).fetchSemanticsNodes().isNotEmpty() }
        typeInto("Odometer (km)", odometer)
        typeInto("Volume (L)", volume)
        typeInto("Total cost (EUR)", total)
        rule.onNodeWithText("Save").performClick()
    }

    @Test fun addVehicleThenFillUpsUpdatesTheOverviewEconomyCard() {
        rule.setContent { Harness() }

        typeInto("Name", "Golf")
        rule.onNodeWithText("Continue").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Save vehicle")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Save vehicle").performClick()

        // Overview of the new vehicle: no economy yet.
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Log two full tanks and the economy shows here.")).fetchSemanticsNodes().isNotEmpty() }

        rule.onNode(hasText("Fill-up") and hasClickAction()).performClick()
        logFillUp("10000", "40", "70")
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Log two full tanks and the economy shows here.")).fetchSemanticsNodes().isNotEmpty() }

        rule.onNode(hasText("Fill-up") and hasClickAction()).performClick()
        logFillUp("10500", "30", "52")

        // 30 L over 500 km = 6 L/100 km.
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("6 L/100 km")).fetchSemanticsNodes().isNotEmpty() }
        // Shown on the economy card and on the closing history row.
        rule.onAllNodes(hasText("6 L/100 km")).onFirst().assertExists()
        rule.onNodeWithTag("AdSlot").assertDoesNotExist()
    }
}
