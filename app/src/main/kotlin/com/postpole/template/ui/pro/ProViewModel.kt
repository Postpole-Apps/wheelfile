package com.postpole.template.ui.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.template.billing.BillingRepository
import com.postpole.template.billing.BillingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProViewModel @Inject constructor(
    private val billing: BillingRepository,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = billing.isPro
    val price: StateFlow<String?> = billing.price
    val pending: StateFlow<Boolean> = billing.pending
    val productUnavailable: StateFlow<Boolean> = billing.productUnavailable
    val messages: Flow<BillingMessage> = billing.messages

    fun buy(activity: Activity) {
        billing.launchPurchase(activity)
    }

    fun restore() {
        viewModelScope.launch { billing.restore() }
    }
}
