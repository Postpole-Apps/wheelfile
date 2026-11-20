package com.postpole.wheelfile.ads

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.ui.theme.Sizes

enum class AdLoadState { LOADING, LOADED, NO_FILL }

/** Pure layout rules for the banner slot (docs/design/DECISIONS.md items 3 to 5). */
object AdSlotState {
    /**
     * A banner may only be requested when ads are not hidden (Remove ads or Pro), consent allows requests
     * (SDK initialized after the consent flow) and Remote Config enables banners.
     */
    fun eligible(adsHidden: Boolean, adsReady: Boolean, config: AdsConfig): Boolean =
        !adsHidden && adsReady && config.bannersAllowed

    /**
     * Total height the slot takes, including the 1 dp hairline. Reserved while loading so nothing jumps,
     * the real ad height once loaded, and 0 on no fill or when the slot isn't eligible.
     */
    fun height(eligible: Boolean, load: AdLoadState, loadedAdHeight: Dp = Sizes.adSlotHeight): Dp = when {
        !eligible -> 0.dp
        load == AdLoadState.LOADING -> Sizes.adSlotTotal
        load == AdLoadState.LOADED -> loadedAdHeight + Sizes.adSlotHairline
        load == AdLoadState.NO_FILL -> 0.dp
        else -> 0.dp
    }

    /** FAB bottom offset above the gesture inset: 16 dp, plus the slot while it is showing or loading. */
    fun fabBottomOffset(slotHeight: Dp): Dp = Sizes.fabGap + slotHeight
}
