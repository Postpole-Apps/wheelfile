package com.postpole.wheelfile.ads

/**
 * Remote Config values that control whether and how often ads show (docs/spec.md §5.2). Keys and defaults
 * are mirrored in `res/xml/remote_config_defaults.xml`; the app behaves the same with no fetch.
 */
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
    val bannersAllowed: Boolean get() = adsEnabled && bannerEnabled

    companion object {
        val DEFAULT = AdsConfig()
    }
}

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

/** Reads typed values by key so the mapping is testable without Firebase. */
interface ConfigValues {
    fun bool(key: String): Boolean?
    fun long(key: String): Long?
    fun string(key: String): String?
}

fun AdsConfig.Companion.from(values: ConfigValues): AdsConfig {
    val d = DEFAULT
    return AdsConfig(
        adsEnabled = values.bool(AdsConfigKeys.ADS_ENABLED) ?: d.adsEnabled,
        bannerEnabled = values.bool(AdsConfigKeys.BANNER_ENABLED) ?: d.bannerEnabled,
        interstitialEnabled = values.bool(AdsConfigKeys.INTERSTITIAL_ENABLED) ?: d.interstitialEnabled,
        rewardedEnabled = values.bool(AdsConfigKeys.REWARDED_ENABLED) ?: d.rewardedEnabled,
        nativeEnabled = values.bool(AdsConfigKeys.NATIVE_ENABLED) ?: d.nativeEnabled,
        appOpenEnabled = values.bool(AdsConfigKeys.APP_OPEN_ENABLED) ?: d.appOpenEnabled,
        interstitialBlockFirstSession = values.bool(AdsConfigKeys.INTERSTITIAL_BLOCK_FIRST_SESSION)
            ?: d.interstitialBlockFirstSession,
        interstitialMinInstallDays = values.long(AdsConfigKeys.INTERSTITIAL_MIN_INSTALL_DAYS)?.toInt()
            ?: d.interstitialMinInstallDays,
        interstitialMinIntervalSec = values.long(AdsConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC)?.toInt()
            ?: d.interstitialMinIntervalSec,
        interstitialMaxPerSession = values.long(AdsConfigKeys.INTERSTITIAL_MAX_PER_SESSION)?.toInt()
            ?: d.interstitialMaxPerSession,
        interstitialMaxPerDay = values.long(AdsConfigKeys.INTERSTITIAL_MAX_PER_DAY)?.toInt()
            ?: d.interstitialMaxPerDay,
        maxContentRating = values.string(AdsConfigKeys.MAX_CONTENT_RATING)?.takeIf { it.isNotBlank() }
            ?: d.maxContentRating,
        rewardedDailyLimit = values.long(AdsConfigKeys.REWARDED_DAILY_LIMIT)?.toInt() ?: d.rewardedDailyLimit,
    )
}
