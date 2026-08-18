package com.example.data.settings

import android.content.Context
import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Small encrypted key/value store for secrets such as API keys and OAuth tokens.
 * The AES key is generated and retained by Android Keystore; values are encrypted
 * with a fresh GCM IV on every write.
 */
class SecureSettingsStore(
    context: Context,
    private val legacyPreferenceNames: Set<String> = emptySet()
) {
    private val appContext = context.applicationContext
    private val securePreferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getSecret(key: String): String? {
        val encoded = securePreferences.getString(key, null)
        if (!encoded.isNullOrBlank()) {
            decrypt(encoded)?.let { return it }
        }

        // One-time migration path for values previously stored as plaintext.
        legacyPreferenceNames.asSequence()
            .mapNotNull { preferenceName ->
                appContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
                    .getString(key, null)
                    ?.takeIf(String::isNotBlank)
                    ?.let { legacyValue -> preferenceName to legacyValue }
            }
            .firstOrNull()
            ?.let { (preferenceName, legacyValue) ->
                putSecret(key, legacyValue)
                appContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
                    .edit()
                    .remove(key)
                    .apply()
                return legacyValue
            }

        return null
    }

    fun putSecret(key: String, value: String) {
        val encrypted = encrypt(value) ?: return
        securePreferences.edit().putString(key, encrypted).apply()
    }

    fun removeSecret(key: String) {
        securePreferences.edit().remove(key).apply()
        legacyPreferenceNames.forEach { preferenceName ->
            appContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
                .edit()
                .remove(key)
                .apply()
        }
    }

    private fun encrypt(value: String): String? {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val payload = ByteBuffer.allocate(4 + iv.size + ciphertext.size)
                .putInt(iv.size)
                .put(iv)
                .put(ciphertext)
                .array()
            Base64.encodeToString(payload, Base64.NO_WRAP)
        }.onFailure { error ->
            Log.e(TAG, "Unable to encrypt setting", error)
        }.getOrNull()
    }

    private fun decrypt(encoded: String): String? {
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(payload)
            val ivSize = buffer.int
            require(ivSize in 12..16) { "Invalid GCM IV" }
            val iv = ByteArray(ivSize)
            buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.onFailure { error ->
            Log.w(TAG, "Unable to decrypt setting; treating it as a legacy value", error)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SecureSettingsStore"
        const val PREFERENCES_NAME = "opus_secure_settings"
        const val KEY_ALIAS = "opus_secure_settings_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}
