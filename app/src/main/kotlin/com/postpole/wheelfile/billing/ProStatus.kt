package com.postpole.wheelfile.billing

import kotlinx.coroutines.flow.StateFlow

/** Read-only view of the Pro entitlement, so ViewModels do not need the whole billing stack. */
interface ProStatus {
    val isPro: StateFlow<Boolean>
}
