package com.postpole.wheelfile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.ads.AdScreen
import com.postpole.wheelfile.ads.AdSlotState
import com.postpole.wheelfile.ads.AdUnits
import com.postpole.wheelfile.ads.AdsConfig
import com.postpole.wheelfile.ads.BannerLoad
import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import com.postpole.wheelfile.ui.garage.GarageActions
import com.postpole.wheelfile.ui.garage.GarageScreen
import com.postpole.wheelfile.ui.garage.GarageUiState
import com.postpole.wheelfile.ui.pro.PAYWALL_PRO_TAG
import com.postpole.wheelfile.ui.pro.PaywallActions
import com.postpole.wheelfile.ui.pro.PaywallScreen
import com.postpole.wheelfile.ui.pro.PaywallState
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.WheelfileTheme
import com.postpole.wheelfile.ui.vehicle.VehicleDetailActions
import com.postpole.wheelfile.ui.vehicle.VehicleDetailScreen
import com.postpole.wheelfile.ui.vehicle.VehicleDetailUiState
import com.postpole.wheelfile.ui.vehicle.VehicleTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Banners appear only on Garage (with at least one vehicle) and the History, Documents and Reports tabs
 * (DECISIONS.md items 2-5, spec §5.2). Everything on the never-list is ad-free, and the slot collapses on no fill.
 *
 * Screens that take a banner slot are rendered with a marker in the slot, so the test sees exactly where the app
 * would put an ad. Screens that have no slot parameter at all (forms, Settings, import, paywall...) must not
 * reference the ad composables, which a source check pins down.
 */
@RunWith(RobolectricTestRunner::class)
class AdPlacementTest {
    @get:Rule val rule = createComposeRule()

