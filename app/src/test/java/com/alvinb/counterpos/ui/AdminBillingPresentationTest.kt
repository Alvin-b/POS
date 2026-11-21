package com.alvinb.counterpos.ui

import com.alvinb.counterpos.data.remote.AdminBusiness
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminBillingPresentationTest {
    @Test
    fun paidRequiresAnActiveTermAndARecordedReceipt() {
        assertEquals(
            AdminBillingState.PAID,
            business(status = "active", receipt = "QAB123XYZ").billingState()
        )
        assertEquals(
            AdminBillingState.ACTIVE_UNVERIFIED,
            business(status = "active").billingState()
        )
    }

    @Test
    fun expiredAndUnknownNeverFallThroughToPaid() {
        assertEquals(AdminBillingState.EXPIRED, business(status = "expired").billingState())
        assertEquals(AdminBillingState.UNKNOWN, business(status = "unknown").billingState())
        assertEquals("Expired on 2026-10-01", business(status = "expired", paidUntil = "2026-10-01T00:00:00Z").billingExpiryLabel())
    }

    @Test
    fun suspensionIsIndependentFromSubscriptionState() {
        assertEquals(AdminBillingState.TRIAL, business(status = "trial", lifecycle = "suspended").billingState())
        assertEquals(AdminBillingState.LOCKED, business(status = "active", locked = true, receipt = "QAB123XYZ").billingState())
    }

    @Test
    fun paymentLabelShowsTheRecordedReceiptAndDate() {
        assertEquals(
            "Last verified payment · QAB123XYZ · 2026-11-01",
            business(status = "active", receipt = "QAB123XYZ", paymentAt = "2026-11-01T10:20:00Z").paymentRecordLabel()
        )
    }

    @Test
    fun phoneDisplayFormatsOwnerAndFallbackMpesaNumbers() {
        assertEquals("+254 712 345 678", business(status = "active", ownerPhone = "254712345678").displayPhoneLabel())
        assertEquals("+254 712 345 678", business(status = "active", ownerPhone = "0712345678").displayPhoneLabel())
        assertEquals("+254 722 111 222 (M-Pesa)", business(status = "active", paymentPhone = "254722111222").displayPhoneLabel())
        assertEquals("No phone on file", business(status = "active").displayPhoneLabel())
    }

    @Test
    fun businessTypeDistinguishesLiquorStoreFromClub() {
        assertEquals(true, business(status = "active", businessType = "counter_seller").isLiquorStoreBusiness())
        assertEquals(true, business(status = "active", businessType = "liquor_store").isLiquorStoreBusiness())
        assertEquals("Liquor Store", business(status = "active", businessType = "counter_seller").businessTypeLabel())
        assertEquals(false, business(status = "active", businessType = "big_club").isLiquorStoreBusiness())
        assertEquals(false, business(status = "active", businessType = "club").isLiquorStoreBusiness())
        assertEquals("Club", business(status = "active", businessType = "big_club").businessTypeLabel())
    }

    private fun business(
        status: String,
        lifecycle: String = "active",
        businessType: String = "big_club",
        receipt: String? = null,
        paidUntil: String? = null,
        paymentAt: String? = null,
        locked: Boolean = false,
        ownerPhone: String? = null,
        paymentPhone: String? = null
    ) = AdminBusiness(
        shopId = "shop-1",
        shopName = "Sample Shop",
        status = lifecycle,
        businessType = businessType,
        businessSize = "medium",
        featureFlags = emptySet(),
        ownerEmail = "owner@example.com",
        memberCount = 1,
        productCount = 0,
        stockUnits = 0,
        createdAt = "2026-01-01T00:00:00Z",
        suspendedAt = null,
        closedAt = null,
        lifecycleReason = null,
        subscriptionStatus = status,
        trialEndsAt = "2026-10-01T00:00:00Z",
        subscriptionEndsAt = paidUntil,
        lastPaymentAt = paymentAt,
        lastPaymentReceipt = receipt,
        lastPaymentPhone = paymentPhone,
        paymentLockActive = locked,
        ownerPhone = ownerPhone
    )
}
