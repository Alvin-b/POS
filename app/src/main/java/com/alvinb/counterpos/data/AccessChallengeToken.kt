package com.alvinb.counterpos.data

import java.util.Locale

private const val ACCESS_CHALLENGE_QR_PREFIX = "CPR1|"
private val PASSKEY_PREFIXES = listOf("CPB1.", "CPX1.")
private val ENROLLMENT_TOKEN_REGEX = Regex(
    """(?:CPSA1\|[A-Za-z0-9._|-]+|CPR1\|[A-Za-z0-9_-]+|CPT1\|[A-Za-z0-9_-]+|(?:CPB1\.|CPX1\.)+[A-Za-z0-9_-]+)""",
    RegexOption.IGNORE_CASE
)

internal data class PasskeyParseResult(
    val rawPasskey: String,
    val isButchery: Boolean
)

internal fun normalizeAccessChallengeToken(value: String): String {
    var current = value.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?.trim('"', '\'')
        .orEmpty()
    while (current.startsWith(ACCESS_CHALLENGE_QR_PREFIX, ignoreCase = true)) {
        current = current.substring(ACCESS_CHALLENGE_QR_PREFIX.length).trim().trim('"', '\'')
    }
    return current.trim('"', '\'').trim()
}

internal fun accessChallengeQrPayload(value: String, fallbackToken: String): String {
    val token = normalizeAccessChallengeToken(value)
        .ifBlank { normalizeAccessChallengeToken(fallbackToken) }
    return if (token.isBlank()) "" else "$ACCESS_CHALLENGE_QR_PREFIX$token"
}

internal fun parseEnrollmentPasskey(value: String): PasskeyParseResult {
    var current = value.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?.trim('"', '\'')
        .orEmpty()
    var isButchery = false
    var strippedAny = true
    while (strippedAny) {
        strippedAny = false
        for (prefix in PASSKEY_PREFIXES) {
            if (current.startsWith(prefix, ignoreCase = true)) {
                if (prefix.equals("CPB1.", ignoreCase = true)) {
                    isButchery = true
                }
                current = current.substring(prefix.length).trim().trim('"', '\'')
                strippedAny = true
                break
            }
        }
    }
    val rawPasskey = current.trim('"', '\'').trim().uppercase(Locale.ROOT)
    return PasskeyParseResult(rawPasskey = rawPasskey, isButchery = isButchery)
}

internal fun extractEnrollmentCredentialPayload(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val match = ENROLLMENT_TOKEN_REGEX.find(trimmed) ?: return null
    val token = match.value.trim()
    return when {
        token.startsWith("CPR1|", ignoreCase = true) -> {
            val clean = normalizeAccessChallengeToken(token)
            if (clean.isBlank()) null else "$ACCESS_CHALLENGE_QR_PREFIX$clean"
        }
        token.startsWith("CPB1.", ignoreCase = true) || token.startsWith("CPX1.", ignoreCase = true) -> {
            val parsed = parseEnrollmentPasskey(token)
            if (parsed.rawPasskey.isBlank()) null
            else if (parsed.isButchery) "CPB1.${parsed.rawPasskey}" else "CPX1.${parsed.rawPasskey}"
        }
        else -> token
    }
}

