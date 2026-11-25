package com.postpole.wheelfile.analytics

import com.postpole.wheelfile.ads.ProPromptDismissMethod
import com.postpole.wheelfile.ads.ProPromptSurface
import com.postpole.wheelfile.ads.ProPromptTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

class ProPromptEventsTest {
    private class Recorder : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = Unit
    }

    @Test fun triggerEnumValues() {
        assertEquals(
            listOf("entries_milestone", "service_logged", "second_vehicle_added", "reports_3_months"),
            ProPromptTrigger.entries.map { it.id },
        )
        assertEquals(listOf("sheet", "card"), ProPromptSurface.entries.map { it.id })
        assertEquals(listOf("close", "not_now", "swipe"), ProPromptDismissMethod.entries.map { it.id })
    }

    @Test fun shown_hasOnlyTriggerAndSurface() {
        val a = Recorder()
        a.logProPromptShown(ProPromptTrigger.SERVICE_LOGGED, ProPromptSurface.SHEET)
        assertEquals(
            listOf("pro_prompt_shown" to mapOf("trigger" to "service_logged", "surface" to "sheet")),
            a.events,
        )
    }

    @Test fun dismissed_addsMethod() {
        val a = Recorder()
        a.logProPromptDismissed(ProPromptTrigger.REPORTS_3_MONTHS, ProPromptSurface.CARD, ProPromptDismissMethod.SWIPE)
        assertEquals(
            listOf(
                "pro_prompt_dismissed" to
                    mapOf("trigger" to "reports_3_months", "surface" to "card", "method" to "swipe"),
            ),
            a.events,
        )
    }

    @Test fun ctaTap_hasOnlyTriggerAndSurface() {
        val a = Recorder()
        a.logProPromptCtaTap(ProPromptTrigger.ENTRIES_MILESTONE, ProPromptSurface.CARD)
        assertEquals(
            listOf("pro_prompt_cta_tap" to mapOf("trigger" to "entries_milestone", "surface" to "card")),
            a.events,
        )
    }
}
