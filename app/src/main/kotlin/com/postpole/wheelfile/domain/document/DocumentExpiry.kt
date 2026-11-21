package com.postpole.wheelfile.domain.document

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ExpiryStatus { NONE, VALID, EXPIRING, EXPIRED }

object DocumentExpiry {
    /** Whole days from today to the expiry day; negative once passed. Null without an expiry date. */
    fun daysLeft(expiry: Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        expiry ?: return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val due = Instant.ofEpochMilli(expiry).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, due)
    }

    /** Expiring means inside the earliest alert (30 days by default). */
    fun status(expiry: Long?, alertDays: List<Int>, now: Long, zone: ZoneId = ZoneId.systemDefault()): ExpiryStatus {
        val days = daysLeft(expiry, now, zone) ?: return ExpiryStatus.NONE
        return when {
            days < 0 -> ExpiryStatus.EXPIRED
            days <= DocumentAlerts.leadDays(alertDays) -> ExpiryStatus.EXPIRING
            else -> ExpiryStatus.VALID
        }
    }
}

/**
 * Expiry alerts at 30 / 14 / 7 / 1 days (user-editable per document). They run on the reminder engine: a document with an
 * expiry date has one linked reminder whose lead time is the earliest alert, and the daily check asks [shouldNotify].
 */
object DocumentAlerts {
    val DEFAULT_DAYS = listOf(30, 14, 7, 1)
    val CHOICES = DEFAULT_DAYS

    private const val DAY_MS = 86_400_000L
    private const val REPEAT_EXPIRED_DAYS = 7L
    private const val EXPIRED = -1

    /** Lead time for the linked reminder: the earliest alert. */
    fun leadDays(alertDays: List<Int>): Int = alertDays.maxOrNull() ?: DEFAULT_DAYS.first()

    /** The tightest alert [daysLeft] has reached (an alert of N days is reached at N days or fewer); [EXPIRED] once passed. */
    private fun threshold(alertDays: List<Int>, daysLeft: Long): Int? =
        if (daysLeft < 0) EXPIRED else alertDays.filter { it >= daysLeft }.minOrNull()

    /**
     * True when an alert is due: the document has reached a tighter alert than the one the last notification was sent for
     * (so each of 30 / 14 / 7 / 1 fires once), or it is expired and the last notification is a week old.
     */
    fun shouldNotify(
        alertDays: List<Int>,
        expiry: Long?,
        lastNotifiedAt: Long?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (alertDays.isEmpty()) return false
        val daysNow = DocumentExpiry.daysLeft(expiry, now, zone) ?: return false
        val current = threshold(alertDays, daysNow) ?: return false
        val last = lastNotifiedAt ?: return true
        val daysThen = DocumentExpiry.daysLeft(expiry, last, zone) ?: return true
        val before = threshold(alertDays, daysThen) ?: return true
        return when {
            current < before -> true
            current == EXPIRED -> now - last >= REPEAT_EXPIRED_DAYS * DAY_MS
            else -> false
        }
    }
}
