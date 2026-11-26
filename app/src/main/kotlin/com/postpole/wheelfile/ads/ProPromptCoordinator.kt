package com.postpole.wheelfile.ads

import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.logProPromptCtaTap
import com.postpole.wheelfile.analytics.logProPromptDismissed
import com.postpole.wheelfile.analytics.logProPromptShown
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.ui.pro.PaywallTrigger
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A prompt a rest point decided to show now, on the [surface] that screen renders. */
data class ActiveProPrompt(val trigger: ProPromptTrigger, val surface: ProPromptSurface)

/** The main screens that are rest points, and what each can host. Pass `hasCardSlot` to the policy from here. */
enum class ProPromptRestPoint(val hasCardSlot: Boolean, val hasSheetHost: Boolean) {
    /** Garage has the inline card slot between the hero card and the other vehicles. No sheets here. */
    GARAGE(hasCardSlot = true, hasSheetHost = false),

    /** Vehicle detail › Overview hosts the sheet. It has no card slot, so a card waits for the Garage. */
    VEHICLE_OVERVIEW(hasCardSlot = false, hasSheetHost = true),

    /** Vehicle detail › Reports has the inline card slot after the range chips. It only hosts `reports_3_months`. */
    VEHICLE_REPORTS(hasCardSlot = true, hasSheetHost = false),
}

/**
 * Connects the features to [ProPromptPolicy] for `second_vehicle_added` and `entries_milestone` (spec v0.2.2 §5.4a).
 * It owns no caps: every rule is in the policy. Failures here never break a save, they are recorded as non-fatals.
 */
