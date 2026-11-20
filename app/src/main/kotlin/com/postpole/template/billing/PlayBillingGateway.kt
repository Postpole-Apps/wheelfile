package com.postpole.template.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.queryPurchasesAsync
import com.postpole.template.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PlayBillingGateway(
    context: Context,
    private val productId: String = BuildConfig.PRO_PRODUCT_ID,
) : BillingGateway {

    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 16)
    override val updates: Flow<PurchaseUpdate> = _updates.asSharedFlow()

    private val _disconnects = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val disconnects: Flow<Unit> = _disconnects.asSharedFlow()

    private class LaunchableProduct(val details: ProductDetails, val offerToken: String)

    @Volatile
    private var launchable: LaunchableProduct? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(
            PurchasesUpdatedListener { result, purchases ->
                _updates.tryEmit(
                    when (result.responseCode) {
                        BillingClient.BillingResponseCode.OK ->
                            PurchaseUpdate.Purchases(
                                purchases.orEmpty()
                                    .filter { productId in it.products }
                                    .map { it.toOwned() },
                            )
                        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseUpdate.AlreadyOwned
                        BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Canceled
                        else -> PurchaseUpdate.Error(result.responseCode)
                    },
                )
            },
        )
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override suspend fun connect(): Boolean {
        if (client.isReady) return true
        val done = CompletableDeferred<Boolean>()
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    done.complete(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    done.complete(false)
                    _disconnects.tryEmit(Unit)
                }
            },
        )
        return done.await()
    }

    override suspend fun queryProduct(): ProductResult {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        // The ktx wrapper drops unfetchedProductList, so use the callback API to see status codes.
        val done = CompletableDeferred<Pair<BillingResult, QueryProductDetailsResult>>()
        client.queryProductDetailsAsync(params) { billingResult, queryResult ->
            done.complete(billingResult to queryResult)
        }
        val (billingResult, result) = done.await()
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "queryProductDetails failed: ${billingResult.responseCode}")
            return ProductResult.Failed
        }
        result.unfetchedProductList.forEach {
            Log.w(TAG, "Product ${it.productId} not fetched, status code ${it.statusCode}")
        }
        val details = result.productDetailsList.firstOrNull()
        val offers = details?.oneTimePurchaseOfferDetailsList.orEmpty()
            .mapNotNull { o -> o.offerToken?.takeIf { it.isNotEmpty() }?.let { o to it } }
        // The legacy single-offer getter returns the legacy-compatible purchase option; prefer it.
        @Suppress("DEPRECATION")
        val legacyToken = details?.oneTimePurchaseOfferDetails?.offerToken
        val chosen = offers.firstOrNull { it.second == legacyToken } ?: offers.firstOrNull()
        if (details == null || chosen == null) {
            launchable = null
            return ProductResult.Unavailable
        }
        launchable = LaunchableProduct(details, chosen.second)
        return ProductResult.Loaded(
            formattedPrice = chosen.first.formattedPrice,
            priceMicros = chosen.first.priceAmountMicros,
            currencyCode = chosen.first.priceCurrencyCode,
        )
    }

    override suspend fun queryPurchases(): List<OwnedPurchase>? {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        return result.purchasesList
            .filter { productId in it.products }
            .map { it.toOwned() }
    }

    override fun launchPurchase(activity: Activity): Boolean {
        val product = launchable ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product.details)
                        .setOfferToken(product.offerToken)
                        .build(),
                ),
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode ==
            BillingClient.BillingResponseCode.OK
    }

    override suspend fun acknowledge(token: String): Boolean {
        val result = client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build(),
        )
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private companion object {
        const val TAG = "PlayBillingGateway"
    }

    private fun Purchase.toOwned() = OwnedPurchase(
        token = purchaseToken,
        status = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseStatus.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseStatus.PENDING
            else -> PurchaseStatus.UNSPECIFIED
        },
        acknowledged = isAcknowledged,
    )
}
