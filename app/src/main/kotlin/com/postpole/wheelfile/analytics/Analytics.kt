package com.postpole.wheelfile.analytics

import com.postpole.wheelfile.ads.ProPromptDismissMethod
import com.postpole.wheelfile.ads.ProPromptSurface
import com.postpole.wheelfile.ads.ProPromptTrigger

/** Event and parameter names. Values mirror Firebase's standard names where one exists. */
object AnalyticsEvents {
    const val APP_OPEN = "app_open"
    const val FIRST_LAUNCH = "first_launch"
    const val SCREEN_VIEW = "screen_view"
    const val PRO_SCREEN_VIEW = "pro_screen_view"
    const val PURCHASE_STARTED = "purchase_started"
    const val PURCHASE = "purchase"
    const val PURCHASE_RESTORED = "purchase_restored"
    const val REMOVE_ADS_PAYWALL_VIEW = "remove_ads_paywall_view"
    const val REMOVE_ADS_PURCHASE_START = "remove_ads_purchase_start"
    const val REMOVE_ADS_PURCHASE_SUCCESS = "remove_ads_purchase_success"
    const val REMOVE_ADS_PURCHASE_FAIL = "remove_ads_purchase_fail"
    const val BACKUP_EXPORTED = "backup_exported"
    const val BACKUP_IMPORTED = "backup_imported"
    const val VEHICLE_ADDED = "vehicle_added"
    const val ONBOARDING_COMPLETE = "onboarding_complete"
    const val FILLUP_SAVED = "fillup_saved"
    const val CHARGE_SAVED = "charge_saved"
    const val SERVICE_SAVED = "service_saved"
    const val EXPENSE_SAVED = "expense_saved"
    const val REMINDER_CREATED = "reminder_created"
    const val DOCUMENT_SAVED = "document_saved"
    const val PRESETS_ADDED = "presets_added"
    const val ATTACHMENT_CAP_REACHED = "attachment_cap_reached"
    const val PRO_PROMPT_SHOWN = "pro_prompt_shown"
    const val PRO_PROMPT_DISMISSED = "pro_prompt_dismissed"
    const val PRO_PROMPT_CTA_TAP = "pro_prompt_cta_tap"
}

object AnalyticsParams {
    const val SCREEN_NAME = "screen_name"
    const val VALUE = "value"
    const val CURRENCY = "currency"
    const val ITEM_ID = "item_id"

    /** Which entry point opened the paywall (an enum value such as `pdf_report`). */
    const val TRIGGER = "trigger"
    const val ITEM_COUNT = "item_count"
    const val FORMAT = "format"
    const val POWERTRAIN = "powertrain"
    const val UNIT_SYSTEM = "unit_system"

    /** `sheet` or `card` (automatic Pro prompts). */
    const val SURFACE = "surface"

    /** `close`, `not_now` or `swipe` (automatic Pro prompts). */
    const val METHOD = "method"
}

/**
 * Automatic Pro prompt events (spec v0.2.2 §5.4a). The only inputs are enums, so no free text, count, ID or user
 * data can reach the event.
 */
fun Analytics.logProPromptShown(trigger: ProPromptTrigger, surface: ProPromptSurface) =
    logEvent(AnalyticsEvents.PRO_PROMPT_SHOWN, proPromptParams(trigger, surface))

fun Analytics.logProPromptDismissed(
    trigger: ProPromptTrigger,
    surface: ProPromptSurface,
    method: ProPromptDismissMethod,
) = logEvent(
    AnalyticsEvents.PRO_PROMPT_DISMISSED,
    proPromptParams(trigger, surface) + (AnalyticsParams.METHOD to method.id),
)

fun Analytics.logProPromptCtaTap(trigger: ProPromptTrigger, surface: ProPromptSurface) =
    logEvent(AnalyticsEvents.PRO_PROMPT_CTA_TAP, proPromptParams(trigger, surface))

private fun proPromptParams(trigger: ProPromptTrigger, surface: ProPromptSurface): Map<String, Any> =
    mapOf(AnalyticsParams.TRIGGER to trigger.id, AnalyticsParams.SURFACE to surface.id)

/**
 * App-facing analytics + crash reporting seam. Backed by Firebase when the app has a
 * google-services.json, otherwise [NoOpAnalytics]. Never put personal data in params.
 */
interface Analytics {
    /** Values may be String, Long, Int, Double or Boolean. */
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())

    fun setUserProperty(name: String, value: String?)

    fun logScreenView(screenName: String)

    /** Records a handled exception (Crashlytics non-fatal). */
    fun recordNonFatal(throwable: Throwable)
}

/** Used when Firebase isn't configured: every call is safe and does nothing. */
object NoOpAnalytics : Analytics {
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
}
