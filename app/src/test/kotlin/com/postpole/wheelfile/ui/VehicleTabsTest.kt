package com.postpole.wheelfile.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.ui.theme.WheelfileTheme
import com.postpole.wheelfile.ui.vehicle.VehicleTab
import com.postpole.wheelfile.ui.vehicle.VehicleTabs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tab labels must render in full (no clipping) at 360dp wide and 1.3x font scale. */
@RunWith(RobolectricTestRunner::class)
class VehicleTabsTest {
    @get:Rule val rule = createComposeRule()

    private fun assertLabelsFit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        lateinit var measurer: TextMeasurer
        lateinit var style: TextStyle
        lateinit var density: Density
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                WheelfileTheme {
                    measurer = rememberTextMeasurer()
                    style = MaterialTheme.typography.titleSmall
                    density = LocalDensity.current
                    VehicleTabs(selected = 0, onSelect = {})
                }
            }
        }
        VehicleTab.entries.forEach { tab ->
            val label = context.getString(tab.label)
            val needed = measurer.measure(label, style, softWrap = false, maxLines = 1, density = density).size.width
            val actual = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().size.width
            assertTrue("$label clipped: needs $needed px, got $actual px", actual >= needed)
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp") fun english() = assertLabelsFit()

    @Test @Config(qualifiers = "de-w360dp-h640dp") fun german() = assertLabelsFit()

    @Test @Config(qualifiers = "fr-w360dp-h640dp") fun french() = assertLabelsFit()

    @Test @Config(qualifiers = "es-w360dp-h640dp") fun spanish() = assertLabelsFit()
}
