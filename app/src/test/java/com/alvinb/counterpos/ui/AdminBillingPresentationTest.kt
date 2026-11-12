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

    private fun business(
        status: String,
        lifecycle: String = "active",
        receipt: String? = null,
        paidUntil: String? = null,
        paymentAt: String? = null,
        locked: Boolean = false
    ) = AdminBusiness(
        shopId = "shop-1",
        shopName = "Sample Shop",
        status = lifecycle,
        businessType = "big_club",
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
        paymentLockActive = locked
    )
}
