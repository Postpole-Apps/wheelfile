package com.postpole.wheelfile.billing

import com.postpole.wheelfile.BuildConfig

/** Play product IDs (one-time INAPP). Prices come only from Play `ProductDetails`, never from code. */
object ProductIds {
    const val PRO = BuildConfig.PRO_PRODUCT_ID
    const val REMOVE_ADS = BuildConfig.REMOVE_ADS_PRODUCT_ID
    val ALL: List<String> = listOf(PRO, REMOVE_ADS)
}

/**
 * What the user owns. `adsHidden = owns(remove_ads) || owns(lifetime_full_access)`,
 * `isPro = owns(lifetime_full_access)` (spec §5.1). A refund of one product leaves the other intact.
 */
data class Entitlements(
    val ownsPro: Boolean = false,
    val ownsRemoveAds: Boolean = false,
) {
    val isPro: Boolean get() = ownsPro
    val adsHidden: Boolean get() = ownsRemoveAds || ownsPro
    val ownsAnything: Boolean get() = ownsPro || ownsRemoveAds

    companion object {
        /** Builds entitlements from the product IDs the user currently owns (purchased, not pending). */
        fun from(ownedProductIds: Set<String>) = Entitlements(
            ownsPro = ProductIds.PRO in ownedProductIds,
            ownsRemoveAds = ProductIds.REMOVE_ADS in ownedProductIds,
        )
    }
}