    private val marker = "AdSlot"
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
            GarageScreen(state, noGarageActions, adSlot = { Box(Modifier.testTag(marker)) })
        }
    }

    private fun detail(tab: VehicleTab) = rule.setContent {
        WheelfileTheme(darkTheme = false) {
            VehicleDetailScreen(
                state = VehicleDetailUiState(data = vehicleData(), switcher = listOf(vehicle()), loaded = true),
                actions = VehicleDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                historyAdSlot = { Box(Modifier.testTag("$marker-history")) },
                documentsAdSlot = { Box(Modifier.testTag("$marker-documents")) },
                reportsAdSlot = { Box(Modifier.testTag("$marker-reports")) },
                initialTab = tab,
            )
        }
    }

    // ---- Where a banner is allowed ----

    @Test fun garageWithAVehicle_hasTheBanner() {
        garage(GarageUiState.Content(listOf(vehicleData()), emptyList(), 1))
        rule.onNodeWithTag(marker).assertExists()
    }

    @Test fun emptyAndLoadingGarage_haveNoBanner() {
        garage(GarageUiState.Empty)
        rule.onNodeWithTag(marker).assertDoesNotExist()
    }

    @Test fun loadingGarage_hasNoBanner() {
        garage(GarageUiState.Loading)
        rule.onNodeWithTag(marker).assertDoesNotExist()
    }

    @Test fun overviewTab_hasNoBanner() {
        detail(VehicleTab.OVERVIEW)
        listOf("history", "documents", "reports").forEach { rule.onNodeWithTag("$marker-$it").assertDoesNotExist() }
    }

    @Test fun historyTab_hasExactlyTheHistoryBanner() = onlyBanner(VehicleTab.HISTORY, "history")

    @Test fun documentsTab_hasExactlyTheDocumentsBanner() = onlyBanner(VehicleTab.DOCUMENTS, "documents")

    @Test fun reportsTab_hasExactlyTheReportsBanner() = onlyBanner(VehicleTab.REPORTS, "reports")

    private fun onlyBanner(tab: VehicleTab, expected: String) {
        detail(tab)
        listOf("history", "documents", "reports").forEach {
            val node = rule.onNodeWithTag("$marker-$it")
            if (it == expected) node.assertExists() else node.assertDoesNotExist()
        }
    }

    @Test fun paywall_hasNoBanner() {
        rule.setContent {
            WheelfileTheme(darkTheme = false) {
                PaywallScreen(PaywallState(proPrice = "p", removeAdsPrice = "r"), PaywallActions({}, {}, {}, {}))
            }
        }
        rule.onNodeWithTag(PAYWALL_PRO_TAG).assertExists()
        rule.onNodeWithTag(marker).assertDoesNotExist()
    }

    // ---- The never-list: no ad code at all ----

    private val mainSrc = File("src/main/kotlin/com/postpole/wheelfile")

    private fun usesBannerSlot(file: File): Boolean =
        Regex("\\bAdSlot\\(|\\bAdScreen\\.|InterstitialAd|AdView\\b").containsMatchIn(file.readText())

    @Test fun onlyGarageAndVehicleDetailPlaceBanners() {
        val uses = mainSrc.walkTopDown().filter { it.isFile && it.extension == "kt" && "/ads/" !in it.path }
            .filter(::usesBannerSlot).map { it.name }.toSet()
        // GarageScreen places the Garage banner, VehicleDetailScreen the three tab banners; the preview is debug only.
        assertEquals(setOf("GarageScreen.kt", "VehicleDetailScreen.kt", "AdPreviewScreen.kt"), uses)
    }

    @Test fun neverListScreens_doNotReferenceAds() {
        val neverList = listOf(
            "ui/vehicle/OnboardingScreens.kt", // Welcome, Add vehicle
            "ui/entry/EntryFormScreen.kt", // fill-up and charge
            "ui/entry/RecordFormScreen.kt", // service and expense
            "ui/reminder/RemindersScreen.kt",
            "ui/reminder/ReminderFormScreen.kt",
            "ui/document/DocumentFormScreen.kt",
            "ui/document/DocumentDetail.kt",
            "ui/importer/ImportScreen.kt",
            "ui/settings/SettingsScreen.kt", // Settings, privacy options, export, backup, restore, units, currency
            "ui/pro/ProScreen.kt", // paywall
            "ui/report/PdfReportViewModel.kt", // PDF share
        )
        neverList.forEach { path ->
            val file = File(mainSrc, path)
            assertTrue("$path is missing", file.exists())
            assertTrue("$path must not reference ad code", !usesBannerSlot(file))
        }
    }

    @Test fun interstitialIsOnlyShownFromTheBackToGarageFlow() {
        val callers = mainSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { "onBackToGarage(" in it.readText() }.map { it.name }.toSet()
        assertEquals(setOf("Interstitial.kt", "VehicleDetailViewModel.kt"), callers)
    }

    // ---- Slot behaviour ----

    @Test fun slotCollapsesOnNoFillAndWhenAdsAreHidden() {
        assertEquals(0.dp, AdSlotState.height(allowed = true, load = BannerLoad.NO_FILL))
        assertEquals(0.dp, AdSlotState.height(allowed = false, load = BannerLoad.LOADED))
        assertEquals(Sizes.adSlotTotal, AdSlotState.height(allowed = true, load = BannerLoad.LOADING))
    }

    @Test fun bannersNeedConsentConfigAndNoEntitlement() {
        val c = AdsConfig.DEFAULTS
        assertTrue(AdSlotState.bannersAllowed(canRequestAds = true, config = c, adsHidden = false))
        assertTrue(!AdSlotState.bannersAllowed(canRequestAds = false, config = c, adsHidden = false))
        assertTrue(!AdSlotState.bannersAllowed(canRequestAds = true, config = c, adsHidden = true))
        assertTrue(!AdSlotState.bannersAllowed(canRequestAds = true, config = c.copy(bannerEnabled = false), adsHidden = false))
    }

    @Test fun everyBannerScreenHasItsOwnUnitAndNothingElseDoes() {
        assertEquals(setOf(AdScreen.GARAGE, AdScreen.HISTORY, AdScreen.DOCUMENTS, AdScreen.REPORTS), AdScreen.entries.toSet())
        assertEquals(AdUnits.DEBUG.bannerGarage, AdUnits.current.banner(AdScreen.GARAGE))
    }
}
