package com.alvinb.counterpos.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Small Android Keystore-backed envelope for the refresh/access-token JSON. */
class EncryptedSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "counterpos_secure_session",
        Context.MODE_PRIVATE
    )

    private val keyAlias = "counterpos_session_key"

    private fun fallbackKey(): SecretKey {
        val installation = installationId().toByteArray(StandardCharsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(installation)
        return SecretKeySpec(digest, "AES")
    }

    private fun key(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = keyStore.getKey(keyAlias, null) as? SecretKey
            if (existing != null) return existing

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generator.generateKey()
        } catch (_: Exception) {
            fallbackKey()
        }
    }

    fun save(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = runCatching { key() }.getOrElse { fallbackKey() }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val nonce = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        preferences.edit()
            .putString("payload", "${encode(nonce)}.${encode(ciphertext)}")
            .apply()
    }

    fun read(): String? {
        val stored = preferences.getString("payload", null) ?: return null
        val parts = stored.split('.', limit = 2)
        if (parts.size != 2) return null
        val nonce = decode(parts[0])
        val ciphertext = decode(parts[1])

        // First attempt with primary key
        val primaryResult = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, nonce))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrNull()
        if (primaryResult != null) return primaryResult

        // Second attempt with fallback installation key
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, fallbackKey(), GCMParameterSpec(128, nonce))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun clear() {
        preferences.edit().remove("payload").apply()
    }

    /** Stable per-installation identifier used only to recover the same QR identity. */
    fun installationId(): String {
        val existing = preferences.getString("installation_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        preferences.edit().putString("installation_id", created).apply()
        return created
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)
}
