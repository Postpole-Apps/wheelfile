package com.postpole.wheelfile.billing

/**
 * What the user owns. Remove ads (`remove_ads`) and Pro (`lifetime_full_access`) are separate one-time
 * products, so a refund of one leaves the other in place (docs/spec.md §5.1).
 */
data class Entitlements(
    val ownsRemoveAds: Boolean = false,
    val ownsPro: Boolean = false,
) {
    /** Either product hides every ad. */
    val adsHidden: Boolean get() = ownsRemoveAds || ownsPro

    val isPro: Boolean get() = ownsPro

    companion object {
        val NONE = Entitlements()
    }
}

/** Outcome of a user-initiated "Restore purchases". */
enum class RestoreResult { RESTORED, NONE_FOUND, FAILED }
