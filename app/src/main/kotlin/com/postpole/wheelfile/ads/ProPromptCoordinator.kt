package com.postpole.wheelfile.ads

import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.logProPromptCtaTap
import com.postpole.wheelfile.analytics.logProPromptDismissed
import com.postpole.wheelfile.analytics.logProPromptShown
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.ui.pro.PaywallTrigger
import kotlinx.coroutines.CancellationException
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
) {
    @Inject
    constructor(
        policy: ProPromptPolicy,
        configSource: AdsConfigSource,
        billing: BillingRepository,
        repo: VehicleRepository,
        analytics: Analytics,
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
    )

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
        for (trigger in REST_ORDER) {
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
        private val REST_ORDER = listOf(ProPromptTrigger.SECOND_VEHICLE_ADDED, ProPromptTrigger.ENTRIES_MILESTONE)

        fun paywallTrigger(trigger: ProPromptTrigger): PaywallTrigger = when (trigger) {
            ProPromptTrigger.ENTRIES_MILESTONE -> PaywallTrigger.AUTO_ENTRIES_MILESTONE
            ProPromptTrigger.SECOND_VEHICLE_ADDED -> PaywallTrigger.AUTO_SECOND_VEHICLE_ADDED
            ProPromptTrigger.SERVICE_LOGGED -> PaywallTrigger.AUTO_SERVICE_LOGGED
            ProPromptTrigger.REPORTS_3_MONTHS -> PaywallTrigger.AUTO_REPORTS_3_MONTHS
        }
    }
}
