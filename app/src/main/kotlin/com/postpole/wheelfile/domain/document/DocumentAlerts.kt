package com.postpole.wheelfile.domain.document

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ExpiryStatus {
    /** The document has no expiry date. */
    NONE,

    /** Expires later than the first alert. */
    VALID,

    /** Inside the alert window (warning pill). */
    EXPIRING,

    /** The expiry date has passed (expired pill). */
    EXPIRED,
}

/**
 * Expiry alerts for documents (docs/spec.md §2.5): by default 30, 14, 7 and 1 days before the expiry date, plus once
 * when it passes. The daily reminder worker runs these rules for the reminder linked to each document, so a document
 * alert is an ordinary reminder as far as scheduling, quiet hours and notifications go.
 */
object DocumentAlerts {
    val DEFAULT_DAYS: List<Int> = listOf(30, 14, 7, 1)

    /** Whole days from today to the expiry day; negative once passed. Null without an expiry date. */
    fun daysLeft(expiry: Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (expiry == null) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, Instant.ofEpochMilli(expiry).atZone(zone).toLocalDate())
    }

    /** The reminder lead time that makes the first alert due: the longest configured alert. */
    fun leadDays(alertDays: List<Int>): Int = alertDays.maxOrNull() ?: 0

    fun status(expiry: Long?, alertDays: List<Int>, now: Long, zone: ZoneId = ZoneId.systemDefault()): ExpiryStatus {
        val left = daysLeft(expiry, now, zone) ?: return ExpiryStatus.NONE
        return when {
            left < 0 -> ExpiryStatus.EXPIRED
            left <= leadDays(alertDays) -> ExpiryStatus.EXPIRING
            else -> ExpiryStatus.VALID
        }
    }

    /**
     * True when an alert is due and has not been sent yet. [lastNotifiedAt] is when the linked reminder last notified.
     * Each alert day fires once: a document added 5 days before expiry gets the 7-day alert straight away, then the
     * 1-day alert, then one alert when it expires.
     */
    fun shouldNotify(
        expiry: Long?,
        alertDays: List<Int>,
        lastNotifiedAt: Long?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val left = daysLeft(expiry, now, zone) ?: return false
        val expiryDay = Instant.ofEpochMilli(expiry!!).atZone(zone).toLocalDate()
        val threshold = if (left < 0) {
            0
        } else {
            // The latest alert already crossed is the smallest alert day that is still >= the days left.
            alertDays.filter { it >= left }.minOrNull() ?: return false
        }
        val crossedAt = expiryDay.minusDays(threshold.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        return lastNotifiedAt == null || lastNotifiedAt < crossedAt
    }

    /** Normalised alert days: positive, unique, largest first. */
    fun normalise(days: List<Int>): List<Int> = days.filter { it > 0 }.distinct().sortedDescending()
}
