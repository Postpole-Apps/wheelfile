package com.postpole.wheelfile.ads

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What starts an automatic Pro prompt (spec v0.2.2 §5.4a). The [id] is the analytics enum value. */
enum class ProPromptTrigger(val id: String) {
    ENTRIES_MILESTONE("entries_milestone"),
    SERVICE_LOGGED("service_logged"),
    SECOND_VEHICLE_ADDED("second_vehicle_added"),
    REPORTS_3_MONTHS("reports_3_months"),
}

enum class ProPromptSurface(val id: String) {
    SHEET("sheet"),
    CARD("card"),
}

/** How a prompt was dismissed (analytics enum). */
enum class ProPromptDismissMethod(val id: String) {
    CLOSE("close"),
    NOT_NOW("not_now"),
    SWIPE("swipe"),
}

/** What the user owns. `remove_ads` owners get cards instead of sheets; Pro owners never get a prompt. */
enum class ProAccess { FREE, REMOVE_ADS, PRO }

enum class ProPromptDecision {
    SHOW,
    NOT_PENDING,
    EXPIRED,
    DISABLED_BY_CONFIG,
    PRO_OWNER,
    FIRST_SESSION,
    TOO_SOON_AFTER_INSTALL,
    MIN_SESSION,
    NEXT_SESSION,
    PUMP_SESSION,
    HARD_GATE_RECENT,
    INTERSTITIAL_SESSION,
    SESSION_CAP,
    SOFT_WINDOW,
    CARD_RESHOW_WINDOW,
    SHEET_DISMISS_LIMIT,
    CARD_DISMISS_LIMIT,
    NO_CARD_SLOT,
}

/** The decision plus the surface to render, which can differ from the qualified one (sheet turned into a card). */
data class ProPromptOutcome(val decision: ProPromptDecision, val surface: ProPromptSurface?) {
    val shouldShow: Boolean get() = decision == ProPromptDecision.SHOW
}

/** A prompt that qualified but has not been shown yet. Dropped [ProPromptPolicy.DEFERRAL_MS] after [qualifiedAt]. */
data class PendingProPrompt(
    val surface: ProPromptSurface,
    val qualifiedAt: Long,
    /** Earliest session number it may show in (next session for pump-session cases). */
    val minSession: Int = 0,
)

/**
 * Prompt state. This is app state: it is never exported to CSV and never written into `.wheelfile` backups.
 * Count and time maps are keyed by `"<trigger id>:<surface id>"`.
 */
data class ProPromptState(
    val sessionCount: Int = 0,
    val installTimeMs: Long = 0L,
    val shownCounts: Map<String, Int> = emptyMap(),
    val dismissedCounts: Map<String, Int> = emptyMap(),
    val lastShownAt: Map<String, Long> = emptyMap(),
    val lastDismissedAt: Map<String, Long> = emptyMap(),
    val pending: Map<String, PendingProPrompt> = emptyMap(),
    val lastHardGateAt: Long? = null,
    val interstitialThisSession: Boolean = false,
    val proPromptThisSession: Boolean = false,
    val pumpSession: Boolean = false,
) {
    internal fun count(map: Map<String, Int>, trigger: ProPromptTrigger, surface: ProPromptSurface) =
        map[key(trigger, surface)] ?: 0

    /** Last time any soft prompt (sheet or card) was shown. */
    val lastSoftShownAt: Long? get() = lastShownAt.values.maxOrNull()

    val sheetDismissals: Int
        get() = ProPromptTrigger.entries.sumOf { count(dismissedCounts, it, ProPromptSurface.SHEET) }

    companion object {
        fun key(trigger: ProPromptTrigger, surface: ProPromptSurface) = "${trigger.id}:${surface.id}"
    }
}

/** Persists [ProPromptState]. The app implementation is DataStore backed (see `ProPromptStore.kt`). */
interface ProPromptStateStore {
    suspend fun load(): ProPromptState

    suspend fun save(state: ProPromptState)
}

class InMemoryProPromptStateStore(var state: ProPromptState = ProPromptState()) : ProPromptStateStore {
    override suspend fun load() = state
    override suspend fun save(state: ProPromptState) {
        this.state = state
    }
}

