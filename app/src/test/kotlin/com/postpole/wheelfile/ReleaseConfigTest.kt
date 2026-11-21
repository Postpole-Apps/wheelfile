package com.postpole.wheelfile

import com.postpole.wheelfile.ads.AdsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Release configuration that cannot be seen from a debug run: production IDs and R8 keep rules for release,
 * the App ID in the manifest, the privacy policy URL, and rewarded ads off by default.
 * Ad unit sets themselves are covered by `AdUnitsTest`.
 */
class ReleaseConfigTest {
    private val gradle = File("build.gradle.kts").readText()
    private val rules = File("proguard-rules.pro").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun releaseBuildMinifiesAndShrinks() {
        val release = gradle.substringAfter("release {").substringBefore("compileOptions")
        assertTrue("isMinifyEnabled = true" in release)
        assertTrue("isShrinkResources = true" in release)
        assertTrue("proguard-rules.pro" in release)
    }

    @Test fun releaseUsesProductionUnitsAndDebugNeverDoes() {
        val debug = gradle.substringAfter("debug {").substringBefore("release {")
        val release = gradle.substringAfter("release {").substringBefore("compileOptions")
        assertTrue("adUnits(debugAdUnits, allowOverride = false)" in debug)
        assertTrue("adUnits(releaseAdUnits, allowOverride = true)" in release)
    }

    @Test fun appIdIsTheProductionAppId() {
        assertTrue("ca-app-pub-2007361247232604~9674014227" in gradle)
        assertTrue("com.google.android.gms.ads.APPLICATION_ID" in manifest)
        assertTrue("\${admobAppId}" in manifest)
    }

    @Test fun r8KeepsAdsUmpAndBilling() {
        listOf("com.google.android.gms.ads", "com.google.android.ump", "com.android.billingclient").forEach {
            assertTrue("$it kept", Regex("-keep class ${Regex.escape(it)}\\.\\*\\* \\{ \\*; \\}").containsMatchIn(rules))
        }
    }

    @Test fun adIdPermissionIsNeverRemoved() {
        assertTrue("com.google.android.gms.permission.AD_ID" in manifest)
        val removed = Regex("<[^>]*(AD_ID|ADSERVICES)[^>]*tools:node=\"remove\"[^>]*>", RegexOption.DOT_MATCHES_ALL)
        assertFalse(removed.containsMatchIn(manifest))
    }

    @Test fun rewardedAdsAreOffByDefault() {
        assertFalse(AdsConfig.DEFAULTS.rewardedEnabled)
        val xml = File("src/main/res/xml/remote_config_defaults.xml").readText()
        assertTrue(Regex("<key>ads_rewarded_enabled</key>\\s*<value>false</value>").containsMatchIn(xml))
    }

    @Test fun privacyPolicyUrl() {
        val config = File("src/main/res/values/app_config.xml").readText()
        assertTrue(
            Regex("name=\"privacy_policy_url\"[^>]*>https://pocket-ledger-postpole.web.app/wheelfile/privacy</string>")
                .containsMatchIn(config),
        )
    }

    @Test fun productsAreOneTimePurchases() {
        assertTrue("\"lifetime_full_access\"" in gradle)
        assertTrue("\"remove_ads\"" in gradle)
        assertEquals(false, "SUBS" in File("src/main/kotlin/com/postpole/wheelfile/billing/PlayBillingGateway.kt").readText())
    }
}
