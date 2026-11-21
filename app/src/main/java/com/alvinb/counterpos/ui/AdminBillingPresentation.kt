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

internal fun AdminBusiness.effectivePhone(): String? =
    ownerPhone?.takeIf(String::isNotBlank) ?: lastPaymentPhone?.takeIf(String::isNotBlank)

internal fun formatDisplayPhone(raw: String?): String? {
    val clean = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
    val digits = clean.filter(Char::isDigit)
    return when {
        digits.length == 12 && digits.startsWith("254") ->
            "+${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6, 9)} ${digits.substring(9)}"
        digits.length == 10 && digits.startsWith("0") ->
            "+254 ${digits.substring(1, 4)} ${digits.substring(4, 7)} ${digits.substring(7)}"
        else -> clean
    }
}

internal fun AdminBusiness.displayPhoneLabel(): String {
    val ownerFormatted = formatDisplayPhone(ownerPhone)
    if (ownerFormatted != null) return ownerFormatted
    val mpesaFormatted = formatDisplayPhone(lastPaymentPhone)
    if (mpesaFormatted != null) return "$mpesaFormatted (M-Pesa)"
    return "No phone on file"
}

internal fun AdminBusiness.isLiquorStoreBusiness(): Boolean {
    val type = businessType.trim().lowercase(java.util.Locale.ROOT)
    return type == "counter_seller" || type == "liquor_store"
}

internal fun AdminBusiness.businessTypeLabel(): String =
    if (isLiquorStoreBusiness()) "Liquor Store" else "Club"

private fun String?.adminDate(): String? = this
    ?.takeIf(String::isNotBlank)
    ?.substringBefore('T')
    ?.takeIf(String::isNotBlank)