/** The part of [ProPromptPolicy] that [AdsPolicy] needs to keep prompts and interstitials in separate sessions. */
interface ProPromptSessionGate {
    /** True when an automatic Pro prompt was shown in the current session. */
    val proPromptShownThisSession: Boolean

    /** Called by [AdsPolicy] when an interstitial was shown. */
    fun onInterstitialShown()

    /** Called by [AdsPolicy] after a save, so a fill-up or charge starts the pump-session rule for prompts too. */
    fun onSaved(kind: SaveKind)

    object None : ProPromptSessionGate {
        override val proPromptShownThisSession = false
        override fun onInterstitialShown() = Unit
        override fun onSaved(kind: SaveKind) = Unit
    }
}

/**
 * Remote Config values for automatic Pro prompts. Remote Config may only tighten the hard-coded caps or switch
 * the prompts off, so the values are clamped on the way in.
 */
data class ProPromptConfig(
    val enabled: Boolean = true,
    val minSession: Int = DEFAULT_MIN_SESSION,
    val entriesThreshold: Int = DEFAULT_ENTRIES_THRESHOLD,
) {
    fun coerced() = copy(
        minSession = minSession.coerceAtLeast(MIN_SESSION_FLOOR),
        entriesThreshold = entriesThreshold.coerceIn(ENTRIES_THRESHOLD_MIN, ENTRIES_THRESHOLD_MAX),
    )

    companion object {
        const val DEFAULT_MIN_SESSION = 3
        const val MIN_SESSION_FLOOR = 2
        const val DEFAULT_ENTRIES_THRESHOLD = 10
        const val ENTRIES_THRESHOLD_MIN = 5
        const val ENTRIES_THRESHOLD_MAX = 25
    }
}

/**
 * Automatic Pro prompt policy (spec v0.2.2 §5.4a). Time comes from [clock] (epoch millis).
 *
 * Flow: the feature calls [qualify] when a trigger's condition is met, then [evaluate] at the next rest point.
 * If the outcome says to show, the feature shows the prompt and calls [onShown]. The caps are only used up by
 * [onShown], so a prompt that could not be shown stays deferred and is dropped [DEFERRAL_MS] after it qualified.
 *
 * A session is a cold start, or a return to the foreground after at least [SESSION_GAP_MS] in the background.
 */
