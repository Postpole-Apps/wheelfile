package com.postpole.wheelfile.ads

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Keeps `remote_config_defaults.xml` (spec §5.2) and [AdsConfig.DEFAULTS] identical. */
class RemoteConfigDefaultsTest {
    private val xml = File("src/main/res/xml/remote_config_defaults.xml").readText()

    private fun value(key: String): String =
        Regex("<key>$key</key>\\s*<value>([^<]*)</value>").find(xml)?.groupValues?.get(1)
            ?: error("missing key $key")

    @Test fun xmlMatchesSpec() {
        val expected = mapOf(
            AdsConfigKeys.ADS_ENABLED to "true",
            AdsConfigKeys.BANNER_ENABLED to "true",
            AdsConfigKeys.INTERSTITIAL_ENABLED to "true",
            AdsConfigKeys.REWARDED_ENABLED to "false",
            AdsConfigKeys.NATIVE_ENABLED to "false",
            AdsConfigKeys.APP_OPEN_ENABLED to "false",
            AdsConfigKeys.INTERSTITIAL_BLOCK_FIRST_SESSION to "true",
            AdsConfigKeys.INTERSTITIAL_MIN_INSTALL_DAYS to "3",
            AdsConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC to "480",
            AdsConfigKeys.INTERSTITIAL_MAX_PER_SESSION to "1",
            AdsConfigKeys.INTERSTITIAL_MAX_PER_DAY to "3",
            AdsConfigKeys.MAX_CONTENT_RATING to "PG",
            AdsConfigKeys.REWARDED_DAILY_LIMIT to "1",
        )
        expected.forEach { (key, v) -> assertEquals(key, v, value(key)) }
        assertEquals(expected.size, Regex("<entry>").findAll(xml).count())
    }

    @Test fun kotlinDefaultsMatchXml() {
        val d = AdsConfig.DEFAULTS
        assertEquals(value(AdsConfigKeys.ADS_ENABLED).toBoolean(), d.adsEnabled)
        assertEquals(value(AdsConfigKeys.BANNER_ENABLED).toBoolean(), d.bannerEnabled)
        assertEquals(value(AdsConfigKeys.INTERSTITIAL_ENABLED).toBoolean(), d.interstitialEnabled)
        assertEquals(value(AdsConfigKeys.REWARDED_ENABLED).toBoolean(), d.rewardedEnabled)
        assertEquals(value(AdsConfigKeys.NATIVE_ENABLED).toBoolean(), d.nativeEnabled)
        assertEquals(value(AdsConfigKeys.APP_OPEN_ENABLED).toBoolean(), d.appOpenEnabled)
        assertEquals(value(AdsConfigKeys.INTERSTITIAL_BLOCK_FIRST_SESSION).toBoolean(), d.interstitialBlockFirstSession)
        assertEquals(value(AdsConfigKeys.INTERSTITIAL_MIN_INSTALL_DAYS).toInt(), d.interstitialMinInstallDays)
        assertEquals(value(AdsConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC).toInt(), d.interstitialMinIntervalSec)
        assertEquals(value(AdsConfigKeys.INTERSTITIAL_MAX_PER_SESSION).toInt(), d.interstitialMaxPerSession)
        assertEquals(value(AdsConfigKeys.INTERSTITIAL_MAX_PER_DAY).toInt(), d.interstitialMaxPerDay)
        assertEquals(value(AdsConfigKeys.MAX_CONTENT_RATING), d.maxContentRating)
        assertEquals(value(AdsConfigKeys.REWARDED_DAILY_LIMIT).toInt(), d.rewardedDailyLimit)
    }
}
