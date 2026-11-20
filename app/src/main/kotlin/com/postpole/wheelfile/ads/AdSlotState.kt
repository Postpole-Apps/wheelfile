package com.postpole.wheelfile.ads

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.ui.theme.Sizes

/** Where a banner request stands. */
enum class BannerLoad { LOADING, LOADED, NO_FILL }

object AdSlotState {
    /**
     * Whether a banner may be requested at all: consent allows ads, Remote Config enables banners,
     * and the user has not bought Remove ads or Pro.
     */
    fun bannersAllowed(canRequestAds: Boolean, config: AdsConfig, adsHidden: Boolean): Boolean =
        canRequestAds && config.bannersAllowed && !adsHidden

    /**
     * Height the slot takes (DECISIONS.md items 3-5): 0 when banners are not allowed or on no fill;
     * the 50 dp + 1 dp slot while loading (nothing jumps); the real ad height plus the hairline once loaded.
     */
    fun height(allowed: Boolean, load: BannerLoad, loadedAdHeight: Dp = Sizes.adSlotHeight): Dp = when {
        !allowed -> 0.dp
        load == BannerLoad.LOADING -> Sizes.adSlotTotal
        load == BannerLoad.NO_FILL -> 0.dp
        else -> maxOf(loadedAdHeight, Sizes.adSlotHeight) + Sizes.adSlotHairline
    }
}
