package com.postpole.wheelfile.domain.document

import com.postpole.wheelfile.data.db.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DocumentAlertsTest {
    private val zone = ZoneId.of("UTC")
    private val expiry = LocalDate.of(2026, 7, 15).atStartOfDay(zone).toInstant().toEpochMilli()
    private val defaults = DocumentAlerts.DEFAULT_DAYS

    private fun at(y: Int, m: Int, d: Int, hour: Int = 9) = LocalDate.of(y, m, d).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun notify(now: Long, last: Long?, alerts: List<Int> = defaults) =
        DocumentAlerts.shouldNotify(alerts, expiry, last, now, zone)

    // ---- status ----

    @Test fun statusIsNoneValidExpiringOrExpired() {
        fun status(now: Long) = DocumentExpiry.status(expiry, defaults, now, zone)
        assertEquals(ExpiryStatus.NONE, DocumentExpiry.status(null, defaults, at(2026, 6, 1), zone))
        assertEquals(ExpiryStatus.VALID, status(at(2026, 6, 14)))
        assertEquals(ExpiryStatus.EXPIRING, status(at(2026, 6, 15))) // 30 days left
        assertEquals(ExpiryStatus.EXPIRING, status(at(2026, 7, 15))) // the day itself
        assertEquals(ExpiryStatus.EXPIRED, status(at(2026, 7, 16)))
    }

    @Test fun daysLeftCountsCalendarDays() {
        assertEquals(30L, DocumentExpiry.daysLeft(expiry, at(2026, 6, 15, 23), zone))
        assertEquals(-1L, DocumentExpiry.daysLeft(expiry, at(2026, 7, 16, 0), zone))
        assertNull(DocumentExpiry.daysLeft(null, at(2026, 7, 16), zone))
    }

    @Test fun anEarlierFirstAlertWidensTheExpiringWindow() {
        // The FR preset alerts when the test window opens, about 6 months before.
        assertEquals(182, DocumentAlerts.leadDays(listOf(182, 30, 14, 7, 1)))
        assertEquals(ExpiryStatus.EXPIRING, DocumentExpiry.status(expiry, listOf(182, 30), at(2026, 2, 1), zone))
    }

    // ---- alerts at 30 / 14 / 7 / 1 ----

    @Test fun nothingBeforeThirtyDays() {
        assertFalse(notify(at(2026, 6, 14), null))
    }

    @Test fun eachAlertFiresOnceAsTheDateComesCloser() {
        // 30 days left: first alert.
        assertTrue(notify(at(2026, 6, 15), null))
        // The daily check runs again the next days: nothing new until 14 days.
        assertFalse(notify(at(2026, 6, 16), at(2026, 6, 15)))
        assertFalse(notify(at(2026, 6, 30), at(2026, 6, 15))) // 15 days left
        assertTrue(notify(at(2026, 7, 1), at(2026, 6, 15))) // 14 days left
        assertFalse(notify(at(2026, 7, 5), at(2026, 7, 1)))
        assertTrue(notify(at(2026, 7, 8), at(2026, 7, 1))) // 7 days left
        assertFalse(notify(at(2026, 7, 10), at(2026, 7, 8)))
        assertTrue(notify(at(2026, 7, 14), at(2026, 7, 8))) // 1 day left
        assertFalse(notify(at(2026, 7, 15), at(2026, 7, 14))) // due today: still the last alert
    }

    @Test fun aSkippedAlertStillFiresTheTightestOne() {
        // The phone was off for weeks: from the 30 day alert straight to 1 day left.
        assertTrue(notify(at(2026, 7, 14), at(2026, 6, 15)))
    }

    @Test fun expiredNotifiesOnceThenWeekly() {
        assertTrue(notify(at(2026, 7, 16), at(2026, 7, 14))) // crossed into expired
        assertFalse(notify(at(2026, 7, 19), at(2026, 7, 16)))
        assertTrue(notify(at(2026, 7, 23), at(2026, 7, 16))) // a week later
    }

    @Test fun userEditedAlertDaysAreHonoured() {
        val only7 = listOf(7)
        assertFalse(notify(at(2026, 6, 15), null, only7))
        assertTrue(notify(at(2026, 7, 8), null, only7))
        assertFalse(notify(at(2026, 7, 9), at(2026, 7, 8), only7))
    }

    @Test fun noExpiryOrNoAlertsMeansNoNotification() {
        assertFalse(DocumentAlerts.shouldNotify(defaults, null, null, at(2026, 7, 1), zone))
        assertFalse(notify(at(2026, 7, 1), null, emptyList()))
    }

    // ---- form ----

    @Test fun formRequiresATitleAndRejectsExpiryBeforeIssue() {
        val ok = DocumentFormState(type = DocumentType.INSURANCE, title = "Policy")
        assertTrue(DocumentForm.validate(ok).isEmpty())
        assertEquals(DocumentError.REQUIRED, DocumentForm.validate(ok.copy(title = " ")).getValue(DocumentField.TITLE))
        assertEquals(
            DocumentError.EXPIRY_BEFORE_ISSUE,
            DocumentForm.validate(ok.copy(issueDate = expiry, expiryDate = expiry - 1)).getValue(DocumentField.EXPIRY),
        )
    }

    @Test fun alertsAreOnlyKeptWithAnExpiryDate() {
        val state = DocumentFormState(title = "MOT", alertDays = setOf(7, 30))
        assertTrue(DocumentForm.toDocument(state, 1).alertDays.isEmpty())
        assertEquals(listOf(30, 7), DocumentForm.toDocument(state.copy(expiryDate = expiry), 1).alertDays)
        // A document saved without a date offers the defaults again once one is added.
        val reopened = DocumentForm.fromDocument(DocumentForm.toDocument(state, 1))
        assertEquals(defaults.toSet(), reopened.alertDays)
    }
}
