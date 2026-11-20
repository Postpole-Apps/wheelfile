package com.postpole.wheelfile.ads

/** Remote Config keys for ads (spec §5.2). Defaults live in `res/xml/remote_config_defaults.xml`. */
object AdsConfigKeys {
    const val ADS_ENABLED = "ads_enabled"
    const val BANNER_ENABLED = "ads_banner_enabled"
    const val INTERSTITIAL_ENABLED = "ads_interstitial_enabled"
    const val REWARDED_ENABLED = "ads_rewarded_enabled"
    const val NATIVE_ENABLED = "ads_native_enabled"
    const val APP_OPEN_ENABLED = "ads_app_open_enabled"
    const val INTERSTITIAL_BLOCK_FIRST_SESSION = "ads_interstitial_block_first_session"
    const val INTERSTITIAL_MIN_INSTALL_DAYS = "ads_interstitial_min_install_days"
    const val INTERSTITIAL_MIN_INTERVAL_SEC = "ads_interstitial_min_interval_sec"
    const val INTERSTITIAL_MAX_PER_SESSION = "ads_interstitial_max_per_session"
    const val INTERSTITIAL_MAX_PER_DAY = "ads_interstitial_max_per_day"
    const val MAX_CONTENT_RATING = "ads_max_content_rating"
    const val REWARDED_DAILY_LIMIT = "ads_rewarded_daily_limit"
}

/** Snapshot of the ad-related Remote Config values. Controls whether a format shows, never unit IDs. */
data class AdsConfig(
    val adsEnabled: Boolean = true,
    val bannerEnabled: Boolean = true,
    val interstitialEnabled: Boolean = true,
    val rewardedEnabled: Boolean = false,
    val nativeEnabled: Boolean = false,
    val appOpenEnabled: Boolean = false,
    val interstitialBlockFirstSession: Boolean = true,
    val interstitialMinInstallDays: Int = 3,
    val interstitialMinIntervalSec: Int = 480,
    val interstitialMaxPerSession: Int = 1,
    val interstitialMaxPerDay: Int = 3,
    val maxContentRating: String = "PG",
    val rewardedDailyLimit: Int = 1,
) {
    /** True when banners may be requested as far as Remote Config is concerned. */
    val bannersAllowed: Boolean get() = adsEnabled && bannerEnabled

    /** True when interstitials may be requested as far as Remote Config is concerned. */
    val interstitialsAllowed: Boolean get() = adsEnabled && interstitialEnabled

    companion object {
        /** Same values as `remote_config_defaults.xml` (a unit test keeps them in sync). */
        val DEFAULTS = AdsConfig()
    }
}
