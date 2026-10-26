package com.alvinb.counterpos

import com.alvinb.counterpos.data.remote.shouldClearSessionForRefreshFailure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseRefreshPolicyTest {
    @Test
    fun `explicit invalid refresh token clears the session`() {
        assertTrue(shouldClearSessionForRefreshFailure(400, "Invalid Refresh Token: Refresh Token Not Found"))
        assertTrue(shouldClearSessionForRefreshFailure(400, "refresh_token_not_found"))
        assertTrue(shouldClearSessionForRefreshFailure(401, "refresh token expired"))
    }

    @Test
    fun `temporary refresh failures preserve the session`() {
        assertFalse(shouldClearSessionForRefreshFailure(400, "invalid JSON body"))
        assertFalse(shouldClearSessionForRefreshFailure(429, "refresh rate limit exceeded"))
        assertFalse(shouldClearSessionForRefreshFailure(503, "refresh service unavailable"))
        assertFalse(shouldClearSessionForRefreshFailure(401, "invalid login credentials"))
    }
}
