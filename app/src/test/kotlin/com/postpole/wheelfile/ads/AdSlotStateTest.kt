package com.postpole.wheelfile.ads

import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.ui.theme.Sizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSlotStateTest {
    private val config = AdsConfig.DEFAULTS

    @Test fun loading_reservesFiftyPlusOneDp() {
        assertEquals(51.dp, Sizes.adSlotTotal)
        assertEquals(Sizes.adSlotTotal, AdSlotState.height(allowed = true, load = BannerLoad.LOADING))
    }

    @Test fun noFill_collapsesToZero() {
        assertEquals(0.dp, AdSlotState.height(allowed = true, load = BannerLoad.NO_FILL))
    }

    @Test fun loaded_keepsTheSlot() {
        assertEquals(Sizes.adSlotTotal, AdSlotState.height(allowed = true, load = BannerLoad.LOADED))
        assertEquals(61.dp, AdSlotState.height(allowed = true, load = BannerLoad.LOADED, loadedAdHeight = 60.dp))
    }

    @Test fun notAllowed_isZeroInEveryLoadState() {
        BannerLoad.entries.forEach {
            assertEquals(0.dp, AdSlotState.height(allowed = false, load = it))
        }
    }

    @Test fun adsHidden_takesZeroHeight() {
        val allowed = AdSlotState.bannersAllowed(canRequestAds = true, config = config, adsHidden = true)
        assertFalse(allowed)
        assertEquals(0.dp, AdSlotState.height(allowed, BannerLoad.LOADING))
    }

    @Test fun noConsent_neverAllowsRequests() {
        assertFalse(AdSlotState.bannersAllowed(canRequestAds = false, config = config, adsHidden = false))
    }

    @Test fun remoteConfigCanDisableBanners() {
        assertFalse(AdSlotState.bannersAllowed(true, config.copy(adsEnabled = false), adsHidden = false))
        assertFalse(AdSlotState.bannersAllowed(true, config.copy(bannerEnabled = false), adsHidden = false))
        assertTrue(AdSlotState.bannersAllowed(true, config, adsHidden = false))
    }
}
