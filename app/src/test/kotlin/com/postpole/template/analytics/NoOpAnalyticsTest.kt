package com.postpole.template.analytics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoOpAnalyticsTest {
    @Test
    fun noOp_acceptsEveryCallWithoutThrowing() {
        val analytics: Analytics = NoOpAnalytics
        analytics.logEvent(AnalyticsEvents.PURCHASE, mapOf(AnalyticsParams.VALUE to 4.99, AnalyticsParams.CURRENCY to "INR"))
        analytics.logEvent(AnalyticsEvents.APP_OPEN)
        analytics.setUserProperty("plan", "pro")
        analytics.setUserProperty("plan", null)
        analytics.logScreenView("home")
        analytics.recordNonFatal(IllegalStateException("boom"))
    }

    /** The template has no google-services.json, so Firebase is not initialised and we must fall back. */
    @Test
    fun createAnalytics_withoutFirebaseConfig_returnsNoOp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertSame(NoOpAnalytics, createAnalytics(context))
    }
}