@Singleton
class ProPromptCoordinator internal constructor(
    private val policy: ProPromptPolicy,
    private val config: () -> ProPromptConfig,
    private val access: () -> ProAccess,
    private val totalEntries: suspend () -> Int,
    private val analytics: Analytics,
    private val serviceGate: ServiceLoggedGate? = null,
) {
    @Inject
    constructor(
        policy: ProPromptPolicy,
        configSource: AdsConfigSource,
        billing: BillingRepository,
        repo: VehicleRepository,
        analytics: Analytics,
        serviceGate: ServiceLoggedGate,
    ) : this(
        policy,
        { configSource.config.value.proPrompt },
        {
            val owned = billing.entitlements.value
            when {
                owned.ownsPro -> ProAccess.PRO
                owned.ownsRemoveAds -> ProAccess.REMOVE_ADS
                else -> ProAccess.FREE
            }
        },
        repo::totalEntryCount,
        analytics,
        serviceGate,
    )

    /**
     * Call after a new service was saved at [savedAt] (not an edit), after [onEntrySaved]. The first service after
     * a reminder fired qualifies `service_logged` as a sheet, once ever; the policy turns it into a next-session card
     * when the pump-session rule or `remove_ads` ownership blocks the sheet. Pro owners never get it: the policy
     * returns `PRO_OWNER` and the 7-day drop clears it.
     */
    suspend fun onServiceSaved(savedAt: Long) = guarded {
        if (serviceGate?.tryConsume(savedAt) != true) return@guarded
        policy.qualify(ProPromptTrigger.SERVICE_LOGGED, ProPromptSurface.SHEET)
    }

    /**
     * Call after a new entry was saved (not an edit). The Nth saved entry, counted from the database so deleted
     * entries do not count, qualifies `entries_milestone`: a fill-up or charge earns a next-session Garage card,
     * a service or expense earns a sheet (the policy turns it into a card when the pump-session rule or
     * `remove_ads` ownership blocks the sheet).
     */
    suspend fun onEntrySaved(kind: SaveKind) = guarded {
        markSaveCompleted()
        if (totalEntries() != config().coerced().entriesThreshold) return@guarded
        val surface = if (kind.isPumpSession) ProPromptSurface.CARD else ProPromptSurface.SHEET
        policy.qualify(ProPromptTrigger.ENTRIES_MILESTONE, surface, earnedBy = kind)
    }

    /** Call after a vehicle was added. The 2nd active vehicle qualifies a Garage card for the next session. */
    suspend fun onVehicleAdded(activeCount: Int) = guarded {
        markSaveCompleted()
        if (activeCount != SECOND_VEHICLE) return@guarded
        policy.qualify(ProPromptTrigger.SECOND_VEHICLE_ADDED, ProPromptSurface.CARD, nextSession = true)
    }

    /**
     * The Reports tab was opened for a vehicle whose first entry is at [firstEntryAt]. At least 3 calendar months
     * before [now] qualifies `reports_3_months` (a card, no Remote Config key). Qualifying again is a no-op.
     */
    suspend fun onReportsOpened(firstEntryAt: Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()) = guarded {
        if (hasThreeMonthsOfEntries(firstEntryAt, now, zone)) {
            policy.qualify(ProPromptTrigger.REPORTS_3_MONTHS, ProPromptSurface.CARD)
        }
    }

    @Volatile private var saveCompleted = false

    /**
     * A save or finish completed in this process (entry, service, expense, vehicle). The next main screen to resume
     * consumes it with [consumeSaveCompleted] and only then is a rest point. It is in memory only, so a cold start
     * never has it set.
     */
    fun markSaveCompleted() {
        saveCompleted = true
    }

    /** One-shot: true once after [markSaveCompleted]. Launch and plain navigation find it false, so they never evaluate. */
    fun consumeSaveCompleted(): Boolean {
        val was = saveCompleted
        saveCompleted = false
        return was
    }

    /**
     * Call when [point] has settled (a main screen after a save or finish, never over a form or a sheet). Returns the
     * prompt to render, or null. A shown prompt uses up the caps and logs `pro_prompt_shown`.
     */
    suspend fun onRestPoint(point: ProPromptRestPoint): ActiveProPrompt? = guarded {
        val candidates = if (point == ProPromptRestPoint.VEHICLE_REPORTS) REPORTS_ORDER else REST_ORDER
        for (trigger in candidates) {
            val outcome = policy.evaluate(trigger, config(), access(), hasCardSlot = point.hasCardSlot)
            val surface = outcome.surface
            if (!outcome.shouldShow || surface == null) continue
            // A sheet cannot show on the Garage: it stays pending for the next rest point that can host it.
            if (surface == ProPromptSurface.SHEET && !point.hasSheetHost) continue
            policy.onShown(trigger, surface)
            analytics.logProPromptShown(trigger, surface)
            return@guarded ActiveProPrompt(trigger, surface)
        }
        null
    }

    suspend fun onDismissed(prompt: ActiveProPrompt, method: ProPromptDismissMethod) = guarded {
        policy.onDismissed(prompt.trigger, prompt.surface)
        analytics.logProPromptDismissed(prompt.trigger, prompt.surface, method)
    }

    /** Logs the CTA tap. The prompt is not dismissed: the paywall opens over it and the user may come back. */
    fun onCtaTapped(prompt: ActiveProPrompt) = analytics.logProPromptCtaTap(prompt.trigger, prompt.surface)

    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        analytics.recordNonFatal(e)
        null
    }

    companion object {
        private const val SECOND_VEHICLE = 2

        /** Older earned prompt first. Only one can show per session (the policy's session cap). */
        private val REST_ORDER = listOf(
            ProPromptTrigger.SECOND_VEHICLE_ADDED,
            ProPromptTrigger.SERVICE_LOGGED,
            ProPromptTrigger.ENTRIES_MILESTONE,
        )

        private val REPORTS_ORDER = listOf(ProPromptTrigger.REPORTS_3_MONTHS)

        private const val REPORTS_MONTHS = 3L

        /** True when the first entry is at least 3 calendar months before [now]. Less than that does not qualify. */
        fun hasThreeMonthsOfEntries(firstEntryAt: Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
            if (firstEntryAt == null) return false
            val first = Instant.ofEpochMilli(firstEntryAt).atZone(zone).toLocalDate()
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return !first.plusMonths(REPORTS_MONTHS).isAfter(today)
        }

        fun paywallTrigger(trigger: ProPromptTrigger): PaywallTrigger = when (trigger) {
            ProPromptTrigger.ENTRIES_MILESTONE -> PaywallTrigger.AUTO_ENTRIES_MILESTONE
            ProPromptTrigger.SECOND_VEHICLE_ADDED -> PaywallTrigger.AUTO_SECOND_VEHICLE_ADDED
            ProPromptTrigger.SERVICE_LOGGED -> PaywallTrigger.AUTO_SERVICE_LOGGED
            ProPromptTrigger.REPORTS_3_MONTHS -> PaywallTrigger.AUTO_REPORTS_3_MONTHS
        }
    }
}
