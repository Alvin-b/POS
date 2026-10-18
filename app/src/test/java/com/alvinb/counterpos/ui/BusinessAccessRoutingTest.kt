package com.alvinb.counterpos.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessAccessRoutingTest {
    @Test
    fun missingBusinessSelectionDoesNotShowEmployeeLock() {
        assertFalse(shouldShowEmployeeAccessLocked(null))
    }

    @Test
    fun activeMembershipDoesNotShowEmployeeLock() {
        assertFalse(shouldShowEmployeeAccessLocked("active"))
    }

    @Test
    fun suspendedOrTerminatedMembershipShowsEmployeeLock() {
        assertTrue(shouldShowEmployeeAccessLocked("suspended"))
        assertTrue(shouldShowEmployeeAccessLocked("terminated"))
    }
}
