package com.postpole.wheelfile.ads

import android.content.Context

/** Keeps interstitial impression times in SharedPreferences (small, read synchronously). */
class PrefsInterstitialHistory(context: Context) : InterstitialHistory {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun shownAt(): List<Long> =
        prefs.getString(KEY_SHOWN, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    override fun record(epochMillis: Long) {
        // Keep only the last two days; day caps and the interval never look further back.
        val cutoff = epochMillis - 2L * 24 * 60 * 60 * 1_000
        val kept = (shownAt().filter { it >= cutoff } + epochMillis).joinToString(",")
        prefs.edit().putString(KEY_SHOWN, kept).apply()
    }

    private companion object {
        const val FILE = "ads_policy"
        const val KEY_SHOWN = "interstitial_shown_at"
    }
}

/** Builds the per-launch [AdsPolicy]. True "first session" is the very first launch after install. */
fun createAdsPolicy(context: Context): AdsPolicy {
    val prefs = context.getSharedPreferences("ads_policy", Context.MODE_PRIVATE)
    val first = !prefs.getBoolean("seen_first_session", false)
    if (first) prefs.edit().putBoolean("seen_first_session", true).apply()
    val installTime = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }
        .getOrDefault(System.currentTimeMillis())
    return AdsPolicy(
        clock = System::currentTimeMillis,
        installTimeMs = installTime,
        isFirstSession = first,
        history = PrefsInterstitialHistory(context),
    )
}
