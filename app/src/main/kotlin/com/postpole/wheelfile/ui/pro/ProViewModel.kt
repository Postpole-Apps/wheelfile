package com.postpole.wheelfile.ui.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.analytics.AnalyticsParams
import com.postpole.wheelfile.billing.BillingMessage
import com.postpole.wheelfile.billing.BillingRepository
import com.postpole.wheelfile.billing.ProductIds
import com.postpole.wheelfile.billing.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val analytics: Analytics,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = billing.isPro
    val adsHidden: StateFlow<Boolean> = billing.adsHidden

    /** Play's formatted prices (`ProductDetails`); null until loaded. Never a literal from code. */
    val price: StateFlow<String?> = billing.price
    val removeAdsPrice: StateFlow<String?> = billing.removeAdsPrice
    val pending: StateFlow<Boolean> = billing.pending
    val productUnavailable: StateFlow<Boolean> = billing.productUnavailable
    val messages: Flow<BillingMessage> = billing.messages

    private val _restoreResults = Channel<RestoreResult>(Channel.BUFFERED)
    val restoreResults: Flow<RestoreResult> = _restoreResults.receiveAsFlow()

    private var viewLogged = false

    /** Logs the paywall view once per screen instance. Params are the trigger enum only. */
    fun onViewed(trigger: PaywallTrigger) {
        if (viewLogged) return
        viewLogged = true
        val params = mapOf<String, Any>(AnalyticsParams.TRIGGER to trigger.analyticsValue)
        analytics.logEvent(AnalyticsEvents.PRO_SCREEN_VIEW, params)
        analytics.logEvent(AnalyticsEvents.REMOVE_ADS_PAYWALL_VIEW, params)
    }

    fun buyPro(activity: Activity) {
        billing.launchPurchase(activity, ProductIds.PRO)
    }

    fun buyRemoveAds(activity: Activity) {
        billing.launchPurchase(activity, ProductIds.REMOVE_ADS)
    }

    fun restore() {
        viewModelScope.launch { _restoreResults.trySend(billing.restore()) }
    }
}
