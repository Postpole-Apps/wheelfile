package com.postpole.wheelfile.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

/** Seam over the Mobile Ads SDK so the consent gate can be unit tested. */
interface AdsSdk {
    /** Initialises the SDK. Only ever called after consent info was updated and `canRequestAds()` is true. */
    fun initialize()

    /** Applies the max ad content rating (G, PG, T or MA). Unknown values fall back to PG. */
    fun setMaxContentRating(rating: String)
}

class GoogleAdsSdk(private val context: Context) : AdsSdk {
    override fun initialize() {
        MobileAds.initialize(context)
    }

    override fun setMaxContentRating(rating: String) {
        val value = when (rating.uppercase()) {
            "G" -> RequestConfiguration.MAX_AD_CONTENT_RATING_G
            "T" -> RequestConfiguration.MAX_AD_CONTENT_RATING_T
            "MA" -> RequestConfiguration.MAX_AD_CONTENT_RATING_MA
            else -> RequestConfiguration.MAX_AD_CONTENT_RATING_PG
        }
        // No test-device IDs here on purpose: debug builds use Google's test units instead.
        MobileAds.setRequestConfiguration(
            MobileAds.getRequestConfiguration().toBuilder().setMaxAdContentRating(value).build(),
        )
    }
}
