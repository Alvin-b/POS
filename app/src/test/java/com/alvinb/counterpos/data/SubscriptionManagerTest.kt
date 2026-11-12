package com.alvinb.counterpos.data

import com.alvinb.counterpos.data.remote.BusinessMembership
import com.alvinb.counterpos.data.remote.SubscriptionStatusResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class SubscriptionManagerTest {

    @Test
    fun testNormalizeKenyanPhone() {
        // Valid 07... and 01... numbers
        assertEquals("254712345678", SubscriptionManager.normalizeKenyanPhone("0712 345 678"))
        assertEquals("254712345678", SubscriptionManager.normalizeKenyanPhone("+254 712 345 678"))
        assertEquals("254712345678", SubscriptionManager.normalizeKenyanPhone("254712345678"))
        assertEquals("254112345678", SubscriptionManager.normalizeKenyanPhone("0112345678"))
        assertEquals("254112345678", SubscriptionManager.normalizeKenyanPhone("+254112345678"))

        // Invalid numbers
        assertNull(SubscriptionManager.normalizeKenyanPhone("12345"))
        assertNull(SubscriptionManager.normalizeKenyanPhone("0812345678"))
        assertNull(SubscriptionManager.normalizeKenyanPhone("254912345678"))
        assertNull(SubscriptionManager.normalizeKenyanPhone(""))
    }

    @Test
    fun testFormatPhoneForDisplay() {
        assertEquals("0712 345 678", SubscriptionManager.formatPhoneForDisplay("254712345678"))
        assertEquals("0112 345 678", SubscriptionManager.formatPhoneForDisplay("254112345678"))
    }

    @Test
    fun testActiveSubscriptionEntitled() {
        val now = System.currentTimeMillis()
        val futureExpiry = Instant.ofEpochMilli(now + TimeUnit.DAYS.toMillis(20)).toString()

        val membership = BusinessMembership(
            shopId = "shop-1",
            shopName = "Club Nexus",
            role = "owner",
            status = "active",
            currency = "KES",
            lowStockThreshold = 5,
            subscriptionStatus = "active",
            subscriptionEndsAt = futureExpiry,
            subscriptionPriceKsh = 3000,
            subscriptionPlanDays = 30
        )

        val entitlement = SubscriptionManager.evaluate(membership, currentTimeMillis = now, isOnline = true)
        assertTrue(entitlement.isEntitled)
        assertEquals(SubscriptionStatus.ACTIVE, entitlement.status)
        assertEquals(20, entitlement.daysRemaining)
        assertEquals(3000, entitlement.amountDueKsh)
        assertFalse(entitlement.isGracePeriod)
    }

    @Test
    fun serverConfirmedPaymentClearsCachedPaymentLock() {
        val now = System.currentTimeMillis()
        val expiry = Instant.ofEpochMilli(now + TimeUnit.DAYS.toMillis(30)).toString()
        val previouslyLocked = BusinessMembership(
            shopId = "shop-locked",
            shopName = "Locked Club",
            role = "owner",
            status = "active",
            currency = "KES",
            lowStockThreshold = 5,
            subscriptionStatus = "payment_locked",
            subscriptionEndsAt = expiry,
            paymentLockActive = true,
            paymentLockAmountKsh = 4200,
            paymentLockReason = "Outstanding balance",
            paymentLockStartedAt = Instant.ofEpochMilli(now - 60_000).toString()
        )
        val paidStatus = SubscriptionStatusResult(
            shopId = previouslyLocked.shopId,
            shopName = previouslyLocked.shopName,
            status = "active",
            isEntitled = true,
            daysLeft = 30,
            trialEndsAt = null,
            subscriptionEndsAt = expiry,
            subscriptionPriceKsh = 2500,
            subscriptionPlanDays = 30,
            lastPaymentAt = Instant.ofEpochMilli(now).toString(),
            lastPaymentReceipt = "QAB123XYZ",
            latestCheckoutRequestId = "ws_CO_123",
            latestPaymentStatus = "paid",
            paymentLockActive = false,
            paymentLockAmountKsh = 0,
            paymentLockReason = null
        )

        val refreshed = SubscriptionManager.mergeServerSubscriptionStatus(
            membership = previouslyLocked,
            status = paidStatus,
            verifiedAtMillis = now
        )

        assertFalse(refreshed.paymentLockActive)
        assertEquals(0, refreshed.paymentLockAmountKsh)
        assertNull(refreshed.paymentLockReason)
        assertNull(refreshed.paymentLockStartedAt)
        assertTrue(SubscriptionManager.evaluate(refreshed, now, isOnline = true).isEntitled)
    }

    @Test
    fun serverPaymentLockRemainsInForceAfterSubscriptionRefresh() {
        val now = System.currentTimeMillis()
        val expiry = Instant.ofEpochMilli(now + TimeUnit.DAYS.toMillis(30)).toString()
        val membership = BusinessMembership(
            shopId = "shop-locked-2",
            shopName = "Locked Lounge",
            role = "owner",
            status = "active",
            currency = "KES",
            lowStockThreshold = 5,
            subscriptionStatus = "active",
            subscriptionEndsAt = expiry
        )
        val lockedStatus = SubscriptionStatusResult(
            shopId = membership.shopId,
            shopName = membership.shopName,
            status = "payment_locked",
            isEntitled = false,
            daysLeft = 30,
            trialEndsAt = null,
            subscriptionEndsAt = expiry,
            subscriptionPriceKsh = 4200,
            subscriptionPlanDays = 30,
            lastPaymentAt = null,
            lastPaymentReceipt = null,
            latestCheckoutRequestId = null,
            latestPaymentStatus = null,
            paymentLockActive = true,
            paymentLockAmountKsh = 4200,
            paymentLockReason = "Outstanding balance"
        )

        val refreshed = SubscriptionManager.mergeServerSubscriptionStatus(
            membership = membership,
            status = lockedStatus,
            verifiedAtMillis = now
        )

        assertTrue(refreshed.paymentLockActive)
        assertEquals(4200, refreshed.paymentLockAmountKsh)
        assertEquals("Outstanding balance", refreshed.paymentLockReason)
        val entitlement = SubscriptionManager.evaluate(refreshed, now, isOnline = true)
        assertFalse(entitlement.isEntitled)
        assertEquals(SubscriptionStatus.PAYMENT_LOCKED, entitlement.status)
    }

    @Test
    fun test14DayTrialEntitled() {
        val now = System.currentTimeMillis()
        val trialExpiry = Instant.ofEpochMilli(now + TimeUnit.DAYS.toMillis(10)).toString()

        val membership = BusinessMembership(
            shopId = "shop-2",
            shopName = "Sky Lounge",
            role = "owner",
            status = "active",
            currency = "KES",
            lowStockThreshold = 5,
            subscriptionStatus = "trial",
            trialEndsAt = trialExpiry
        )

        val entitlement = SubscriptionManager.evaluate(membership, currentTimeMillis = now, isOnline = true)
        assertTrue(entitlement.isEntitled)
        assertEquals(SubscriptionStatus.TRIAL, entitlement.status)
        assertEquals(10, entitlement.daysRemaining)
        assertFalse(entitlement.isGracePeriod)
    }

    @Test
    fun testOfflineGracePeriodUnder72Hours() {
        val now = System.currentTimeMillis()
        val pastExpiry = Instant.ofEpochMilli(now - TimeUnit.HOURS.toMillis(10)).toString()
        val lastVerified = now - TimeUnit.HOURS.toMillis(10) // verified 10 hours ago

        val membership = BusinessMembership(
            shopId = "shop-3",
            shopName = "Corner Bar",
            role = "owner",
            status = "active",
            currency = "KES",
            lowStockThreshold = 5,
            subscriptionStatus = "trial",
            trialEndsAt = pastExpiry,
            lastVerifiedEntitlementAt = lastVerified
        )

        // Offline: should grant grace period (72 - 10 = 62 hours remaining)
        val entitlement = SubscriptionManager.evaluate(membership, currentTimeMillis = now, isOnline = false)
        assertTrue(entitlement.isEntitled)
        assertEquals(SubscriptionStatus.OFFLINE_GRACE, entitlement.status)
        assertTrue(entitlement.isGracePeriod)
        assertEquals(62, entitlement.hoursGraceRemaining)
    }

    @Test
    fun testOfflineGracePeriodExceededLocksApp() {
        val now = System.currentTimeMillis()
        val pastExpiry = Instant.ofEpochMilli(now - TimeUnit.HOURS.toMillis(80)).toString()
        val lastVerified = now - TimeUnit.HOURS.toMillis(80) // verified 80 hours ago (>72h grace)

        val membership = BusinessMembership(
            shopId = "shop-4",
            shopName = "Late Bar",
            role = "owner",
            status = "active",
            currency = "KES",
            lowStockThreshold = 5,
            subscriptionStatus = "trial",
            trialEndsAt = pastExpiry,
            lastVerifiedEntitlementAt = lastVerified
        )

        // Offline after 80h: grace expired, must lock to payment screen
        val entitlement = SubscriptionManager.evaluate(membership, currentTimeMillis = now, isOnline = false)
        assertFalse(entitlement.isEntitled)
        assertEquals(SubscriptionStatus.EXPIRED, entitlement.status)
    }

    @Test
    fun testOnlineExpiredLocksApp() {
        val now = System.currentTimeMillis()
        val pastExpiry = Instant.ofEpochMilli(now - TimeUnit.HOURS.toMillis(2)).toString()

        val membership = BusinessMembership(
            shopId = "shop-5",
            shopName = "Expired Club",
            role = "owner",
            status = "active",
            currency = "KES",
            lowStockThreshold = 5,
            subscriptionStatus = "expired",
            subscriptionEndsAt = pastExpiry
        )

        // Online and expired: must lock immediately to payment screen
        val entitlement = SubscriptionManager.evaluate(membership, currentTimeMillis = now, isOnline = true)
        assertFalse(entitlement.isEntitled)
        assertEquals(SubscriptionStatus.EXPIRED, entitlement.status)
    }

    @Test
    fun offlineGraceExpiresAt72HoursAndMissingVerificationFailsClosed() {
        val now = 1_800_000_000_000L
        val lastVerified = now - TimeUnit.HOURS.toMillis(72)

        assertTrue(SubscriptionManager.hasOfflineGracePeriodElapsed(lastVerified, now))
        assertTrue(SubscriptionManager.hasOfflineGracePeriodElapsed(0L, now))
        assertFalse(
            SubscriptionManager.hasOfflineGracePeriodElapsed(
                now - TimeUnit.HOURS.toMillis(71),
                now
            )
        )
    }
}
