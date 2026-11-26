package com.alvinb.counterpos

import android.content.Context
import android.net.ConnectivityManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvinb.counterpos.data.CounterPosRepository
import com.alvinb.counterpos.data.SubscriptionManager
import com.alvinb.counterpos.data.SubscriptionStatus
import com.alvinb.counterpos.data.local.AppDatabase
import com.alvinb.counterpos.data.local.entity.OutboxEntity
import com.alvinb.counterpos.data.remote.BusinessMembership
import com.alvinb.counterpos.data.remote.SubscriptionStatusResult
import com.alvinb.counterpos.data.remote.SupabaseRestClient
import com.alvinb.counterpos.ui.CounterPosViewModel
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubscriptionAndSyncAuditTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: CounterPosRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val remote = SupabaseRestClient(context)
        remote.clearSession()
        repository = CounterPosRepository(
            context = context,
            dao = database.counterPosDao(),
            assets = context.assets,
            remote = remote
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun parseTimestampHandlesAllPostgresAndIsoFormatsWithoutJavaTime() {
        fun utcMillis(
            year: Int,
            month: Int,
            day: Int,
            hour: Int = 0,
            min: Int = 0,
            sec: Int = 0,
            ms: Int = 0
        ): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(year, month - 1, day, hour, min, sec)
            set(Calendar.MILLISECOND, ms)
        }.timeInMillis

        assertEquals(utcMillis(2026, 11, 25, 14, 30, 45, 0), SubscriptionManager.parseTimestamp("2026-11-25T14:30:45Z"))
        assertEquals(utcMillis(2026, 11, 25, 14, 30, 45, 123), SubscriptionManager.parseTimestamp("2026-11-25T14:30:45.123456Z"))
        assertEquals(utcMillis(2026, 11, 25, 14, 30, 45, 500), SubscriptionManager.parseTimestamp("2026-11-25 14:30:45.5+00"))
        assertEquals(utcMillis(2026, 11, 25, 11, 30, 45, 0), SubscriptionManager.parseTimestamp("2026-11-25T14:30:45+03:00"))
        assertEquals(utcMillis(2026, 11, 25, 19, 30, 45, 0), SubscriptionManager.parseTimestamp("2026-11-25T14:30:45-05:00"))
        assertEquals(utcMillis(2026, 11, 25, 0, 0, 0, 0), SubscriptionManager.parseTimestamp("2026-11-25"))

        val formatted = SubscriptionManager.formatUtcIso(utcMillis(2026, 11, 25, 9, 5, 3, 42))
        assertEquals("2026-11-25T09:05:03.042Z", formatted)
        assertEquals(utcMillis(2026, 11, 25, 9, 5, 3, 42), SubscriptionManager.parseTimestamp(formatted))

        assertNull(SubscriptionManager.parseTimestamp(null))
        assertNull(SubscriptionManager.parseTimestamp(""))
        assertNull(SubscriptionManager.parseTimestamp("null"))
        assertNull(SubscriptionManager.parseTimestamp("2026-13-45T00:00:00Z"))
    }

    @Test
    fun mergeServerSubscriptionStatusEnforcesAndClearsPaymentLock() {
        val baseMembership = BusinessMembership(
            shopId = "shop-1",
            shopName = "Savannah Club",
            role = "owner",
            status = "active",
            employmentStatus = "active",
            businessType = "big_club",
            businessSize = "medium",
            counterInventoryMode = "shared",
            counterCount = 2,
            featureFlags = setOf("tables"),
            currency = "KES",
            lowStockThreshold = 5,
            subscriptionStatus = "active",
            subscriptionEndsAt = SubscriptionManager.formatUtcIso(System.currentTimeMillis() + 86_400_000L * 10),
            paymentLockActive = false
        )

        val lockedResult = SubscriptionStatusResult(
            shopId = "shop-1",
            shopName = "Savannah Club",
            status = "payment_locked",
            isEntitled = false,
            daysLeft = 0,
            trialEndsAt = null,
            subscriptionEndsAt = baseMembership.subscriptionEndsAt,
            subscriptionPriceKsh = 2500,
            subscriptionPlanDays = 30,
            lastPaymentAt = null,
            lastPaymentReceipt = null,
            latestCheckoutRequestId = null,
            latestPaymentStatus = null,
            paymentLockActive = true,
            paymentLockAmountKsh = 4500,
            paymentLockReason = "Arrears due"
        )

        val lockedMembership = SubscriptionManager.mergeServerSubscriptionStatus(
            membership = baseMembership,
            status = lockedResult,
            verifiedAtMillis = System.currentTimeMillis()
        )
        val lockedEval = SubscriptionManager.evaluate(lockedMembership, isOnline = true)
        assertEquals(SubscriptionStatus.PAYMENT_LOCKED, lockedEval.status)
        assertFalse(lockedEval.isEntitled)
        assertEquals(4500, lockedEval.amountDueKsh)

        val unlockedResult = lockedResult.copy(
            status = "active",
            isEntitled = true,
            daysLeft = 30,
            paymentLockActive = false,
            paymentLockAmountKsh = 0,
            paymentLockReason = null
        )
        val unlockedMembership = SubscriptionManager.mergeServerSubscriptionStatus(
            membership = lockedMembership,
            status = unlockedResult,
            verifiedAtMillis = System.currentTimeMillis()
        )
        val unlockedEval = SubscriptionManager.evaluate(unlockedMembership, isOnline = true)
        assertEquals(SubscriptionStatus.ACTIVE, unlockedEval.status)
        assertTrue(unlockedEval.isEntitled)
    }

    @Test
    fun signInWithCredentialRejectsCpr1DeviceChallengeWithGuidance() {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val subscriptionManager = SubscriptionManager(context)
        val viewModel = CounterPosViewModel(repository, connectivity, subscriptionManager)

        viewModel.signInWithCredential("CPR1|KYTX-AB63-6WJV-BXMM")
        val state = viewModel.uiState.value
        assertNotNull(state.qrError)
        assertTrue(state.qrError!!.contains("another phone", ignoreCase = true))
        assertFalse(state.isAuthenticating)
    }

    @Test
    fun prepareOutboxPayloadSupportsButcheryOperations() = runBlocking {
        val operations = listOf(
            "butchery_upsert_config" to JSONObject().put("shop_id", "s1").put("butchery_name", "Prime Meats"),
            "butchery_upsert_cut" to JSONObject().put("shop_id", "s1").put("id", "c1").put("name", "Beef"),
            "butchery_update_meat_price" to JSONObject().put("shop_id", "s1").put("meat_name", "Beef").put("price_per_kg_ksh", 720),
            "butchery_sale" to JSONObject().put("shop_id", "s1").put("cut_id", "c1").put("weight_grams", 500).put("total_ksh", 360),
            "butchery_undo_sale" to JSONObject().put("shop_id", "s1").put("sale_id", 12L)
        )

        for ((opType, payloadJson) in operations) {
            val item = OutboxEntity(
                operationId = "op-$opType",
                shopId = "s1",
                actorUserId = "u1",
                operationType = opType,
                payloadJson = payloadJson.toString(),
                createdAt = 1000L
            )
            val prepared = repository.prepareOutboxPayload(item)
            assertEquals(payloadJson.toString(), prepared.toString())
        }
    }
}
