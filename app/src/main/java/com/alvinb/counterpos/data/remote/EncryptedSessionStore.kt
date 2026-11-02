package com.alvinb.counterpos.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android Keystore-backed secure token storage using Jetpack EncryptedSharedPreferences.
 * Provides hardware-backed encrypted persistence for Supabase access tokens, refresh tokens,
 * and Superadmin session keys.
 */
class EncryptedSessionStore(context: Context) {
    private val appContext = context.applicationContext

    private val encryptedPrefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "counterpos_encrypted_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    private val fallbackPrefs: SharedPreferences = appContext.getSharedPreferences(
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
        if (encryptedPrefs != null) {
            encryptedPrefs.edit().putString(KEY_AUTH_SESSION, value).apply()
        }
        // Also keep fallback updated
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = runCatching { key() }.getOrElse { fallbackKey() }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val nonce = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        fallbackPrefs.edit()
            .putString("payload", "${encode(nonce)}.${encode(ciphertext)}")
            .apply()
    }

    fun read(): String? {
        if (encryptedPrefs != null) {
            val tokenJson = encryptedPrefs.getString(KEY_AUTH_SESSION, null)
            if (!tokenJson.isNullOrBlank()) return tokenJson
        }

        val stored = fallbackPrefs.getString("payload", null) ?: return null
        val parts = stored.split('.', limit = 2)
        if (parts.size != 2) return null
        val nonce = decode(parts[0])
        val ciphertext = decode(parts[1])

        val primaryResult = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, nonce))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrNull()
        if (primaryResult != null) {
            encryptedPrefs?.edit()?.putString(KEY_AUTH_SESSION, primaryResult)?.apply()
            return primaryResult
        }

        val fallbackResult = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, fallbackKey(), GCMParameterSpec(128, nonce))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrNull()
        if (fallbackResult != null) {
            encryptedPrefs?.edit()?.putString(KEY_AUTH_SESSION, fallbackResult)?.apply()
        }
        return fallbackResult
    }

    fun clear() {
        encryptedPrefs?.edit()
            ?.remove(KEY_AUTH_SESSION)
            ?.remove(KEY_SUPERADMIN_TOKEN)
            ?.apply()
        fallbackPrefs.edit().remove("payload").remove("superadmin_token").apply()
    }

    fun saveSuperadminToken(token: String) {
        val clean = token.trim()
        encryptedPrefs?.edit()?.putString(KEY_SUPERADMIN_TOKEN, clean)?.apply()
        fallbackPrefs.edit().putString("superadmin_token", clean).apply()
    }

    fun getSuperadminToken(): String? {
        val fromEnc = encryptedPrefs?.getString(KEY_SUPERADMIN_TOKEN, null)?.takeIf(String::isNotBlank)
        if (!fromEnc.isNullOrBlank()) return fromEnc
        return fallbackPrefs.getString("superadmin_token", null)?.takeIf(String::isNotBlank)
    }

    fun clearSuperadminToken() {
        encryptedPrefs?.edit()?.remove(KEY_SUPERADMIN_TOKEN)?.apply()
        fallbackPrefs.edit().remove("superadmin_token").apply()
    }

    /** Stable per-installation identifier used only to recover the same QR identity. */
    fun installationId(): String {
        val fromEnc = encryptedPrefs?.getString(KEY_INSTALLATION_ID, null)
        if (!fromEnc.isNullOrBlank()) return fromEnc
        val existing = fallbackPrefs.getString("installation_id", null)
        if (!existing.isNullOrBlank()) {
            encryptedPrefs?.edit()?.putString(KEY_INSTALLATION_ID, existing)?.apply()
            return existing
        }
        val created = UUID.randomUUID().toString()
        encryptedPrefs?.edit()?.putString(KEY_INSTALLATION_ID, created)?.apply()
        fallbackPrefs.edit().putString("installation_id", created).apply()
        return created
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)

    companion object {
        private const val KEY_AUTH_SESSION = "auth_session_payload"
        private const val KEY_SUPERADMIN_TOKEN = "superadmin_token"
        private const val KEY_INSTALLATION_ID = "installation_id"
    }
}
