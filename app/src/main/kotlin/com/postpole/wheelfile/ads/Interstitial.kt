package com.postpole.wheelfile.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.postpole.wheelfile.billing.BillingRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Seam over the Mobile Ads interstitial so the flow can be unit tested. Main thread only. */
interface InterstitialGateway {
    /** True when an ad is loaded and can be shown right now. */
    val isReady: Boolean

    /** Starts loading unless an ad is already loaded or loading. */
    fun preload()

    /** Shows the loaded ad over [activity]. Returns false (and shows nothing) if none is loaded. */
    fun show(activity: Activity): Boolean
}

/** `wf_interstitial_after_review` backed by the AdMob SDK. The unit ID comes from this build's `BuildConfig`. */
class GoogleInterstitialGateway(
    private val context: Context,
    private val adUnitId: String = AdUnits.current.interstitialAfterReview,
) : InterstitialGateway {
    private var ad: InterstitialAd? = null
    private var loading = false

    override val isReady: Boolean get() = ad != null

    override fun preload() {
        if (ad != null || loading) return
        loading = true
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    loading = false
                    ad = loaded
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    ad = null
                }
            },
        )
    }

    override fun show(activity: Activity): Boolean {
        val shown = ad ?: return false
        ad = null
        shown.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdFailedToShowFullScreenContent(error: AdError) = Unit
        }
        shown.show(activity)
        return true
    }
}

/**
 * Decides, once the user is back on the Garage, whether `wf_interstitial_after_review` shows (spec §5.2,
 * DECISIONS.md item 9). Every rule lives in [AdsPolicy.evaluate]; this class adds the trigger (back from Vehicle
 * detail after viewing History or Reports) and never blocks navigation: if no ad is loaded yet, nothing is shown.
 *
 * It is never called from a save, a form, or an import/export flow, so those can't show an interstitial.
 */
@Singleton
class InterstitialCoordinator internal constructor(
    private val policy: AdsPolicy,
    private val gateway: InterstitialGateway,
    private val config: () -> AdsConfig,
    private val adsHidden: () -> Boolean,
    private val canRequestAds: () -> Boolean,
) {
    @Inject
    constructor(
        policy: AdsPolicy,
        consent: ConsentManager,
        configSource: AdsConfigSource,
        billing: BillingRepository,
        gateway: InterstitialGateway,
    ) : this(
        policy,
        gateway,
        { configSource.config.value },
        { billing.adsHidden.value },
        { consent.canRequestAds.value },
    )

    private fun decision(): InterstitialDecision = policy.evaluate(config(), adsHidden(), canRequestAds())

    /** Called when History or Reports is viewed: starts loading only if an interstitial could be allowed. */
    fun preloadIfEligible() {
        if (decision() == InterstitialDecision.ALLOWED) gateway.preload()
    }

    /**
     * Called right after the user left Vehicle detail for the Garage. Returns true if an interstitial was shown.
     * The impression is recorded before returning, so rapid repeated navigation can't beat the caps.
     */
    fun onBackToGarage(activity: Activity): Boolean {
        if (!policy.reviewedBeforeGarage) return false
        policy.clearReviewed()
        if (decision() != InterstitialDecision.ALLOWED) return false
        if (!gateway.show(activity)) {
            gateway.preload()
            return false
        }
        policy.onInterstitialShown()
        return true
    }
}
