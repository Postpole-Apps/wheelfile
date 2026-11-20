package com.postpole.wheelfile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.SavedStateHandle
import com.postpole.wheelfile.TestEnv
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.ui.energy.EnergyFormScreen
import com.postpole.wheelfile.ui.energy.EnergyFormViewModel
import com.postpole.wheelfile.ui.garage.GarageContent
import com.postpole.wheelfile.ui.garage.GarageState
import com.postpole.wheelfile.ui.garage.VehicleCardData
import com.postpole.wheelfile.ui.theme.WheelfileTheme
import com.postpole.wheelfile.ui.vehicle.AddVehicleScreen
import com.postpole.wheelfile.ui.vehicle.VehicleDetailScreen
import com.postpole.wheelfile.ui.vehicle.VehicleDetailViewModel
import com.postpole.wheelfile.ui.vehicle.VehicleFormViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class VehicleFlowUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var env: TestEnv

    @Before
    fun setUp() {
        env = TestEnv(locale = Locale.FRANCE)
    }

    @After
    fun tearDown() = env.close()

    private sealed interface Screen {
        data object AddVehicle : Screen
        data class Energy(val vehicleId: Long) : Screen
        data class Detail(val vehicleId: Long) : Screen
    }

    @Composable
    private fun Flow() {
        var screen: Screen by remember { mutableStateOf(Screen.AddVehicle) }
        WheelfileTheme(darkTheme = false) {
            when (val s = screen) {
                Screen.AddVehicle -> AddVehicleScreen(
                    onClose = {},
                    onSaved = { id, _ -> screen = Screen.Detail(id) },
                    onLimitReached = {},
                    viewModel = remember { formViewModel() },
                )
                is Screen.Energy -> EnergyFormScreen(
                    onClose = { screen = Screen.Detail(s.vehicleId) },
                    onSaved = { screen = Screen.Detail(s.vehicleId) },
                    viewModel = remember(s) { energyViewModel(s.vehicleId) },
                )
                is Screen.Detail -> VehicleDetailScreen(
                    initialTab = 0,
                    onBack = {},
                    onAddEnergy = { id, _ -> screen = Screen.Energy(id) },
                    onSwitchVehicle = {},
                    viewModel = remember(s) { detailViewModel(s.vehicleId) },
                )
            }
        }
    }

    private fun formViewModel() = VehicleFormViewModel(
        env.repository, env.settings, env.pro, env.analytics, env.localeProvider,
        SavedStateHandle(mapOf(VehicleFormViewModel.ARG_ONBOARDING to false)),
    )

    private fun energyViewModel(vehicleId: Long) = EnergyFormViewModel(
        env.repository, env.adsPolicy, env.analytics, env.localeProvider, env.clock,
        SavedStateHandle(
            mapOf(
                EnergyFormViewModel.ARG_VEHICLE_ID to vehicleId,
                EnergyFormViewModel.ARG_STREAM to EnergyStream.LIQUID.name,
            ),
        ),
    )

    private fun detailViewModel(vehicleId: Long) = VehicleDetailViewModel(
        env.repository, env.clock, SavedStateHandle(mapOf(VehicleDetailViewModel.ARG_VEHICLE_ID to vehicleId)),
    )

    private fun addFillUp(odometer: String, litres: String, total: String) {
        compose.onNodeWithText("Fill-up", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("odometer").performTextInput(odometer)
        compose.onNodeWithTag("quantity").performTextInput(litres)
        compose.onNodeWithTag("total").performTextInput(total)
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Fill-up", useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
    }

    @Test
    fun addVehicleThenFillUpsUpdateTheOverviewEconomyCard() {
        compose.setContent { Flow() }

        compose.onNodeWithTag("vehicle_name").performTextInput("Golf")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Save").performClick()

        // Overview with no economy yet.
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Log two full fill-ups to see your economy.").assertIsDisplayed() }.isSuccess
        }

        addFillUp("1000", "40", "70")
        compose.onNodeWithText("Log two full fill-ups to see your economy.").assertIsDisplayed()

        addFillUp("1500", "35", "60")
        // 35 L over 500 km = 7.0 L/100 km
        // Both the "Last" and the "Average" figure read 7.0 with a single window.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("7.0 L/100 km").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithText("Fuel economy").assertIsDisplayed()
        compose.onNodeWithText("Log two full fill-ups to see your economy.").assertDoesNotExist()
    }

    // ---- Garage ad gating ---------------------------------------------------------------------

    private fun garage(state: GarageState) {
        compose.setContent {
            WheelfileTheme(darkTheme = false) {
                GarageContent(
                    state = state,
                    onOpenSettings = {}, onAddVehicle = {}, onSelect = {}, onOpenVehicle = { _, _ -> },
                    onAddEnergy = { _, _ -> }, onRestore = {},
                    banner = { Box(Modifier.testTag("ad_slot")) { Text("ad") } },
                )
            }
        }
    }

    @Test
    fun emptyGarageShowsNoAdSlot() {
        garage(GarageState(loading = false))
        compose.onNodeWithText("Your garage is empty").assertIsDisplayed()
        compose.onNodeWithTag("ad_slot").assertDoesNotExist()
    }

    @Test
    fun loadingGarageShowsNoAdSlot() {
        garage(GarageState(loading = true))
        compose.onNodeWithTag("ad_slot").assertDoesNotExist()
    }

    @Test
    fun garageWithAVehicleShowsTheAdSlot() {
        garage(GarageState(loading = false, active = listOf(VehicleCardData(env.vehicle().copy(id = 1), 1200.0, emptyList()))))
        compose.onNodeWithTag("ad_slot").assertIsDisplayed()
    }
}
