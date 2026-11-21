package com.postpole.wheelfile.ui.pro

/**
 * What sent the user to the paywall (spec §5.4). [analyticsValue] is the only thing logged: an enum, never user data.
 */
enum class PaywallTrigger(val analyticsValue: String) {
    VEHICLE_LIMIT("vehicle_limit"),
    PDF_REPORT("pdf_report"),
    ALL_TIME_CHARTS("all_time_charts"),
    ATTACHMENT_LIMIT("attachment_limit"),
    SETTINGS("settings"),
    GARAGE("garage");

    companion object {
        fun fromRoute(value: String?): PaywallTrigger =
            entries.firstOrNull { it.analyticsValue == value } ?: SETTINGS
    }
}
