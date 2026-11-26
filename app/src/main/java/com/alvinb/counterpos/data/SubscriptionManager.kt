package com.alvinb.counterpos.data

import android.content.Context
import android.content.SharedPreferences
import com.alvinb.counterpos.data.remote.BusinessMembership
import com.alvinb.counterpos.data.remote.SubscriptionStatusResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class SubscriptionStatus {
    TRIAL,
    ACTIVE,
    OFFLINE_GRACE,
    EXPIRED,
    PAYMENT_LOCKED
}

data class SubscriptionEntitlement(
    val status: SubscriptionStatus,
    val isEntitled: Boolean,
    val daysRemaining: Int,
    val hoursGraceRemaining: Int,
    val expiryTimestamp: Long,
    val expiryFormatted: String,
    val amountDueKsh: Int,
    val planDays: Int,
    val isGracePeriod: Boolean,
    val statusLabel: String,
    val message: String,
    val isPaymentLocked: Boolean = false
)

class SubscriptionManager(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences("counterpos_subscription_entitlement", Context.MODE_PRIVATE)

    /** Persist the time the app last received an authoritative entitlement response for this shop. */
    fun recordServerVerifiedEntitlement(shopId: String, verifiedAtMillis: Long) {
        if (shopId.isBlank() || verifiedAtMillis <= 0L) return
        val key = entitlementKey(shopId)
        val previous = preferences.getLong(key, 0L)
        if (verifiedAtMillis > previous) {
            preferences.edit().putLong(key, verifiedAtMillis).apply()
        }
    }

    fun lastServerVerifiedEntitlement(shopId: String): Long =
        if (shopId.isBlank()) 0L else preferences.getLong(entitlementKey(shopId), 0L)

    /** Returns true when no verification is available or the 72-hour offline window has elapsed. */
    fun hasOfflineGracePeriodElapsed(shopId: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        hasOfflineGracePeriodElapsed(lastServerVerifiedEntitlement(shopId), nowMillis)

    fun evaluateMembership(
        membership: BusinessMembership,
        isOnline: Boolean,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): SubscriptionEntitlement {
        val persistedVerifiedAt = lastServerVerifiedEntitlement(membership.shopId)
        val verifiedAt = maxOf(persistedVerifiedAt, membership.lastVerifiedEntitlementAt)
        if (isOnline && membership.lastVerifiedEntitlementAt > persistedVerifiedAt) {
            recordServerVerifiedEntitlement(membership.shopId, membership.lastVerifiedEntitlementAt)
        }
        if (!isOnline && verifiedAt <= 0L) {
            return evaluate(membership, currentTimeMillis = currentTimeMillis, isOnline = true)
        }
        return evaluate(
            membership.copy(lastVerifiedEntitlementAt = verifiedAt),
            currentTimeMillis = currentTimeMillis,
            isOnline = isOnline
        )
    }

    private fun entitlementKey(shopId: String) = "${KEY_LAST_SERVER_VERIFIED_ENTITLEMENT}_$shopId"

    companion object {

    const val KEY_LAST_SERVER_VERIFIED_ENTITLEMENT = "last_server_verified_entitlement"

    /** Maximum offline tolerance after server-verified entitlement expires (72 hours). */
    const val OFFLINE_GRACE_PERIOD_MILLIS = 72 * 60 * 60 * 1000L // 72 hours

    /** Default trial length for new stores (14 days). */
    const val DEFAULT_TRIAL_DAYS = 14

    /** Default monthly renewal price in KSh. */
    const val DEFAULT_MONTHLY_PRICE_KSH = 2500

    /** Default renewal term in days. */
    const val DEFAULT_PLAN_DAYS = 30

    /** Merge an authoritative subscription response into the cached business membership. */
    fun mergeServerSubscriptionStatus(
        membership: BusinessMembership,
        status: SubscriptionStatusResult,
        verifiedAtMillis: Long
    ): BusinessMembership = membership.copy(
        subscriptionStatus = status.status,
        trialEndsAt = status.trialEndsAt,
        subscriptionEndsAt = status.subscriptionEndsAt,
        subscriptionPriceKsh = status.subscriptionPriceKsh,
        subscriptionPlanDays = status.subscriptionPlanDays,
        lastPaymentAt = status.lastPaymentAt,
        lastPaymentReceipt = status.lastPaymentReceipt,
        paymentLockActive = status.paymentLockActive,
        paymentLockAmountKsh = status.paymentLockAmountKsh,
        paymentLockReason = status.paymentLockReason,
        paymentLockStartedAt = if (status.paymentLockActive) membership.paymentLockStartedAt else null,
        lastVerifiedEntitlementAt = verifiedAtMillis
    )

    /** Standardizes any valid Kenyan phone number to Daraja international format (254XXXXXXXXX). */
    fun normalizeKenyanPhone(raw: String): String? {
        val digits = raw.filter(Char::isDigit)
        val normalized = when {
            digits.startsWith("254") && digits.length == 12 -> digits
            digits.startsWith("0") && digits.length == 10 -> "254" + digits.substring(1)
            (digits.startsWith("7") || digits.startsWith("1")) && digits.length == 9 -> "254$digits"
            else -> null
        }
        return normalized?.takeIf {
            it.length == 12 && (it.startsWith("2547") || it.startsWith("2541"))
        }
    }

    /** Formats a Kenyan phone number for user-friendly UI display (e.g. 0712 345 678). */
    fun formatPhoneForDisplay(phone: String): String {
        val norm = normalizeKenyanPhone(phone) ?: phone
        return if (norm.startsWith("254") && norm.length == 12) {
            "0${norm.substring(3, 6)} ${norm.substring(6, 9)} ${norm.substring(9)}"
        } else {
            phone
        }
    }

    /** Prefills only a valid Kenyan M-Pesa number; JSON null-like strings are ignored. */
    fun paymentPhonePrefill(lastPaymentPhone: String?, ownerPhone: String?): String =
        listOfNotNull(lastPaymentPhone, ownerPhone)
            .firstNotNullOfOrNull { candidate -> normalizeKenyanPhone(candidate)?.let(::formatPhoneForDisplay) }
            .orEmpty()

    /** Formats epoch milliseconds as an ISO-8601 UTC timestamp compatible with API 24+. */
    fun formatUtcIso(timestampMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date(timestampMillis))
    }

    /** Parses ISO 8601 or Postgres UTC timestamps (including +00/+00:00 offsets) to epoch millis without API 26+ APIs. */
    fun parseTimestamp(iso: String?): Long? {
        val trimmed = iso?.trim()?.takeIf {
            it.isNotEmpty() && !it.equals("null", ignoreCase = true) && !it.equals("undefined", ignoreCase = true)
        } ?: return null

        val match = ISO_TIMESTAMP_REGEX.matchEntire(trimmed) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        val hour = match.groupValues[4].takeIf(String::isNotEmpty)?.toIntOrNull() ?: 0
        val minute = match.groupValues[5].takeIf(String::isNotEmpty)?.toIntOrNull() ?: 0
        val second = match.groupValues[6].takeIf(String::isNotEmpty)?.toIntOrNull() ?: 0
        val fracRaw = match.groupValues[7]
        val millis = if (fracRaw.isEmpty()) {
            0
        } else {
            fracRaw.take(3).padEnd(3, '0').toIntOrNull() ?: return null
        }

        val tzPart = match.groupValues[8]
        val offsetMillis = if (tzPart.isEmpty() || tzPart.equals("Z", ignoreCase = true)) {
            0L
        } else {
            val sign = if (tzPart[0] == '-') -1 else 1
            val tzHour = tzPart.substring(1, 3).toIntOrNull() ?: return null
            val tzMin = match.groupValues[9].takeIf(String::isNotEmpty)?.toIntOrNull() ?: 0
            if (tzHour > 23 || tzMin > 59) return null
            sign * ((tzHour * 60L + tzMin) * 60_000L)
        }

        return runCatching {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"), Locale.US)
            cal.isLenient = false
            cal.clear()
            cal.set(year, month - 1, day, hour, minute, second)
            cal.set(java.util.Calendar.MILLISECOND, millis)
            cal.timeInMillis - offsetMillis
        }.getOrNull()
    }

    private val ISO_TIMESTAMP_REGEX = Regex(
        """^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?)?(Z|[+-]\d{2}(?::?(\d{2}))?)?$""",
        RegexOption.IGNORE_CASE
    )

    /** Formats timestamp to readable format (e.g. 24 Nov 2026). */
    fun formatDate(timestampMillis: Long): String {
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestampMillis))
    }

    /**
     * Evaluates current entitlement for a given shop membership using authoritative
     * server/Superadmin status, individual trial/paid end dates, and up to 72 hours of offline grace.
     */
    fun evaluate(
        membership: BusinessMembership,
        currentTimeMillis: Long = System.currentTimeMillis(),
        isOnline: Boolean = true
    ): SubscriptionEntitlement {
        val amountDue = if (membership.subscriptionPriceKsh > 0) membership.subscriptionPriceKsh else DEFAULT_MONTHLY_PRICE_KSH
        val planDays = if (membership.subscriptionPlanDays > 0) membership.subscriptionPlanDays else DEFAULT_PLAN_DAYS
        val rawStatus = membership.subscriptionStatus.trim().lowercase(Locale.ROOT)

        if (membership.paymentLockActive || rawStatus == "payment_locked") {
            val lockedAmount = membership.paymentLockAmountKsh.takeIf { it > 0 } ?: amountDue
            val lockReason = membership.paymentLockReason?.trim()?.takeIf {
                it.isNotEmpty() && !it.equals("null", ignoreCase = true) && !it.equals("undefined", ignoreCase = true)
            }
            return SubscriptionEntitlement(
                status = SubscriptionStatus.PAYMENT_LOCKED,
                isEntitled = false,
                daysRemaining = 0,
                hoursGraceRemaining = 0,
                expiryTimestamp = currentTimeMillis,
                expiryFormatted = "Payment required",
                amountDueKsh = lockedAmount,
                planDays = planDays,
                isGracePeriod = false,
                statusLabel = "Payment Lock",
                message = lockReason?.let { "Business locked by platform administrator: $it" }
                    ?: "This business is locked by the platform administrator. Pay KSh $lockedAmount to restore access.",
                isPaymentLocked = true
            )
        }

        val subscriptionEnd = parseTimestamp(membership.subscriptionEndsAt)
        val trialEnd = parseTimestamp(membership.trialEndsAt)
        val dayMs = TimeUnit.DAYS.toMillis(1)

        // Superadmin explicitly marked business as expired — bypass stale timestamps
        if (rawStatus != "expired") {
            // 1. Active paid / admin-activated term
            if (rawStatus == "active" || rawStatus == "paid") {
                if (subscriptionEnd != null && subscriptionEnd > currentTimeMillis) {
                    val daysLeft = ((subscriptionEnd - currentTimeMillis + dayMs - 1) / dayMs).toInt().coerceAtLeast(1)
                    return SubscriptionEntitlement(
                        status = SubscriptionStatus.ACTIVE,
                        isEntitled = true,
                        daysRemaining = daysLeft,
                        hoursGraceRemaining = 0,
                        expiryTimestamp = subscriptionEnd,
                        expiryFormatted = formatDate(subscriptionEnd),
                        amountDueKsh = amountDue,
                        planDays = planDays,
                        isGracePeriod = false,
                        statusLabel = "Active ($daysLeft ${if (daysLeft == 1) "day" else "days"} left)",
                        message = "Subscription is active until ${formatDate(subscriptionEnd)}."
                    )
                }
                if (subscriptionEnd == null) {
                    return SubscriptionEntitlement(
                        status = SubscriptionStatus.ACTIVE,
                        isEntitled = true,
                        daysRemaining = planDays,
                        hoursGraceRemaining = 0,
                        expiryTimestamp = currentTimeMillis + planDays * dayMs,
                        expiryFormatted = "Active",
                        amountDueKsh = amountDue,
                        planDays = planDays,
                        isGracePeriod = false,
                        statusLabel = "Active Plan",
                        message = "Subscription is active. Standard renewal: KSh $amountDue for $planDays days."
                    )
                }
            }

            // 2. Trial status assigned by Superadmin / onboarding
            if (rawStatus == "trial") {
                if (trialEnd != null && trialEnd > currentTimeMillis) {
                    val daysLeft = ((trialEnd - currentTimeMillis + dayMs - 1) / dayMs).toInt().coerceAtLeast(1)
                    return SubscriptionEntitlement(
                        status = SubscriptionStatus.TRIAL,
                        isEntitled = true,
                        daysRemaining = daysLeft,
                        hoursGraceRemaining = 0,
                        expiryTimestamp = trialEnd,
                        expiryFormatted = formatDate(trialEnd),
                        amountDueKsh = amountDue,
                        planDays = planDays,
                        isGracePeriod = false,
                        statusLabel = "Free Trial ($daysLeft ${if (daysLeft == 1) "day" else "days"} left)",
                        message = "Free trial active until ${formatDate(trialEnd)}. Renews at KSh $amountDue for $planDays days."
                    )
                }
                if (subscriptionEnd != null && subscriptionEnd > currentTimeMillis) {
                    val daysLeft = ((subscriptionEnd - currentTimeMillis + dayMs - 1) / dayMs).toInt().coerceAtLeast(1)
                    return SubscriptionEntitlement(
                        status = SubscriptionStatus.ACTIVE,
                        isEntitled = true,
                        daysRemaining = daysLeft,
                        hoursGraceRemaining = 0,
                        expiryTimestamp = subscriptionEnd,
                        expiryFormatted = formatDate(subscriptionEnd),
                        amountDueKsh = amountDue,
                        planDays = planDays,
                        isGracePeriod = false,
                        statusLabel = "Active ($daysLeft ${if (daysLeft == 1) "day" else "days"} left)",
                        message = "Subscription is active until ${formatDate(subscriptionEnd)}."
                    )
                }
                if (trialEnd == null) {
                    return SubscriptionEntitlement(
                        status = SubscriptionStatus.TRIAL,
                        isEntitled = true,
                        daysRemaining = DEFAULT_TRIAL_DAYS,
                        hoursGraceRemaining = 0,
                        expiryTimestamp = currentTimeMillis + DEFAULT_TRIAL_DAYS * dayMs,
                        expiryFormatted = "Trial Active",
                        amountDueKsh = amountDue,
                        planDays = planDays,
                        isGracePeriod = false,
                        statusLabel = "Free Trial Active",
                        message = "Free trial active. Renews at KSh $amountDue for $planDays days."
                    )
                }
            }

            // 3. Fallback if status string is unknown/empty: trust valid future timestamps
            if (rawStatus !in setOf("active", "paid", "trial", "expired")) {
                if (subscriptionEnd != null && subscriptionEnd > currentTimeMillis) {
                    val daysLeft = ((subscriptionEnd - currentTimeMillis + dayMs - 1) / dayMs).toInt().coerceAtLeast(1)
                    return SubscriptionEntitlement(
                        status = SubscriptionStatus.ACTIVE,
                        isEntitled = true,
                        daysRemaining = daysLeft,
                        hoursGraceRemaining = 0,
                        expiryTimestamp = subscriptionEnd,
                        expiryFormatted = formatDate(subscriptionEnd),
                        amountDueKsh = amountDue,
                        planDays = planDays,
                        isGracePeriod = false,
                        statusLabel = "Active ($daysLeft ${if (daysLeft == 1) "day" else "days"} left)",
                        message = "Subscription is active until ${formatDate(subscriptionEnd)}."
                    )
                }
                if (trialEnd != null && trialEnd > currentTimeMillis) {
                    val daysLeft = ((trialEnd - currentTimeMillis + dayMs - 1) / dayMs).toInt().coerceAtLeast(1)
                    return SubscriptionEntitlement(
                        status = SubscriptionStatus.TRIAL,
                        isEntitled = true,
                        daysRemaining = daysLeft,
                        hoursGraceRemaining = 0,
                        expiryTimestamp = trialEnd,
                        expiryFormatted = formatDate(trialEnd),
                        amountDueKsh = amountDue,
                        planDays = planDays,
                        isGracePeriod = false,
                        statusLabel = "Free Trial ($daysLeft ${if (daysLeft == 1) "day" else "days"} left)",
                        message = "Free trial active until ${formatDate(trialEnd)}. Renews at KSh $amountDue for $planDays days."
                    )
                }
            }
        }

        // 4. Subscription or trial has expired: evaluate offline 72-hour grace period
        val latestExpiry = subscriptionEnd ?: trialEnd ?: currentTimeMillis
        val lastVerified = if (membership.lastVerifiedEntitlementAt > 0L) {
            membership.lastVerifiedEntitlementAt
        } else {
            latestExpiry
        }

        val elapsedSinceVerified = currentTimeMillis - lastVerified
        val graceRemainingMillis = OFFLINE_GRACE_PERIOD_MILLIS - elapsedSinceVerified

        if (!isOnline && graceRemainingMillis > 0L) {
            val hoursRemaining = (graceRemainingMillis / TimeUnit.HOURS.toMillis(1)).toInt().coerceAtLeast(1)
            return SubscriptionEntitlement(
                status = SubscriptionStatus.OFFLINE_GRACE,
                isEntitled = true,
                daysRemaining = 0,
                hoursGraceRemaining = hoursRemaining,
                expiryTimestamp = latestExpiry,
                expiryFormatted = formatDate(latestExpiry),
                amountDueKsh = amountDue,
                planDays = planDays,
                isGracePeriod = true,
                statusLabel = "Offline Grace ($hoursRemaining hours left)",
                message = "Subscription expired. Working offline under 72h grace ($hoursRemaining hrs left). Connect to renew."
            )
        }

        // 5. Fully Expired: lock app to Payment Screen
        val expiryDateStr = formatDate(latestExpiry)
        return SubscriptionEntitlement(
            status = SubscriptionStatus.EXPIRED,
            isEntitled = false,
            daysRemaining = 0,
            hoursGraceRemaining = 0,
            expiryTimestamp = latestExpiry,
            expiryFormatted = expiryDateStr,
            amountDueKsh = amountDue,
            planDays = planDays,
            isGracePeriod = false,
            statusLabel = "Subscription Expired",
            message = "Your business subscription ended on $expiryDateStr. Pay KSh $amountDue to resume service."
        )
    }

    fun hasOfflineGracePeriodElapsed(lastServerVerifiedAtMillis: Long, nowMillis: Long): Boolean =
        lastServerVerifiedAtMillis <= 0L ||
            nowMillis - lastServerVerifiedAtMillis >= OFFLINE_GRACE_PERIOD_MILLIS
    }
}
