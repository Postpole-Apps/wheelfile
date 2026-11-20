package com.postpole.wheelfile.ads

import com.postpole.wheelfile.BuildConfig

/** Which screen a banner belongs to (banners appear only on these, DECISIONS.md item 3). */
enum class AdScreen { GARAGE, HISTORY, DOCUMENTS, REPORTS }

/** One full set of ad unit IDs. */
data class AdUnitSet(
    val bannerGarage: String,
    val bannerHistory: String,
    val bannerDocuments: String,
    val bannerReports: String,
    val interstitialAfterReview: String,
    val rewarded: String,
) {
    fun banner(screen: AdScreen): String = when (screen) {
        AdScreen.GARAGE -> bannerGarage
        AdScreen.HISTORY -> bannerHistory
        AdScreen.DOCUMENTS -> bannerDocuments
        AdScreen.REPORTS -> bannerReports
    }

    fun all(): List<String> =
        listOf(bannerGarage, bannerHistory, bannerDocuments, bannerReports, interstitialAfterReview, rewarded)
}

/**
 * Ad unit IDs (docs/admob.md). [DEBUG] and [RELEASE] mirror the literals in `app/build.gradle.kts`;
 * [current] is whatever this build's `BuildConfig` carries. AdUnitsTest keeps all three consistent.
 */
object AdUnits {
    val DEBUG = AdUnitSet(
        bannerGarage = "ca-app-pub-3940256099942544/9214589741",
        bannerHistory = "ca-app-pub-3940256099942544/9214589741",
        bannerDocuments = "ca-app-pub-3940256099942544/9214589741",
        bannerReports = "ca-app-pub-3940256099942544/9214589741",
        interstitialAfterReview = "ca-app-pub-3940256099942544/1033173712",
        rewarded = "ca-app-pub-3940256099942544/5224354917",
    )

    val RELEASE = AdUnitSet(
        bannerGarage = "ca-app-pub-2007361247232604/7125861609",
        bannerHistory = "ca-app-pub-2007361247232604/9041578502",
        bannerDocuments = "ca-app-pub-2007361247232604/3312232966",
        bannerReports = "ca-app-pub-2007361247232604/7566725638",
        interstitialAfterReview = "ca-app-pub-2007361247232604/8372987954",
        rewarded = "ca-app-pub-2007361247232604/4715320686",
    )

    val current = AdUnitSet(
        bannerGarage = BuildConfig.AD_UNIT_BANNER_GARAGE,
        bannerHistory = BuildConfig.AD_UNIT_BANNER_HISTORY,
        bannerDocuments = BuildConfig.AD_UNIT_BANNER_DOCUMENTS,
        bannerReports = BuildConfig.AD_UNIT_BANNER_REPORTS,
        interstitialAfterReview = BuildConfig.AD_UNIT_INTERSTITIAL_AFTER_REVIEW,
        rewarded = BuildConfig.AD_UNIT_REWARDED,
    )
}
