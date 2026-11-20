package com.postpole.wheelfile.ads

import com.postpole.wheelfile.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdUnitsTest {
    private val googleTestPrefix = "ca-app-pub-3940256099942544/"
    private val publisherPrefix = "ca-app-pub-2007361247232604/"

    @Test fun debugSet_isOnlyGoogleTestUnits() {
        AdUnits.DEBUG.all().forEach { assertTrue(it, it.startsWith(googleTestPrefix)) }
        assertEquals("ca-app-pub-3940256099942544/9214589741", AdUnits.DEBUG.bannerGarage)
        assertEquals("ca-app-pub-3940256099942544/1033173712", AdUnits.DEBUG.interstitialAfterReview)
        assertEquals("ca-app-pub-3940256099942544/5224354917", AdUnits.DEBUG.rewarded)
    }

    @Test fun releaseSet_isExactlyTheProductionUnits() {
        val r = AdUnits.RELEASE
        assertEquals("ca-app-pub-2007361247232604/7125861609", r.bannerGarage)
        assertEquals("ca-app-pub-2007361247232604/9041578502", r.bannerHistory)
        assertEquals("ca-app-pub-2007361247232604/3312232966", r.bannerDocuments)
        assertEquals("ca-app-pub-2007361247232604/7566725638", r.bannerReports)
        assertEquals("ca-app-pub-2007361247232604/8372987954", r.interstitialAfterReview)
        assertEquals("ca-app-pub-2007361247232604/4715320686", r.rewarded)
        r.all().forEach { assertTrue(it, it.startsWith(publisherPrefix)) }
    }

    /** This test runs against the debug variant, whose BuildConfig must carry only Google test units. */
    @Test fun debugBuildConfig_matchesTheDebugSet() {
        assertTrue(BuildConfig.DEBUG)
        assertEquals(AdUnits.DEBUG, AdUnits.current)
    }

    /** The release literals in build.gradle.kts (what the release BuildConfig is generated from) match [AdUnits.RELEASE]. */
    @Test fun gradleFile_hasTheProductionUnitsForRelease() {
        val gradle = File("build.gradle.kts").readText()
        val releaseBlock = gradle.substringAfter("val releaseAdUnits = mapOf(").substringBefore("\n)")
        val debugBlock = gradle.substringAfter("val debugAdUnits = mapOf(").substringBefore("\n)")
        AdUnits.RELEASE.all().forEach { assertTrue("release block lacks $it", it in releaseBlock) }
        AdUnits.DEBUG.all().forEach { assertTrue("debug block lacks $it", it in debugBlock) }
        assertTrue(googleTestPrefix !in releaseBlock)
        assertTrue(publisherPrefix !in debugBlock)
    }

    @Test fun gradleFile_wiresTheAppId() {
        assertTrue("ca-app-pub-2007361247232604~9674014227" in File("build.gradle.kts").readText())
    }

    @Test fun bannerPerScreen() {
        assertEquals(AdUnits.RELEASE.bannerHistory, AdUnits.RELEASE.banner(AdScreen.HISTORY))
        assertEquals(AdUnits.RELEASE.bannerReports, AdUnits.RELEASE.banner(AdScreen.REPORTS))
    }
}
