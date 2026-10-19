package com.alvinb.counterpos.ui

/**
 * Only explicitly disabled accounts are blocked.
 * Pending, null, or active accounts are not locked out.
 */
internal fun shouldShowEmployeeAccessLocked(employmentStatus: String?): Boolean =
    employmentStatus.equals("suspended", ignoreCase = true) ||
    employmentStatus.equals("terminated", ignoreCase = true) ||
    employmentStatus.equals("locked", ignoreCase = true)
