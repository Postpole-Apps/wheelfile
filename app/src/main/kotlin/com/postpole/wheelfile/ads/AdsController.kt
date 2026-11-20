package com.postpole.wheelfile.ads

import com.postpole.wheelfile.billing.BillingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** Single place the UI asks "may I request an ad?". Combines consent, Remote Config and entitlements. */
@Singleton
class AdsController @Inject constructor(
    private val consent: ConsentManager,
    private val configSource: AdsConfigSource,
    private val billing: BillingRepository,
) {
    /** True only when consent, Remote Config and entitlements all allow banner requests. */
    val bannersAllowed: Flow<Boolean> = combine(
        consent.canRequestAds,
        configSource.config,
        billing.adsHidden,
        AdSlotState::bannersAllowed,
    ).distinctUntilChanged()
}
