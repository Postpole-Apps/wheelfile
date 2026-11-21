package com.postpole.wheelfile.ads

import java.time.Instant
import java.time.ZoneId

/** What the user just saved. Fill-ups and EV charges start the pump-session rule; the rest do not. */
enum class SaveKind(val isPumpSession: Boolean) {
    FILL_UP(true),
    EV_CHARGE(true),
    SERVICE(false),
    EXPENSE(false),
}

/** Flows after which no interstitial may show for the rest of the session (spec §5.2). */
enum class QuietAction { EXPORT, BACKUP, RESTORE, PDF_SHARE }

enum class InterstitialDecision {
    ALLOWED,
    ADS_HIDDEN,
    CONSENT,
    DISABLED_BY_CONFIG,
    FIRST_SESSION,
    TOO_SOON_AFTER_INSTALL,
    MIN_INTERVAL,
    SESSION_CAP,
    DAY_CAP,
    PUMP_SESSION,
    QUIET_ACTION,
}

/** Persists the times interstitials were shown so day caps and the interval survive restarts. */
interface InterstitialHistory {
    /** Epoch millis of past interstitial impressions, oldest first. */
    fun shownAt(): List<Long>

    fun record(epochMillis: Long)
}

class InMemoryInterstitialHistory(initial: List<Long> = emptyList()) : InterstitialHistory {
    private val times = initial.toMutableList()
    override fun shownAt(): List<Long> = times.toList()
    override fun record(epochMillis: Long) {
        times += epochMillis
    }
}

/**
 * Decides whether an interstitial may show. Pure Kotlin: time comes from [clock] (epoch millis).
 *
 * A "session" is one process lifetime, so an [AdsPolicy] instance is created once per launch.
 * [isFirstSession] is true only for the very first launch after install.
 */
class AdsPolicy(
    private val clock: () -> Long,
    private val installTimeMs: Long,
    private val isFirstSession: Boolean,
    private val history: InterstitialHistory = InMemoryInterstitialHistory(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private var shownThisSession = 0
    private var pumpSession = false
    private var quietAction = false

    /** True once the user opened the History or Reports tab this session. Recorded for the V7 interstitial; shows nothing yet. */
    var reviewed = false
        private set

    /** Call when the History or Reports tab is viewed. The future "back to Garage" interstitial needs it (spec §5.2). */
    fun onReviewViewed() {
        reviewed = true
    }

    /** Call when the user leaves Vehicle detail for Garage. Returns whether a review was viewed, then clears the flag. */
    fun consumeReviewed(): Boolean = reviewed.also { reviewed = false }

    /** Call after a save. A fill-up or EV charge blocks interstitials for the rest of the session. */
    fun onSaved(kind: SaveKind) {
        if (kind.isPumpSession) pumpSession = true
    }

    /** Call after export, backup, restore or a PDF share. Blocks interstitials for the rest of the session. */
    fun onQuietAction(@Suppress("UNUSED_PARAMETER") action: QuietAction) {
        quietAction = true
    }

    /** Call when an interstitial was actually shown. */
    fun onInterstitialShown() {
        shownThisSession++
        history.record(clock())
    }

    fun evaluate(config: AdsConfig, adsHidden: Boolean, canRequestAds: Boolean): InterstitialDecision {
        val now = clock()
        val shown = history.shownAt()
        return when {
            adsHidden -> InterstitialDecision.ADS_HIDDEN
            !canRequestAds -> InterstitialDecision.CONSENT
            !config.interstitialsAllowed -> InterstitialDecision.DISABLED_BY_CONFIG
            config.interstitialBlockFirstSession && isFirstSession -> InterstitialDecision.FIRST_SESSION
            now - installTimeMs < config.interstitialMinInstallDays * MS_PER_DAY ->
                InterstitialDecision.TOO_SOON_AFTER_INSTALL
            pumpSession -> InterstitialDecision.PUMP_SESSION
            quietAction -> InterstitialDecision.QUIET_ACTION
            shownThisSession >= config.interstitialMaxPerSession -> InterstitialDecision.SESSION_CAP
            shown.count { sameDay(it, now) } >= config.interstitialMaxPerDay -> InterstitialDecision.DAY_CAP
            shown.lastOrNull()?.let { now - it < config.interstitialMinIntervalSec * 1_000L } == true ->
                InterstitialDecision.MIN_INTERVAL
            else -> InterstitialDecision.ALLOWED
        }
    }

    fun canShowInterstitial(config: AdsConfig, adsHidden: Boolean, canRequestAds: Boolean): Boolean =
        evaluate(config, adsHidden, canRequestAds) == InterstitialDecision.ALLOWED

    private fun sameDay(a: Long, b: Long) =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

    private companion object {
        const val MS_PER_DAY = 24L * 60 * 60 * 1_000
    }
}
