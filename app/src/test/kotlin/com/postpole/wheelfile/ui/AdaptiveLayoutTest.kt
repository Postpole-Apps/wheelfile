package com.postpole.wheelfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.postpole.wheelfile.ui.adaptive.GarageListDetail
import com.postpole.wheelfile.ui.adaptive.isListDetailWidth
import com.postpole.wheelfile.ui.pro.PAYWALL_PRO_TAG
import com.postpole.wheelfile.ui.pro.PAYWALL_REMOVE_ADS_TAG
import com.postpole.wheelfile.ui.pro.PaywallActions
import com.postpole.wheelfile.ui.pro.PaywallScreen
import com.postpole.wheelfile.ui.pro.PaywallState
import com.postpole.wheelfile.ui.theme.WheelfileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Adaptive layouts at compact, medium and expanded widths (spec §3.2): one pane below 600 dp, Garage | Vehicle side
 * by side from 600 dp up. Each test compares its render with a committed golden PNG in `src/test/golden/`
 * ([GoldenImage]) and also writes the latest render to `build/screenshots/`. Pane-layout assertions run first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun garage(selected: Long?) = rule.setContent {
        rootView = LocalView.current
        WheelfileTheme(darkTheme = false) {
            if (isListDetailWidth()) {
                GarageListDetail(
                    selectedVehicleId = selected,
                    listPane = { Box(Modifier.fillMaxSize().background(Color(0xFFE3F2FD)).testTag("list")) },
                    detailPane = { Box(Modifier.fillMaxSize().background(Color(0xFFFFF3E0)).testTag("detail-$it")) },
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFE3F2FD)).testTag("list"))
            }
        }
    }

    /** Draws the window's root view into a PNG. `captureToImage` times out under Robolectric, so draw the view directly. */
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val view = checkNotNull(rootView)
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        // The latest render, for the Play screenshot sets; the committed golden is what the test compares against.
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        GoldenImage.assertMatches(name, bitmap)
    }

    private var rootView: android.view.View? = null

    // ---- Garage | Vehicle ----

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun compact_isSinglePane() {
        garage(selected = 1L)
        rule.onNodeWithTag("list").assertExists()
        rule.onNodeWithTag("detail-1").assertDoesNotExist()
        screenshot("garage-compact")
    }

    @Test @Config(qualifiers = "w700dp-h1000dp-xhdpi")
    fun medium_showsListAndDetailSideBySide() {
        garage(selected = 1L)
        sideBySide()
        screenshot("garage-medium")
    }

    @Test @Config(qualifiers = "w1100dp-h800dp-xhdpi")
    fun expanded_showsListAndDetailSideBySide() {
        garage(selected = 1L)
        sideBySide()
        screenshot("garage-expanded")
    }

    @Test @Config(qualifiers = "w700dp-h1000dp-xhdpi")
    fun wide_withoutASelection_showsAPlaceholderInTheDetailPane() {
        garage(selected = null)
        rule.onNodeWithTag("list").assertExists()
        rule.onNodeWithTag("detail-1").assertDoesNotExist()
        rule.onNodeWithTag("list").getUnclippedBoundsInRoot().let { assertTrue(it.width < 700.dp) }
    }

    private fun sideBySide() {
        val list = rule.onNodeWithTag("list").getUnclippedBoundsInRoot()
        val detail = rule.onNodeWithTag("detail-1").getUnclippedBoundsInRoot()
        assertTrue("list is left of detail: $list $detail", list.right <= detail.left + 1.dp)
        assertTrue("both panes have width", list.width > 100.dp && detail.width > 100.dp)
        assertEquals("panes share one row", list.top, detail.top)
    }

    // ---- Paywall: both options side by side at every width ----

    private fun paywall() = rule.setContent {
        rootView = LocalView.current
        WheelfileTheme(darkTheme = false) {
            PaywallScreen(
                PaywallState(proPrice = "Play price", removeAdsPrice = "Play price"),
                PaywallActions({}, {}, {}, {}),
            )
        }
    }

    private fun bothOptionsShown(name: String) {
        paywall()
        val remove = rule.onNodeWithTag(PAYWALL_REMOVE_ADS_TAG).getUnclippedBoundsInRoot()
        val pro = rule.onNodeWithTag(PAYWALL_PRO_TAG).getUnclippedBoundsInRoot()
        assertTrue("side by side: $remove $pro", remove.right <= pro.left + 1.dp)
        screenshot(name)
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun paywall_compact() = bothOptionsShown("paywall-compact")

    @Test @Config(qualifiers = "w700dp-h1000dp-xhdpi")
    fun paywall_medium() = bothOptionsShown("paywall-medium")

    @Test @Config(qualifiers = "w1100dp-h800dp-xhdpi")
    fun paywall_expanded() = bothOptionsShown("paywall-expanded")
}
