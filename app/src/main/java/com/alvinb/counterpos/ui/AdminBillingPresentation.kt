package com.alvinb.counterpos.ui

import com.alvinb.counterpos.data.remote.AdminBusiness

internal enum class AdminBillingState(val filterKey: String, val label: String) {
    PAID("paid", "Paid"),
    ACTIVE_UNVERIFIED("active_unverified", "Active · no receipt"),
    TRIAL("trial", "Trial"),
    EXPIRED("expired", "Expired"),
    LOCKED("locked", "Payment locked"),
    UNKNOWN("unknown", "Unknown")
}

internal fun AdminBusiness.billingState(): AdminBillingState = when {
    paymentLockActive || subscriptionStatus.equals("payment_locked", ignoreCase = true) -> AdminBillingState.LOCKED
    subscriptionStatus.equals("active", ignoreCase = true) && !lastPaymentReceipt.isNullOrBlank() -> AdminBillingState.PAID
    subscriptionStatus.equals("active", ignoreCase = true) -> AdminBillingState.ACTIVE_UNVERIFIED
    subscriptionStatus.equals("trial", ignoreCase = true) -> AdminBillingState.TRIAL
    subscriptionStatus.equals("expired", ignoreCase = true) -> AdminBillingState.EXPIRED
    else -> AdminBillingState.UNKNOWN
}

internal fun AdminBusiness.billingExpiryLabel(): String = when (billingState()) {
    AdminBillingState.PAID -> subscriptionEndsAt.adminDate()?.let { "Paid term through $it" } ?: "Paid · expiry unavailable"
    AdminBillingState.ACTIVE_UNVERIFIED -> subscriptionEndsAt.adminDate()?.let { "Active term through $it" } ?: "Active term · expiry unavailable"
    AdminBillingState.TRIAL -> trialEndsAt.adminDate()?.let { "Trial ends $it" } ?: "Trial · end date unavailable"
    AdminBillingState.EXPIRED -> (subscriptionEndsAt ?: trialEndsAt)?.adminDate()?.let { "Expired on $it" } ?: "Expired · end date unavailable"
    AdminBillingState.LOCKED -> (subscriptionEndsAt ?: trialEndsAt)?.adminDate()?.let { "Payment locked · subscription date $it" } ?: "Payment locked"
    AdminBillingState.UNKNOWN -> "Billing status unavailable"
}

internal fun AdminBusiness.paymentRecordLabel(): String = when {
    !lastPaymentReceipt.isNullOrBlank() -> buildString {
        append("Last verified payment · ")
        append(lastPaymentReceipt)
        lastPaymentAt.adminDate()?.let { append(" · ").append(it) }
    }
    !lastPaymentAt.isNullOrBlank() -> "Payment date recorded · ${lastPaymentAt.adminDate()} · receipt unavailable"
    else -> "No verified payment receipt recorded"
}

private fun String?.adminDate(): String? = this
    ?.takeIf(String::isNotBlank)
    ?.substringBefore('T')
    ?.takeIf(String::isNotBlank)
