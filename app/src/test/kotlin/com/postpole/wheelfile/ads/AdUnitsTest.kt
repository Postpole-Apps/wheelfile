package com.postpole.wheelfile.ads

import com.postpole.wheelfile.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdUnitsTest {
    private val productionUnits = listOf(
        AdUnits.Production.BANNER_GARAGE,
        AdUnits.Production.BANNER_HISTORY,
        AdUnits.Production.BANNER_DOCUMENTS,
        AdUnits.Production.BANNER_REPORTS,
        AdUnits.Production.INTERSTITIAL_AFTER_REVIEW,
        AdUnits.Production.REWARDED,
    )

    @Test fun productionIds_matchDocsAdmob() {
        assertEquals("ca-app-pub-2007361247232604~9674014227", AdUnits.Production.APP_ID)
        assertEquals("ca-app-pub-2007361247232604/7125861609", AdUnits.Production.BANNER_GARAGE)
        assertEquals("ca-app-pub-2007361247232604/9041578502", AdUnits.Production.BANNER_HISTORY)
        assertEquals("ca-app-pub-2007361247232604/3312232966", AdUnits.Production.BANNER_DOCUMENTS)
        assertEquals("ca-app-pub-2007361247232604/7566725638", AdUnits.Production.BANNER_REPORTS)
        assertEquals("ca-app-pub-2007361247232604/8372987954", AdUnits.Production.INTERSTITIAL_AFTER_REVIEW)
        assertEquals("ca-app-pub-2007361247232604/4715320686", AdUnits.Production.REWARDED)
    }

    @Test fun testIds_areGoogleOfficialUnits() {
        assertEquals("ca-app-pub-3940256099942544/9214589741", AdUnits.Test.BANNER)
        assertEquals("ca-app-pub-3940256099942544/1033173712", AdUnits.Test.INTERSTITIAL)
        assertEquals("ca-app-pub-3940256099942544/5224354917", AdUnits.Test.REWARDED)
    }

    /** The variant under test: debug = Google test units only, release = the production units only. */
    @Test fun currentVariant_usesTheRightUnits() {
        val actual = AdScreen.entries.map(AdUnits::banner) +
            listOf(AdUnits.interstitialAfterReview, AdUnits.rewarded)
        if (BuildConfig.DEBUG) {
            assertEquals(
                AdScreen.entries.map { AdUnits.Test.BANNER } + listOf(AdUnits.Test.INTERSTITIAL, AdUnits.Test.REWARDED),
                actual,
            )
        } else {
            assertEquals(productionUnits, actual)
        }
    }

    /** Covers the variant the unit test does not run: the release block in build.gradle.kts. */
    @Test fun gradleBuildTypes_wireTheExactIds() {
        val script = File("build.gradle.kts").readText()
        val debug = script.substringAfter("        debug {").substringBefore("        release {")
        val release = script.substringAfter("        release {")

        productionUnits.forEach { id ->
            assertTrue("release block is missing $id", id in release)
            assertFalse("debug block must not contain production unit $id", id in debug)
        }
        listOf(AdUnits.Test.BANNER, AdUnits.Test.INTERSTITIAL, AdUnits.Test.REWARDED).forEach { id ->
            assertTrue("debug block is missing $id", id in debug)
            assertFalse("release block must not contain test unit $id", id in release)
        }
        assertTrue("manifest placeholder uses the production App ID", AdUnits.Production.APP_ID in script)
    }
}
