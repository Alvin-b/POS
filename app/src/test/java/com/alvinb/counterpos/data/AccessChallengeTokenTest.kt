package com.alvinb.counterpos.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessChallengeTokenTest {
    @Test
    fun stripsCanonicalQrPrefix() {
        assertEquals("abc123", normalizeAccessChallengeToken("CPR1|abc123"))
    }

    @Test
    fun trimsWhitespaceAndStripsPrefixWithoutCaseSensitivity() {
        assertEquals("abc123", normalizeAccessChallengeToken("  cpr1|abc123  "))
    }

    @Test
    fun leavesBareChallengeTokenUnchanged() {
        assertEquals("abc123", normalizeAccessChallengeToken("  abc123  "))
    }

    @Test
    fun createsCanonicalQrPayloadFromPrefixedOrBareValue() {
        assertEquals("CPR1|abc123", accessChallengeQrPayload(" cPr1|abc123 ", "fallback"))
        assertEquals("CPR1|fallback", accessChallengeQrPayload("", " cpr1|fallback "))
    }
}