class ProPromptPolicy(
    private val clock: () -> Long,
    private val store: ProPromptStateStore,
) : ProPromptSessionGate {
    private val mutex = Mutex()

    // Threading: the main thread (AdsPolicy -> onInterstitialShown/onSaved) and coroutines on other dispatchers
    // (startSession, onForegrounded, qualify, evaluate, onShown, ...) all change the state. Every change goes
    // through AtomicReference.updateAndGet, so a main-thread flag can never be lost to a concurrent
    // copy-and-write-back of an older state. The mutex only orders the suspending persist calls.
    // Transform lambdas must be pure: updateAndGet may retry them.
    private val stateRef = AtomicReference(ProPromptState())
    private val state: ProPromptState get() = stateRef.get()

    @Volatile
    private var backgroundedAt: Long? = null

    val current: ProPromptState get() = state

    override val proPromptShownThisSession: Boolean get() = state.proPromptThisSession

    /** Cold start: loads the stored state and begins a new session. [installTimeMs] is stored on first run. */
    suspend fun startSession(installTimeMs: Long): Unit = mutex.withLock {
        val loaded = store.load()
        backgroundedAt = null
        val first = newSession(loaded.copy(installTimeMs = loaded.installTimeMs.takeIf { it > 0 } ?: installTimeMs))
        update { first }
    }

    /**
     * Delete all data cleared the stored prompt state. Drop the in-memory copy too, so the next write cannot restore
     * the old counts and dates: the app starts over as a fresh install in its current session. Nothing is persisted
     * here; the store was already cleared.
     */
    suspend fun resetAfterWipe(): Unit = mutex.withLock {
        stateRef.set(ProPromptState(sessionCount = 1, installTimeMs = clock()))
    }

    fun onBackgrounded() {
        backgroundedAt = clock()
    }

    /** Returns true if this return to the foreground began a new session (≥ 30 min away). */
    suspend fun onForegrounded(): Boolean = mutex.withLock {
        val left = backgroundedAt ?: return false
        backgroundedAt = null
        if (clock() - left < SESSION_GAP_MS) return false
        update { newSession(it) }
        true
    }

    /**
     * Called by [AdsPolicy.onSaved]. A fill-up or charge makes this a pump-session: no prompt until the next session.
     * Main thread; the flag is persisted with the next state write (a new session resets it anyway).
     */
    override fun onSaved(kind: SaveKind) {
        if (kind.isPumpSession) stateRef.updateAndGet { it.copy(pumpSession = true) }
    }

    /** Call when a hard-gate paywall was shown; automatic prompts then wait 48 h. */
    suspend fun onHardGateShown(): Unit = mutex.withLock {
        val now = clock()
        update { it.copy(lastHardGateAt = now) }
    }

    override fun onInterstitialShown() {
        // Called from AdsPolicy on the main thread; the flag is also persisted with the next state write.
        stateRef.updateAndGet { it.copy(interstitialThisSession = true) }
    }

    /**
     * A trigger's condition was met. [earnedBy] is the save that earned it, if any: a fill-up or charge turns the
     * prompt into an inline card for the next session. An already pending trigger keeps its original time, so
     * the 7-day drop can't be postponed by qualifying again. [nextSession] also holds a prompt back until the next
     * session without a pump-session save (a card specified as "next session", such as a second vehicle).
     */
    suspend fun qualify(
        trigger: ProPromptTrigger,
        surface: ProPromptSurface,
        earnedBy: SaveKind? = null,
        nextSession: Boolean = false,
    ): Unit =
        mutex.withLock {
            if (trigger.id in state.pending) return@withLock
            val pump = earnedBy?.isPumpSession == true
            val now = clock()
            update {
                val prompt = PendingProPrompt(
                    surface = if (pump) ProPromptSurface.CARD else surface,
                    qualifiedAt = now,
                    minSession = if (pump || nextSession) it.sessionCount + 1 else 0,
                )
                it.copy(pending = it.pending + (trigger.id to prompt))
            }
        }

    /**
     * Decides whether [trigger] may show now. Also drops expired prompts and turns pump-blocked sheets, and sheets
     * of remove_ads owners, into cards. [hasCardSlot] is false on screens without an inline card slot (Garage
     * always has one); a card then stays pending until a rest point that has one, or until it is dropped.
     */
    suspend fun evaluate(
        trigger: ProPromptTrigger,
        config: ProPromptConfig,
        access: ProAccess,
        hasCardSlot: Boolean = true,
    ): ProPromptOutcome = mutex.withLock {
        val now = clock()
        val cfg = config.coerced()
        val pending = state.pending[trigger.id] ?: return ProPromptOutcome(ProPromptDecision.NOT_PENDING, null)
        if (now - pending.qualifiedAt > DEFERRAL_MS) {
            update { it.copy(pending = it.pending - trigger.id) }
            return ProPromptOutcome(ProPromptDecision.EXPIRED, null)
        }
        var surface = pending.surface
        fun out(d: ProPromptDecision) = ProPromptOutcome(d, surface)

        if (!cfg.enabled) return out(ProPromptDecision.DISABLED_BY_CONFIG)
        if (access == ProAccess.PRO) return out(ProPromptDecision.PRO_OWNER)
        if (access == ProAccess.REMOVE_ADS && surface == ProPromptSurface.SHEET) {
            // §E: a remove_ads owner gets the inline card for a sheet trigger. Same trigger and same qualifiedAt
            // (the 7-day drop is unchanged); the card caps apply and the sheet counters are never touched.
            surface = ProPromptSurface.CARD
            update { it.copy(pending = it.pending + (trigger.id to pending.copy(surface = surface))) }
        }
        if (state.pumpSession) {
            if (surface == ProPromptSurface.SHEET) {
                surface = ProPromptSurface.CARD
                update {
                    val next = pending.copy(surface = surface, minSession = it.sessionCount + 1)
                    it.copy(pending = it.pending + (trigger.id to next))
                }
            }
            return out(ProPromptDecision.PUMP_SESSION)
        }
        if (state.sessionCount <= 1) return out(ProPromptDecision.FIRST_SESSION)
        if (now - state.installTimeMs < MIN_INSTALL_AGE_MS) return out(ProPromptDecision.TOO_SOON_AFTER_INSTALL)
        if (pending.minSession > state.sessionCount) return out(ProPromptDecision.NEXT_SESSION)
        if (surface == ProPromptSurface.SHEET && state.sessionCount < cfg.minSession) {
            return out(ProPromptDecision.MIN_SESSION)
        }
        if (state.lastHardGateAt?.let { now - it < HARD_GATE_QUIET_MS } == true) {
            return out(ProPromptDecision.HARD_GATE_RECENT)
        }
        if (state.interstitialThisSession) return out(ProPromptDecision.INTERSTITIAL_SESSION)
        if (state.proPromptThisSession) return out(ProPromptDecision.SESSION_CAP)
        if (state.lastSoftShownAt?.let { now - it < SOFT_WINDOW_MS } == true) {
            return out(ProPromptDecision.SOFT_WINDOW)
        }
        if (surface == ProPromptSurface.SHEET) {
            if (state.sheetDismissals >= MAX_SHEET_DISMISSALS) return out(ProPromptDecision.SHEET_DISMISS_LIMIT)
        } else {
            val key = ProPromptState.key(trigger, ProPromptSurface.CARD)
            if ((state.dismissedCounts[key] ?: 0) >= MAX_CARD_DISMISSALS) {
                return out(ProPromptDecision.CARD_DISMISS_LIMIT)
            }
            if (state.lastDismissedAt[key]?.let { now - it < CARD_RESHOW_MS } == true) {
                return out(ProPromptDecision.CARD_RESHOW_WINDOW)
            }
        }
        if (surface == ProPromptSurface.CARD && !hasCardSlot) return out(ProPromptDecision.NO_CARD_SLOT)
        out(ProPromptDecision.SHOW)
    }

    /** Call when the prompt was actually shown. This is what uses up the session and 7-day caps. */
    suspend fun onShown(trigger: ProPromptTrigger, surface: ProPromptSurface): Unit = mutex.withLock {
        val key = ProPromptState.key(trigger, surface)
        val now = clock()
        update {
            it.copy(
                shownCounts = it.shownCounts + (key to (it.shownCounts[key] ?: 0) + 1),
                lastShownAt = it.lastShownAt + (key to now),
                pending = it.pending - trigger.id,
                proPromptThisSession = true,
            )
        }
    }

    suspend fun onDismissed(trigger: ProPromptTrigger, surface: ProPromptSurface): Unit = mutex.withLock {
        val key = ProPromptState.key(trigger, surface)
        val now = clock()
        update {
            it.copy(
                dismissedCounts = it.dismissedCounts + (key to (it.dismissedCounts[key] ?: 0) + 1),
                lastDismissedAt = it.lastDismissedAt + (key to now),
            )
        }
    }

    private fun newSession(from: ProPromptState) = from.copy(
        sessionCount = from.sessionCount + 1,
        interstitialThisSession = false,
        proPromptThisSession = false,
        pumpSession = false,
    )

    private suspend fun update(transform: (ProPromptState) -> ProPromptState) {
        store.save(stateRef.updateAndGet(transform))
    }

    companion object {
        const val SESSION_GAP_MS = 30L * 60 * 1_000
        const val MIN_INSTALL_AGE_MS = 3L * DAY_MS
        const val SOFT_WINDOW_MS = 7L * DAY_MS
        const val CARD_RESHOW_MS = 14L * DAY_MS
        const val DEFERRAL_MS = 7L * DAY_MS
        const val HARD_GATE_QUIET_MS = 48L * 60 * 60 * 1_000
        const val MAX_SHEET_DISMISSALS = 3
        const val MAX_CARD_DISMISSALS = 3
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1_000
