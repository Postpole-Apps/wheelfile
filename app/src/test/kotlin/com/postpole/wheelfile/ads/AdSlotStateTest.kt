package com.postpole.wheelfile.ads

import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.ui.theme.Sizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSlotStateTest {
    private val config = AdsConfig.DEFAULT

    @Test fun loading_reservesFiftyPlusOneDp() {
        assertEquals(51.dp, Sizes.adSlotTotal)
        assertEquals(Sizes.adSlotTotal, AdSlotState.height(eligible = true, load = AdLoadState.LOADING))
    }

    @Test fun noFill_collapsesToZero() {
        assertEquals(0.dp, AdSlotState.height(eligible = true, load = AdLoadState.NO_FILL))
    }

    @Test fun loaded_usesTheAdHeightPlusHairline() {
        assertEquals(51.dp, AdSlotState.height(eligible = true, load = AdLoadState.LOADED))
        assertEquals(
            91.dp,
            AdSlotState.height(eligible = true, load = AdLoadState.LOADED, loadedAdHeight = 90.dp),
        )
    }

    @Test fun adsHidden_isZeroInEveryLoadState() {
        AdLoadState.entries.forEach { load ->
            val eligible = AdSlotState.eligible(adsHidden = true, adsReady = true, config = config)
            assertEquals(0.dp, AdSlotState.height(eligible, load))
        }
    }

    @Test fun eligible_onlyWhenNotHiddenConsentedAndEnabled() {
        assertTrue(AdSlotState.eligible(adsHidden = false, adsReady = true, config = config))
        assertFalse(AdSlotState.eligible(adsHidden = true, adsReady = true, config = config))
        // Consent disallows requests (or the SDK is not initialized yet).
        assertFalse(AdSlotState.eligible(adsHidden = false, adsReady = false, config = config))
        assertFalse(AdSlotState.eligible(false, true, config.copy(bannerEnabled = false)))
        assertFalse(AdSlotState.eligible(false, true, config.copy(adsEnabled = false)))
    }

    @Test fun notEligible_isZeroEvenWhileLoading() {
        assertEquals(0.dp, AdSlotState.height(eligible = false, load = AdLoadState.LOADING))
    }

    @Test fun fab_sitsSixteenDpAboveTheSlot() {
        assertEquals(16.dp, AdSlotState.fabBottomOffset(0.dp))
        assertEquals(67.dp, AdSlotState.fabBottomOffset(Sizes.adSlotTotal))
    }
}
