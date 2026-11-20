package com.postpole.wheelfile.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementTest {
    @Test fun productIds() {
        assertEquals("lifetime_full_access", ProductIds.PRO)
        assertEquals("remove_ads", ProductIds.REMOVE_ADS)
    }

    @Test fun nothingOwned() {
        val e = Entitlements.from(emptySet())
        assertFalse(e.isPro)
        assertFalse(e.adsHidden)
    }

    @Test fun removeAds_hidesAdsButIsNotPro() {
        val e = Entitlements.from(setOf(ProductIds.REMOVE_ADS))
        assertTrue(e.adsHidden)
        assertFalse(e.isPro)
    }

    @Test fun pro_hidesAdsAndIsPro() {
        val e = Entitlements.from(setOf(ProductIds.PRO))
        assertTrue(e.adsHidden)
        assertTrue(e.isPro)
    }

    @Test fun both() {
        val e = Entitlements.from(setOf(ProductIds.PRO, ProductIds.REMOVE_ADS))
        assertTrue(e.adsHidden)
        assertTrue(e.isPro)
    }

    @Test fun refundOfOne_leavesTheOther() {
        val afterRemoveAdsRefund = Entitlements.from(setOf(ProductIds.PRO))
        assertTrue(afterRemoveAdsRefund.isPro)
        assertTrue(afterRemoveAdsRefund.adsHidden)

        val afterProRefund = Entitlements.from(setOf(ProductIds.REMOVE_ADS))
        assertFalse(afterProRefund.isPro)
        assertTrue(afterProRefund.adsHidden)
    }

    @Test fun unknownProductsAreIgnored() {
        assertFalse(Entitlements.from(setOf("something_else")).adsHidden)
    }
}
