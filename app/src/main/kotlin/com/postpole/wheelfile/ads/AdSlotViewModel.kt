package com.postpole.wheelfile.ads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.billing.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Whether a banner may be requested right now. Starts false so nothing is requested before it is known. */
@HiltViewModel
class AdSlotViewModel @Inject constructor(
    billing: BillingRepository,
    consent: ConsentManager,
    remoteConfig: RemoteAdsConfig,
) : ViewModel() {
    val eligible: StateFlow<Boolean> = combine(
        billing.entitlements,
        consent.adsReady,
        remoteConfig.config,
    ) { entitlements, adsReady, config ->
        AdSlotState.eligible(entitlements.adsHidden, adsReady, config)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
