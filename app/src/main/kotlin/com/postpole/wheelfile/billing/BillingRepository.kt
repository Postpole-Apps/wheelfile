package com.postpole.wheelfile.billing

import android.app.Activity
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.analytics.AnalyticsParams
import com.postpole.wheelfile.analytics.NoOpAnalytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the Pro entitlement. [isPro] is cached in DataStore for instant startup but is
 * reconciled with Play on every connect, so refunds/revocations remove access.
 */
class BillingRepository(
    private val gateway: BillingGateway,
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val maxConnectAttempts: Int = 5,
    private val initialBackoffMs: Long = 1_000,
    private val maxAcknowledgeAttempts: Int = 3,
    private val acknowledgeBackoffMs: Long = 500,
    private val log: (String) -> Unit = { Log.w("BillingRepository", it) },
    private val analytics: Analytics = NoOpAnalytics,
) {
    private enum class Source { PURCHASE_FLOW, REFRESH }

    private val loadedProducts = java.util.concurrent.ConcurrentHashMap<String, ProductResult.Loaded>()

    /** What the user owns, cached in DataStore and reconciled with Play on every refresh. */
    val entitlements: StateFlow<Entitlements> = dataStore.data
        .map { Entitlements(ownsPro = it[IS_PRO] ?: false, ownsRemoveAds = it[OWNS_REMOVE_ADS] ?: false) }
        .stateIn(scope, SharingStarted.Eagerly, Entitlements())

    val isPro: StateFlow<Boolean> = entitlements.map { it.isPro }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** True when Remove ads or Pro is owned: every ad is hidden. */
    val adsHidden: StateFlow<Boolean> = entitlements.map { it.adsHidden }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _price = MutableStateFlow<String?>(null)

    /** Localized Pro price from ProductDetails, null until loaded. */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _removeAdsPrice = MutableStateFlow<String?>(null)

    /** Localized Remove ads price from ProductDetails, null until loaded. */
    val removeAdsPrice: StateFlow<String?> = _removeAdsPrice.asStateFlow()

    private val _pending = MutableStateFlow(false)

    /** True while a purchase is PENDING (not yet paid); Pro is not granted. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private val _productUnavailable = MutableStateFlow(false)

    /** True when Play answered but the Pro product/offer isn't available (so don't show "loading"). */
    val productUnavailable: StateFlow<Boolean> = _productUnavailable.asStateFlow()

    private val _messages = Channel<BillingMessage>(Channel.BUFFERED)

    /** One-shot messages for the UI (purchase errors, launch failures). */
    val messages: Flow<BillingMessage> = _messages.receiveAsFlow()

    private val refreshLock = Mutex()
    private var started = false

    /** Call once at app start. Connects, loads product, restores purchases. */
    fun start() {
        if (started) return
        started = true
        scope.launch { gateway.updates.collect(::onUpdate) }
        scope.launch { gateway.disconnects.collect { refresh() } }
        scope.launch { refresh() }
    }

    /** Reconnects if needed, then re-queries product details and owned purchases. */
    suspend fun refresh(): Boolean = refreshLock.withLock {
        if (!connectWithRetry()) return false
        for (productId in ProductIds.ALL) {
            val priceFlow = if (productId == ProductIds.PRO) _price else _removeAdsPrice
            when (val product = gateway.queryProduct(productId)) {
                is ProductResult.Loaded -> {
                    loadedProducts[productId] = product
                    priceFlow.value = product.formattedPrice
                    if (productId == ProductIds.PRO) _productUnavailable.value = false
                }
                ProductResult.Unavailable -> {
                    loadedProducts.remove(productId)
                    priceFlow.value = null
                    if (productId == ProductIds.PRO) _productUnavailable.value = true
                }
                ProductResult.Failed ->
                    analytics.recordNonFatal(BillingException("Product details query failed for $productId"))
            }
        }
        val owned = gateway.queryPurchases()
        if (owned == null) {
            analytics.recordNonFatal(BillingException("Purchases query failed"))
            return false
        }
        applyPurchases(owned, revokeIfAbsent = true, source = Source.REFRESH)
        true
    }

    /** User-initiated restore. Queries both products; always returns a result the UI can show. */
    suspend fun restore(): RestoreResult {
        val ok = try {
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            analytics.recordNonFatal(e)
            false
        }
        return when {
            !ok -> RestoreResult.FAILED
            readEntitlements().ownsAnything -> RestoreResult.RESTORED
            else -> RestoreResult.NOTHING_FOUND
        }
    }

    /** Call when the app comes to the foreground to pick up pending completions and refunds. */
    fun onForeground() {
        if (started) scope.launch { refresh() }
    }

    fun launchPurchase(activity: Activity, productId: String = ProductIds.PRO): Boolean {
        val launched = gateway.launchPurchase(activity, productId)
        if (launched) {
            lastLaunchedProduct = productId
            if (productId == ProductIds.REMOVE_ADS) {
                analytics.logEvent(AnalyticsEvents.REMOVE_ADS_PURCHASE_START)
            } else {
                analytics.logEvent(AnalyticsEvents.PURCHASE_STARTED, mapOf(AnalyticsParams.ITEM_ID to productId))
            }
        } else {
            analytics.recordNonFatal(BillingException("Could not launch purchase flow"))
            if (productId == ProductIds.REMOVE_ADS) analytics.logEvent(AnalyticsEvents.REMOVE_ADS_PURCHASE_FAIL)
            _messages.trySend(BillingMessage.LAUNCH_FAILED)
        }
        return launched
    }

    /** The product whose purchase sheet was opened last, so a failed result can be attributed to it. */
    @Volatile private var lastLaunchedProduct: String? = null

    private suspend fun onUpdate(update: PurchaseUpdate) {
        when (update) {
            is PurchaseUpdate.Purchases ->
                applyPurchases(update.purchases, revokeIfAbsent = false, source = Source.PURCHASE_FLOW)
            PurchaseUpdate.AlreadyOwned -> refresh()
            PurchaseUpdate.Canceled -> Unit
            is PurchaseUpdate.Error -> {
                log("Purchase failed with code ${update.code}")
                analytics.recordNonFatal(BillingException("Purchase failed with code ${update.code}"))
                if (lastLaunchedProduct == ProductIds.REMOVE_ADS) {
                    analytics.logEvent(AnalyticsEvents.REMOVE_ADS_PURCHASE_FAIL)
                }
                _messages.trySend(BillingMessage.PURCHASE_ERROR)
            }
        }
    }

    private suspend fun applyPurchases(purchases: List<OwnedPurchase>, revokeIfAbsent: Boolean, source: Source) {
        val purchased = purchases.filter { it.status == PurchaseStatus.PURCHASED }
        _pending.value = purchased.isEmpty() && purchases.any { it.status == PurchaseStatus.PENDING }
        val before = readEntitlements()
        val owned = purchased.map { it.productId }.toSet()
        // Each product is granted or (on a full refresh) revoked on its own, so a refund of one leaves the other.
        val ownsPro = ProductIds.PRO in owned || (before.ownsPro && !revokeIfAbsent)
        val ownsRemoveAds = ProductIds.REMOVE_ADS in owned || (before.ownsRemoveAds && !revokeIfAbsent)
        setEntitlements(Entitlements(ownsPro = ownsPro, ownsRemoveAds = ownsRemoveAds))
        // One event per product that was not owned before (rather than one per refresh).
        if (ownsPro && !before.ownsPro) logPurchaseEvent(source, ProductIds.PRO)
        if (ownsRemoveAds && !before.ownsRemoveAds) logPurchaseEvent(source, ProductIds.REMOVE_ADS)
        purchased.filter { !it.acknowledged }.map { it.token }.distinct().forEach { acknowledgeWithRetry(it) }
    }

    /** `purchase` (with value + currency) for a fresh buy, `purchase_restored` when found on refresh/restore. */
    private fun logPurchaseEvent(source: Source, productId: String) {
        when (source) {
            Source.PURCHASE_FLOW -> {
                val product = loadedProducts[productId]
                val params = buildMap<String, Any> {
                    put(AnalyticsParams.ITEM_ID, productId)
                    if (product != null && product.priceMicros > 0 && product.currencyCode.isNotEmpty()) {
                        put(AnalyticsParams.VALUE, product.priceMicros / 1_000_000.0)
                        put(AnalyticsParams.CURRENCY, product.currencyCode)
                    }
                }
                analytics.logEvent(AnalyticsEvents.PURCHASE, params)
                // No success event on restore: only a fresh buy reaches this branch.
                if (productId == ProductIds.REMOVE_ADS) analytics.logEvent(AnalyticsEvents.REMOVE_ADS_PURCHASE_SUCCESS)
            }
            Source.REFRESH -> analytics.logEvent(
                AnalyticsEvents.PURCHASE_RESTORED,
                mapOf(AnalyticsParams.ITEM_ID to productId),
            )
        }
    }

    /** Play refunds unacknowledged purchases after 3 days, so retry now; the next refresh retries again. */
    private suspend fun acknowledgeWithRetry(token: String) {
        var backoff = acknowledgeBackoffMs
        repeat(maxAcknowledgeAttempts) { attempt ->
            if (gateway.acknowledge(token)) return
            log("Acknowledge failed (attempt ${attempt + 1}/$maxAcknowledgeAttempts)")
            if (attempt < maxAcknowledgeAttempts - 1) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        analytics.recordNonFatal(BillingException("Acknowledge failed after $maxAcknowledgeAttempts attempts"))
    }

    private suspend fun connectWithRetry(): Boolean {
        var backoff = initialBackoffMs
        repeat(maxConnectAttempts) { attempt ->
            if (gateway.connect()) return true
            if (attempt < maxConnectAttempts - 1) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        return false
    }

    private suspend fun readEntitlements(): Entitlements = dataStore.data.first().let {
        Entitlements(ownsPro = it[IS_PRO] ?: false, ownsRemoveAds = it[OWNS_REMOVE_ADS] ?: false)
    }

    private suspend fun setEntitlements(value: Entitlements) {
        dataStore.edit {
            it[IS_PRO] = value.ownsPro
            it[OWNS_REMOVE_ADS] = value.ownsRemoveAds
        }
    }

    private companion object {
        val IS_PRO = EntitlementKeys.IS_PRO
        val OWNS_REMOVE_ADS = EntitlementKeys.OWNS_REMOVE_ADS
        const val MAX_BACKOFF_MS = 15_000L
    }
}

/**
 * DataStore keys of the cached purchases. They describe what the Google account owns, not user data, so "Delete all
 * data" keeps them (the next Play refresh would only restore them) and a device-transfer copy of them is never trusted:
 * [BillingRepository.start] reconciles them with Play on every launch and clears Pro when Play has no purchase.
 */
object EntitlementKeys {
    val IS_PRO = booleanPreferencesKey("is_pro")
    val OWNS_REMOVE_ADS = booleanPreferencesKey("owns_remove_ads")
    val ALL: Set<Preferences.Key<*>> = setOf(IS_PRO, OWNS_REMOVE_ADS)
}
