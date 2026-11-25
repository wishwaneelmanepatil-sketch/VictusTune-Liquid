package com.victustune.player.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager {

    private val keyAlias = "victustune_auth_key"
    private val transformation = "AES/GCM/NoPadding"
    private val keyStore: KeyStore? = try {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    } catch (e: Throwable) {
        null
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = keyStore
        if (ks != null) {
            if (!ks.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(keyGenSpec)
                return keyGenerator.generateKey()
            }
            val entry = ks.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }
        // Fallback for JVM unit test execution
        val fallbackKeyBytes = "VictusTuneStaticSecKeyFallback12".toByteArray(Charsets.UTF_8)
        return SecretKeySpec(fallbackKeyBytes, "AES")
    }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(cipherTextBase64: String): String? {
        return try {
            val combined = Base64.getDecoder().decode(cipherTextBase64)
            if (combined.size < 12) return null
            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            val encryptedBytes = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, encryptedBytes, 0, encryptedBytes.size)

            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
