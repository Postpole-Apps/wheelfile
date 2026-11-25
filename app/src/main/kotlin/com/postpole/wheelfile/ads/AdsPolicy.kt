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

    /** An automatic Pro prompt was shown this session; prompts and interstitials never share a session (§5.4a). */
    PRO_PROMPT_SESSION,
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
    private val proPrompts: ProPromptSessionGate = ProPromptSessionGate.None,
) {
    private var shownThisSession = 0
    private var pumpSession = false
    private var quietAction = false

    /** Call after a save. A fill-up or EV charge blocks interstitials for the rest of the session. */
    fun onSaved(kind: SaveKind) {
        if (kind.isPumpSession) pumpSession = true
        proPrompts.onSaved(kind)
    }

    /**
     * True once the user has gone back from Vehicle detail to the Garage after viewing the History or Reports tab
     * (DECISIONS.md item 9). [InterstitialCoordinator] reads it to decide on `wf_interstitial_after_review`.
     */
    var reviewedBeforeGarage = false
        private set

    /** Call when the user leaves Vehicle detail for the Garage. [viewedReview] is true if History or Reports was opened. */
    fun onLeftVehicleDetail(viewedReview: Boolean) {
        if (viewedReview) reviewedBeforeGarage = true
    }

    /** Call once [reviewedBeforeGarage] has been acted on (or the Garage was shown without it). */
    fun clearReviewed() {
        reviewedBeforeGarage = false
    }

    /** Call after export, backup, restore or a PDF share. Blocks interstitials for the rest of the session. */
    fun onQuietAction(@Suppress("UNUSED_PARAMETER") action: QuietAction) {
        quietAction = true
    }

    /** Call when an interstitial was actually shown. */
    fun onInterstitialShown() {
        shownThisSession++
        history.record(clock())
        proPrompts.onInterstitialShown()
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
            proPrompts.proPromptShownThisSession -> InterstitialDecision.PRO_PROMPT_SESSION
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
