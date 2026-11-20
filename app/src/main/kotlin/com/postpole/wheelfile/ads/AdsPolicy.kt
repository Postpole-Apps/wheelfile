package com.postpole.wheelfile.ads

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Things that happen during a session and affect whether an interstitial may show (docs/spec.md §5.2). */
enum class SessionEvent {
    /** Pump-session rule: no interstitial for the rest of the session. */
    FILL_UP_SAVED,

    /** Pump-session rule: an EV charge counts like a fill-up. */
    CHARGE_SAVED,

    /** Service and expense saves do not count towards the pump-session rule. */
    SERVICE_SAVED,
    EXPENSE_SAVED,

    /** No interstitial after export, backup, restore or a PDF share (the rest of the session). */
    EXPORTED,
    BACKED_UP,
    RESTORED,
    PDF_SHARED,
}

/**
 * Interstitial history that survives restarts. The caller persists it (for example in DataStore) and
 * passes the saved value back in when it creates the next session's [AdsPolicy].
 */
data class AdsHistory(
    val installTimeMs: Long,
    val lastInterstitialMs: Long? = null,
    /** Local date (epoch day) that [shownOnDay] counts for. */
    val dayEpoch: Long? = null,
    val shownOnDay: Int = 0,
)

/**
 * Pure interstitial pacing rules. One instance per app session. It only answers "may an interstitial
 * show now?"; whether ads are hidden or consent allows requests is checked separately by the caller.
 */
class AdsPolicy(
    history: AdsHistory,
    /** True when this is the first session after install (`ads_interstitial_block_first_session`). */
    private val firstSession: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    var history: AdsHistory = history
        private set

    private var shownThisSession = 0
    private var suppressedForSession = false

    fun onEvent(event: SessionEvent) {
        when (event) {
            SessionEvent.FILL_UP_SAVED,
            SessionEvent.CHARGE_SAVED,
            SessionEvent.EXPORTED,
            SessionEvent.BACKED_UP,
            SessionEvent.RESTORED,
            SessionEvent.PDF_SHARED,
            -> suppressedForSession = true
            SessionEvent.SERVICE_SAVED, SessionEvent.EXPENSE_SAVED -> Unit
        }
    }

    fun canShowInterstitial(config: AdsConfig): Boolean {
        if (!config.adsEnabled || !config.interstitialEnabled) return false
        if (suppressedForSession) return false
        if (firstSession && config.interstitialBlockFirstSession) return false

        val now = clock()
        val installedDays = TimeUnit.MILLISECONDS.toDays((now - history.installTimeMs).coerceAtLeast(0))
        if (installedDays < config.interstitialMinInstallDays) return false

        if (shownThisSession >= config.interstitialMaxPerSession) return false
        if (shownToday(now) >= config.interstitialMaxPerDay) return false

        val last = history.lastInterstitialMs
        if (last != null && now - last < TimeUnit.SECONDS.toMillis(config.interstitialMinIntervalSec.toLong())) {
            return false
        }
        return true
    }

    /** Call when an interstitial was actually shown. */
    fun recordInterstitialShown() {
        val now = clock()
        history = history.copy(
            lastInterstitialMs = now,
            dayEpoch = epochDay(now),
            shownOnDay = shownToday(now) + 1,
        )
        shownThisSession++
    }

    private fun shownToday(now: Long): Int =
        if (history.dayEpoch == epochDay(now)) history.shownOnDay else 0

    private fun epochDay(ms: Long): Long = LocalDate.ofInstant(Instant.ofEpochMilli(ms), zone).toEpochDay()
}
