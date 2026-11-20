package com.postpole.wheelfile.ads

import com.postpole.wheelfile.BuildConfig

/** Screens that may show an anchored adaptive banner (docs/design/DECISIONS.md item 3). */
enum class AdScreen { GARAGE, HISTORY, DOCUMENTS, REPORTS }

/**
 * Ad unit IDs for the current build type. Debug builds only ever see Google's test units; release builds
 * see the production units from docs/admob.md. Remote Config never changes these, only whether a format shows.
 */
object AdUnits {
    fun banner(screen: AdScreen): String = when (screen) {
        AdScreen.GARAGE -> BuildConfig.AD_UNIT_BANNER_GARAGE
        AdScreen.HISTORY -> BuildConfig.AD_UNIT_BANNER_HISTORY
        AdScreen.DOCUMENTS -> BuildConfig.AD_UNIT_BANNER_DOCUMENTS
        AdScreen.REPORTS -> BuildConfig.AD_UNIT_BANNER_REPORTS
    }

    val interstitialAfterReview: String get() = BuildConfig.AD_UNIT_INTERSTITIAL_AFTER_REVIEW
    val rewarded: String get() = BuildConfig.AD_UNIT_REWARDED

    /** Google's official test units (docs/admob.md, "Debug builds"). */
    object Test {
        const val BANNER = "ca-app-pub-3940256099942544/9214589741"
        const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
    }

    /** Production units (docs/admob.md, "Release builds"). */
    object Production {
        const val APP_ID = "ca-app-pub-2007361247232604~9674014227"
        const val BANNER_GARAGE = "ca-app-pub-2007361247232604/7125861609"
        const val BANNER_HISTORY = "ca-app-pub-2007361247232604/9041578502"
        const val BANNER_DOCUMENTS = "ca-app-pub-2007361247232604/3312232966"
        const val BANNER_REPORTS = "ca-app-pub-2007361247232604/7566725638"
        const val INTERSTITIAL_AFTER_REVIEW = "ca-app-pub-2007361247232604/8372987954"
        const val REWARDED = "ca-app-pub-2007361247232604/4715320686"
    }
}
