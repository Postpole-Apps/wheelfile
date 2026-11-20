package com.postpole.wheelfile.billing

import android.app.Activity
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.postpole.wheelfile.BuildConfig
import com.postpole.wheelfile.analytics.Analytics
import com.postpole.wheelfile.analytics.AnalyticsEvents
import com.postpole.wheelfile.analytics.AnalyticsParams
import com.postpole.wheelfile.analytics.NoOpAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the Pro and Remove ads entitlements. They are cached in DataStore for instant startup but are
 * reconciled with Play on every connect, so refunds/revocations remove access (one product at a time).
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
    private val proProductId: String = BuildConfig.PRO_PRODUCT_ID,
    private val removeAdsProductId: String = BuildConfig.REMOVE_ADS_PRODUCT_ID,
) {
    private enum class Source { PURCHASE_FLOW, REFRESH }

    @Volatile
    private var loadedProducts: Map<String, ProductResult.Loaded> = emptyMap()

    /** What the user owns right now (cached, then reconciled with Play). */
    val entitlements: StateFlow<Entitlements> = dataStore.data
        .map { Entitlements(ownsRemoveAds = it[OWNS_REMOVE_ADS] ?: false, ownsPro = it[IS_PRO] ?: false) }
        .stateIn(scope, SharingStarted.Eagerly, Entitlements.NONE)

    val isPro: StateFlow<Boolean> = entitlements
        .map { it.isPro }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _price = MutableStateFlow<String?>(null)

    /** Localized Pro price from ProductDetails, null until loaded. */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _removeAdsPrice = MutableStateFlow<String?>(null)

    /** Localized Remove ads price from ProductDetails, null until loaded. */
    val removeAdsPrice: StateFlow<String?> = _removeAdsPrice.asStateFlow()

    private val _pending = MutableStateFlow(false)

    /** True while a purchase is PENDING (not yet paid); nothing is granted until it completes. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private val _productUnavailable = MutableStateFlow(false)

    /** True when Play answered but the Pro product/offer isn't available (so don't show "loading"). */
    val productUnavailable: StateFlow<Boolean> = _productUnavailable.asStateFlow()

    private val _messages = Channel<BillingMessage>(Channel.BUFFERED)

    /** One-shot messages for the UI (purchase errors, launch failures). */
    val messages: Flow<BillingMessage> = _messages.receiveAsFlow()

    private val refreshLock = Mutex()
    private var started = false

    /** Call once at app start. Connects, loads products, restores purchases. */
    fun start() {
        if (started) return
        started = true
        scope.launch { gateway.updates.collect(::onUpdate) }
        scope.launch { gateway.disconnects.collect { refresh() } }
        scope.launch { refresh() }
    }

    /** Reconnects if needed, then re-queries product details and owned purchases (both products). */
    suspend fun refresh(): Boolean = refreshLock.withLock {
        if (!connectWithRetry()) return false
        val products = gateway.queryProducts()
        loadedProducts = products.mapNotNull { (id, result) -> (result as? ProductResult.Loaded)?.let { id to it } }
            .toMap()
        when (products[proProductId]) {
            is ProductResult.Loaded -> {
                _price.value = loadedProducts[proProductId]?.formattedPrice
                _productUnavailable.value = false
            }
            ProductResult.Unavailable -> {
                _price.value = null
                _productUnavailable.value = true
            }
            ProductResult.Failed, null -> analytics.recordNonFatal(BillingException("Product details query failed"))
        }
        _removeAdsPrice.value = loadedProducts[removeAdsProductId]?.formattedPrice
        val owned = gateway.queryPurchases()
        if (owned == null) {
            analytics.recordNonFatal(BillingException("Purchases query failed"))
            return false
        }
        applyPurchases(owned, revokeIfAbsent = true, source = Source.REFRESH)
        true
    }

    /** User-initiated restore: queries both products and reports what happened. */
    suspend fun restore(): RestoreResult {
        if (!refresh()) return RestoreResult.FAILED
        val owned = readCached()
        return if (owned.ownsPro || owned.ownsRemoveAds) RestoreResult.RESTORED else RestoreResult.NONE_FOUND
    }

    /** Call when the app comes to the foreground to pick up pending completions and refunds. */
    fun onForeground() {
        if (started) scope.launch { refresh() }
    }

    fun launchPurchase(activity: Activity, productId: String = proProductId): Boolean {
        val launched = gateway.launchPurchase(activity, productId)
        if (launched) {
            analytics.logEvent(AnalyticsEvents.PURCHASE_STARTED)
        } else {
            analytics.recordNonFatal(BillingException("Could not launch purchase flow"))
            _messages.trySend(BillingMessage.LAUNCH_FAILED)
        }
        return launched
    }

    private suspend fun onUpdate(update: PurchaseUpdate) {
        when (update) {
            is PurchaseUpdate.Purchases ->
                applyPurchases(update.purchases, revokeIfAbsent = false, source = Source.PURCHASE_FLOW)
            PurchaseUpdate.AlreadyOwned -> refresh()
            PurchaseUpdate.Canceled -> Unit
            is PurchaseUpdate.Error -> {
                log("Purchase failed with code ${update.code}")
                analytics.recordNonFatal(BillingException("Purchase failed with code ${update.code}"))
                _messages.trySend(BillingMessage.PURCHASE_ERROR)
            }
        }
    }

    private suspend fun applyPurchases(purchases: List<OwnedPurchase>, revokeIfAbsent: Boolean, source: Source) {
        val purchased = purchases.filter { it.status == PurchaseStatus.PURCHASED }
        val ownedIds = purchased.flatMap { it.productIds }.toSet()
        val pendingIds = purchases.filter { it.status == PurchaseStatus.PENDING }.flatMap { it.productIds }.toSet()
        _pending.value = (pendingIds - ownedIds).isNotEmpty()

        val before = readCached()
        val nowPro = proProductId in ownedIds
        val nowRemoveAds = removeAdsProductId in ownedIds
        val after = if (revokeIfAbsent) {
            // A full Play query is authoritative: a refund of one product leaves the other untouched.
            Entitlements(ownsRemoveAds = nowRemoveAds, ownsPro = nowPro)
        } else {
            Entitlements(ownsRemoveAds = before.ownsRemoveAds || nowRemoveAds, ownsPro = before.ownsPro || nowPro)
        }
        if (after != before) {
            dataStore.edit {
                it[IS_PRO] = after.ownsPro
                it[OWNS_REMOVE_ADS] = after.ownsRemoveAds
            }
        }
        val gained = buildList {
            if (after.ownsPro && !before.ownsPro) add(proProductId)
            if (after.ownsRemoveAds && !before.ownsRemoveAds) add(removeAdsProductId)
        }
        if (gained.isNotEmpty()) logPurchaseEvent(source, gained.first())
        purchased.filter { !it.acknowledged }.forEach { acknowledgeWithRetry(it.token) }
    }

    private suspend fun readCached(): Entitlements {
        val prefs = dataStore.data.first()
        return Entitlements(ownsRemoveAds = prefs[OWNS_REMOVE_ADS] ?: false, ownsPro = prefs[IS_PRO] ?: false)
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
            }
            Source.REFRESH -> analytics.logEvent(AnalyticsEvents.PURCHASE_RESTORED)
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

    private companion object {
        val IS_PRO = booleanPreferencesKey("is_pro")
        val OWNS_REMOVE_ADS = booleanPreferencesKey("owns_remove_ads")
        const val MAX_BACKOFF_MS = 15_000L
    }
}
