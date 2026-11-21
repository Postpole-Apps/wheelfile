package com.postpole.wheelfile.domain.document

import com.postpole.wheelfile.domain.entry.FieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DocumentAlertsTest {
    private val zone = ZoneOffset.UTC
    private fun day(s: String): Long = LocalDate.parse(s).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun at(s: String, hour: Int = 9): Long =
        LocalDate.parse(s).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val expiry = day("2026-12-31")
    private val days = DocumentAlerts.DEFAULT_DAYS

    @Test fun defaultAlertsAre30And14And7And1Days() {
        assertEquals(listOf(30, 14, 7, 1), DocumentAlerts.DEFAULT_DAYS)
        assertEquals(30, DocumentAlerts.leadDays(days))
    }

    @Test fun daysLeftCountsWholeLocalDays() {
        assertEquals(0L, DocumentAlerts.daysLeft(expiry, at("2026-12-31", 23), zone))
        assertEquals(10L, DocumentAlerts.daysLeft(expiry, at("2026-12-21"), zone))
        assertEquals(-1L, DocumentAlerts.daysLeft(expiry, at("2027-01-01", 0), zone))
        assertNull(DocumentAlerts.daysLeft(null, at("2026-12-21"), zone))
    }

    @Test fun statusIsValidThenExpiringThenExpired() {
        assertEquals(ExpiryStatus.NONE, DocumentAlerts.status(null, days, at("2026-12-01"), zone))
        assertEquals(ExpiryStatus.VALID, DocumentAlerts.status(expiry, days, at("2026-11-30"), zone))
        assertEquals(ExpiryStatus.EXPIRING, DocumentAlerts.status(expiry, days, at("2026-12-01"), zone))
        assertEquals(ExpiryStatus.EXPIRING, DocumentAlerts.status(expiry, days, at("2026-12-31"), zone))
        assertEquals(ExpiryStatus.EXPIRED, DocumentAlerts.status(expiry, days, at("2027-01-01"), zone))
    }

    @Test fun noAlertBeforeTheFirstAlertDay() {
        assertFalse(DocumentAlerts.shouldNotify(expiry, days, null, at("2026-11-30"), zone))
    }

    @Test fun eachAlertDayFiresOnceAsTheDateApproaches() {
        var last: Long? = null
        val fired = mutableListOf<String>()
        var date = LocalDate.parse("2026-11-25")
        while (!date.isAfter(LocalDate.parse("2027-01-05"))) {
            val now = at(date.toString())
            if (DocumentAlerts.shouldNotify(expiry, days, last, now, zone)) {
                fired += date.toString()
                last = now
            }
            date = date.plusDays(1)
        }
        // 30 days before (1 Dec), 14 (17 Dec), 7 (24 Dec), 1 (30 Dec), then once when it expires (1 Jan).
        assertEquals(listOf("2026-12-01", "2026-12-17", "2026-12-24", "2026-12-30", "2027-01-01"), fired)
    }

    @Test fun addingADocumentInsideTheWindowAlertsOnceForTheCrossedDay() {
        val now = at("2026-12-26") // 5 days left: the 7-day alert is the latest one crossed
        assertTrue(DocumentAlerts.shouldNotify(expiry, days, null, now, zone))
        assertFalse(DocumentAlerts.shouldNotify(expiry, days, now, at("2026-12-27"), zone))
        assertTrue(DocumentAlerts.shouldNotify(expiry, days, now, at("2026-12-30"), zone))
    }

    @Test fun customAlertDaysAreHonoured() {
        val custom = listOf(60, 3)
        assertEquals(60, DocumentAlerts.leadDays(custom))
        assertTrue(DocumentAlerts.shouldNotify(expiry, custom, null, at("2026-11-01"), zone))
        assertFalse(DocumentAlerts.shouldNotify(expiry, custom, at("2026-11-01"), at("2026-12-01"), zone))
        assertTrue(DocumentAlerts.shouldNotify(expiry, custom, at("2026-11-01"), at("2026-12-29"), zone))
    }

    @Test fun noExpiryNeverAlerts() {
        assertFalse(DocumentAlerts.shouldNotify(null, days, null, at("2026-12-31"), zone))
    }

    @Test fun normaliseKeepsPositiveUniqueDaysLargestFirst() {
        assertEquals(listOf(30, 7, 1), DocumentAlerts.normalise(listOf(1, 7, 30, 7, 0, -2)))
    }

    @Test fun formRequiresATitleAndAnExpiryAfterTheIssueDate() {
        val ok = DocumentFormState(title = "Policy", issueDate = day("2026-01-01"), expiryDate = day("2026-12-31"))
        assertTrue(DocumentForm.validate(ok).isValid)
        assertEquals(FieldError.REQUIRED, DocumentForm.validate(ok.copy(title = " ")).errors[DocumentField.TITLE])
        assertEquals(
            FieldError.INVALID,
            DocumentForm.validate(ok.copy(expiryDate = day("2025-12-31"))).errors[DocumentField.EXPIRY],
        )
        assertTrue(DocumentForm.validate(ok.copy(issueDate = null, expiryDate = null)).isValid)
    }

    @Test fun formTogglesAlertDaysAndKeepsThemSorted() {
        val s = DocumentForm.toggleAlertDay(DocumentFormState(), 60)
        assertEquals(listOf(60, 30, 14, 7, 1), s.alertDays)
        assertEquals(listOf(60, 14, 7, 1), DocumentForm.toggleAlertDay(s, 30).alertDays)
    }
}
