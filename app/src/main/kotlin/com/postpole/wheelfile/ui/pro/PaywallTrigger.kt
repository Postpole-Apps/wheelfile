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
    GARAGE("garage"),

    // Automatic Pro prompts (spec v0.2.2 §5.4a): `auto_<trigger>`. The funnel wiring itself is #23.
    AUTO_ENTRIES_MILESTONE("auto_entries_milestone"),
    AUTO_SECOND_VEHICLE_ADDED("auto_second_vehicle_added"),
    AUTO_SERVICE_LOGGED("auto_service_logged"),
    AUTO_REPORTS_3_MONTHS("auto_reports_3_months");

    companion object {
        fun fromRoute(value: String?): PaywallTrigger =
            entries.firstOrNull { it.analyticsValue == value } ?: SETTINGS
    }
}
