package com.alvinb.counterpos.ui

/** A missing selection is routed to the picker/setup screen, not the disabled screen. */
internal fun shouldShowEmployeeAccessLocked(employmentStatus: String?): Boolean =
    employmentStatus != null && employmentStatus != "active"
