package com.postpole.template.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

enum class PurchaseStatus { PURCHASED, PENDING, UNSPECIFIED }

data class OwnedPurchase(
    val token: String,
    val status: PurchaseStatus,
    val acknowledged: Boolean,
)

/** Result of loading the Pro product from Play. */
sealed interface ProductResult {
    data class Loaded(
        val formattedPrice: String,
        /** Price in micro-units (1,000,000 = one unit of currency), 0 if unknown. */
        val priceMicros: Long = 0,
        /** ISO 4217 code, empty if unknown. */
        val currencyCode: String = "",
    ) : ProductResult

    /** Play answered but the product (or its purchasable offer) is not available. */
    data object Unavailable : ProductResult

    /** The query itself failed (network, service error); worth retrying later. */
    data object Failed : ProductResult
}

/** Wraps a billing failure so Crashlytics groups them as one non-fatal type. */
class BillingException(message: String) : Exception(message)

/** One-shot, user-visible billing problems. */
enum class BillingMessage { PURCHASE_ERROR, LAUNCH_FAILED }

/** Result of the purchase flow as reported by Play. */
sealed interface PurchaseUpdate {
    data class Purchases(val purchases: List<OwnedPurchase>) : PurchaseUpdate
    data object AlreadyOwned : PurchaseUpdate
    data object Canceled : PurchaseUpdate
    data class Error(val code: Int) : PurchaseUpdate
}

/** Thin seam over Play's BillingClient so [BillingRepository] can be unit tested with a fake. */
interface BillingGateway {
    val updates: Flow<PurchaseUpdate>

    /** Emits whenever the billing service connection drops. */
    val disconnects: Flow<Unit>

    /** Returns true when connected (no-op if already connected). */
    suspend fun connect(): Boolean

    suspend fun queryProduct(): ProductResult

    /** Purchases of the Pro product currently owned, or null if the query failed. */
    suspend fun queryPurchases(): List<OwnedPurchase>?

    /** Launches the Play purchase sheet. Returns false if it could not be launched. */
    fun launchPurchase(activity: Activity): Boolean

    suspend fun acknowledge(token: String): Boolean
}
