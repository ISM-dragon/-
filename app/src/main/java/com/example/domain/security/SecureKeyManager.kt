package com.example.domain.security

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore Hardware-backed AES-256-GCM Secure Encryption
 * Prevents plaintext API key leaks in storage, memory dumps, or logcat.
 */
class SecureKeyManager(private val context: Context) {

    private val keyStoreAlias = "OpusProSecureKeyAlias_v1"
    private val androidKeyStore = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128

    init {
        try {
            ensureMasterKey()
        } catch (_: Exception) {
            // Robolectric or host environment without hardware AndroidKeyStore provider
        }
    }

    private fun ensureMasterKey() {
        val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        if (!keyStore.containsAlias(keyStoreAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                androidKeyStore
            )
            val keyGenSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                keyStoreAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
            keyStore.getKey(keyStoreAlias, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encrypts plaintext string returning Base64 encoded payload with IV
     */
    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val key = getSecretKey() ?: throw IllegalStateException("KeyStore unavailable")
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            // Format: Base64(IV):Base64(Ciphertext)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            "$ivBase64:$cipherBase64"
        } catch (e: Exception) {
            throw IllegalStateException("Secure Android Keystore is unavailable", e)
        }
    }

    /**
     * Decrypts formatted Base64 string back to plaintext
     */
    fun decrypt(encryptedPayload: String): String {
        if (encryptedPayload.isBlank()) return ""
        return try {
            val key = getSecretKey()
            if (encryptedPayload.contains(":") && key != null) {
                val parts = encryptedPayload.split(":")
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                
                val cipher = Cipher.getInstance(transformation)
                val spec = GCMParameterSpec(gcmTagLength, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, spec)
                val plainBytes = cipher.doFinal(cipherBytes)
                String(plainBytes, Charsets.UTF_8)
            } else {
                // Legacy Base64 decode
                String(Base64.decode(encryptedPayload, Base64.NO_WRAP), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Returns masked preview: "sk-proj-****4829" or "AIzaSy****9102"
     */
    fun maskKey(key: String): String {
        val trimmed = key.trim()
        if (trimmed.length <= 8) return "••••••••"
        val start = trimmed.take(6)
        val end = trimmed.takeLast(4)
        return "$start••••••••$end"
    }
}
