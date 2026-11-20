package com.postpole.wheelfile.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdsConfigTest {
    private val spec = AdsConfig(
        adsEnabled = true,
        bannerEnabled = true,
        interstitialEnabled = true,
        rewardedEnabled = false,
        nativeEnabled = false,
        appOpenEnabled = false,
        interstitialBlockFirstSession = true,
        interstitialMinInstallDays = 3,
        interstitialMinIntervalSec = 480,
        interstitialMaxPerSession = 1,
        interstitialMaxPerDay = 3,
        maxContentRating = "PG",
        rewardedDailyLimit = 1,
    )

    @Test fun defaults_matchSpecSection5_2() {
        assertEquals(spec, AdsConfig.DEFAULT)
    }

    /** The bundled XML must carry exactly the same keys and values as [AdsConfig.DEFAULT]. */
    @Test fun bundledXml_matchesTheDefaults() {
        val xml = File("src/main/res/xml/remote_config_defaults.xml").readText()
        val entries = Regex("<key>(.*?)</key>\\s*<value>(.*?)</value>").findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
        val values = object : ConfigValues {
            override fun bool(key: String) = entries.getValue(key).toBooleanStrict()
            override fun long(key: String) = entries.getValue(key).toLong()
            override fun string(key: String) = entries.getValue(key)
        }
        assertEquals(13, entries.size)
        assertEquals(AdsConfig.DEFAULT, AdsConfig.from(values))
    }

    @Test fun missingValues_fallBackToDefaults() {
        val empty = object : ConfigValues {
            override fun bool(key: String): Boolean? = null
            override fun long(key: String): Long? = null
            override fun string(key: String): String? = null
        }
        assertEquals(AdsConfig.DEFAULT, AdsConfig.from(empty))
    }

    @Test fun remoteValues_overrideDefaults() {
        val remote = object : ConfigValues {
            override fun bool(key: String) = key != AdsConfigKeys.BANNER_ENABLED
            override fun long(key: String): Long? = if (key == AdsConfigKeys.INTERSTITIAL_MAX_PER_DAY) 5 else null
            override fun string(key: String): String? = if (key == AdsConfigKeys.MAX_CONTENT_RATING) "G" else ""
        }
        val config = AdsConfig.from(remote)
        assertFalse(config.bannerEnabled)
        assertFalse(config.bannersAllowed)
        assertTrue(config.rewardedEnabled)
        assertEquals(5, config.interstitialMaxPerDay)
        assertEquals("G", config.maxContentRating)
    }

    @Test fun contentRating_defaultsToPg() {
        assertEquals("PG", contentRatingConstant("PG"))
        assertEquals("PG", contentRatingConstant("nonsense"))
        assertEquals("G", contentRatingConstant("g"))
        assertEquals("MA", contentRatingConstant(" MA "))
    }
}
