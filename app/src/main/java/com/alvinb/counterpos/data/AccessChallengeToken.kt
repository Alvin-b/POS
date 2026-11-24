package com.alvinb.counterpos.data

private const val ACCESS_CHALLENGE_QR_PREFIX = "CPR1|"

internal fun normalizeAccessChallengeToken(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.startsWith(ACCESS_CHALLENGE_QR_PREFIX, ignoreCase = true)) {
        trimmed.substring(ACCESS_CHALLENGE_QR_PREFIX.length).trim()
    } else {
        trimmed
    }
}

internal fun accessChallengeQrPayload(value: String, fallbackToken: String): String {
    val token = normalizeAccessChallengeToken(value)
        .ifBlank { normalizeAccessChallengeToken(fallbackToken) }
    return if (token.isBlank()) "" else "$ACCESS_CHALLENGE_QR_PREFIX$token"
}
