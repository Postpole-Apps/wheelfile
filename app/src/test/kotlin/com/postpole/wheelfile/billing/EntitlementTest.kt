package com.postpole.wheelfile.billing

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.postpole.wheelfile.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class TwoProductGateway : BillingGateway {
    override val updates: Flow<PurchaseUpdate> = MutableSharedFlow(extraBufferCapacity = 16)
    override val disconnects: Flow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)
    var owned: List<OwnedPurchase>? = emptyList()

    override suspend fun connect() = true
    override suspend fun queryProducts(): Map<String, ProductResult> = mapOf(
        BuildConfig.PRO_PRODUCT_ID to ProductResult.Loaded("$9.99"),
        BuildConfig.REMOVE_ADS_PRODUCT_ID to ProductResult.Loaded("$3.99"),
    )
    override suspend fun queryPurchases() = owned
    override fun launchPurchase(activity: Activity, productId: String) = true
    override suspend fun acknowledge(token: String) = true
}

@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementTest {
    @get:Rule val tmp = TemporaryFolder()

    private val gateway = TwoProductGateway()
    private lateinit var dataStore: DataStore<Preferences>

    private val proPurchase = OwnedPurchase(
        "pro-token", PurchaseStatus.PURCHASED, acknowledged = true, productIds = listOf(BuildConfig.PRO_PRODUCT_ID),
    )
    private val removeAdsPurchase = OwnedPurchase(
        "ads-token", PurchaseStatus.PURCHASED, acknowledged = true,
        productIds = listOf(BuildConfig.REMOVE_ADS_PRODUCT_ID),
    )

    private fun TestScope.repo(): BillingRepository {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "e.preferences_pb") }
        return BillingRepository(gateway, dataStore, scope, initialBackoffMs = 10, acknowledgeBackoffMs = 10, log = {})
    }

    @Test fun productIds_matchTheSpec() {
        assertEquals("lifetime_full_access", BuildConfig.PRO_PRODUCT_ID)
        assertEquals("remove_ads", BuildConfig.REMOVE_ADS_PRODUCT_ID)
    }

    @Test fun nothingOwned_showsAdsAndIsNotPro() {
        assertFalse(Entitlements.NONE.adsHidden)
        assertFalse(Entitlements.NONE.isPro)
    }

    @Test fun removeAdsOnly_hidesAdsButIsNotPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(removeAdsPurchase)
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.entitlements.value.adsHidden)
        assertFalse(repo.entitlements.value.isPro)
        assertFalse(repo.isPro.value)
    }

    @Test fun proOnly_hidesAdsAndIsPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(proPurchase)
        repo.start()
        advanceUntilIdle()
        assertTrue(repo.entitlements.value.adsHidden)
        assertTrue(repo.entitlements.value.isPro)
    }

    @Test fun both_hideAdsAndIsPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(proPurchase, removeAdsPurchase)
        repo.start()
        advanceUntilIdle()
        assertEquals(Entitlements(ownsRemoveAds = true, ownsPro = true), repo.entitlements.value)
    }

    @Test fun refundOfRemoveAds_leavesPro() = runTest {
        val repo = repo()
        gateway.owned = listOf(proPurchase, removeAdsPurchase)
        repo.start()
        advanceUntilIdle()
        gateway.owned = listOf(proPurchase) // remove_ads refunded
        repo.restore()
        advanceUntilIdle()
        assertEquals(Entitlements(ownsRemoveAds = false, ownsPro = true), repo.entitlements.value)
        assertTrue(repo.entitlements.value.adsHidden)
    }

    @Test fun refundOfPro_leavesRemoveAds() = runTest {
        val repo = repo()
        gateway.owned = listOf(proPurchase, removeAdsPurchase)
        repo.start()
        advanceUntilIdle()
        gateway.owned = listOf(removeAdsPurchase) // lifetime_full_access refunded
        repo.restore()
        advanceUntilIdle()
        assertEquals(Entitlements(ownsRemoveAds = true, ownsPro = false), repo.entitlements.value)
        assertTrue(repo.entitlements.value.adsHidden)
        assertFalse(repo.entitlements.value.isPro)
    }

    @Test fun refundOfTheOnlyProduct_showsAdsAgain() = runTest {
        val repo = repo()
        gateway.owned = listOf(removeAdsPurchase)
        repo.start()
        advanceUntilIdle()
        gateway.owned = emptyList()
        repo.restore()
        advanceUntilIdle()
        assertFalse(repo.entitlements.value.adsHidden)
    }

    @Test fun restore_reportsWhatHappened() = runTest {
        val repo = repo()
        repo.start()
        advanceUntilIdle()
        assertEquals(RestoreResult.NONE_FOUND, repo.restore())

        gateway.owned = listOf(removeAdsPurchase)
        assertEquals(RestoreResult.RESTORED, repo.restore())

        gateway.owned = null // Play query failed
        assertEquals(RestoreResult.FAILED, repo.restore())
    }
}
