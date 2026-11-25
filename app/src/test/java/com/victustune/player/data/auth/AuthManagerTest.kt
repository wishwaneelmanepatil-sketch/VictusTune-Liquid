package com.victustune.player.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class AuthManagerTest {

    @Test
    fun testGenerateSapisidHashFormat() {
        val sapisid = "1a2b3c4d5e6f7g8h"
        val timestamp = 1700000000L

        val hashHeader = AuthManager.generateSapisidHash(sapisid, timestamp)

        // Verify prefix and format: SAPISIDHASH {timestamp}_{40-hex-characters}
        val regex = Regex("^SAPISIDHASH 1700000000_[0-9a-f]{40}$")
        assertTrue("Generated header '$hashHeader' should match SAPISIDHASH format", regex.matches(hashHeader))

        // Verify exact SHA-1 computation
        val expectedMessage = "$timestamp $sapisid https://music.youtube.com"
        val expectedDigest = MessageDigest.getInstance("SHA-1").digest(expectedMessage.toByteArray(Charsets.UTF_8))
        val expectedHex = expectedDigest.joinToString("") { "%02x".format(it) }
        val expectedHeader = "SAPISIDHASH ${timestamp}_$expectedHex"

        assertEquals(expectedHeader, hashHeader)
    }

    @Test
    fun testExtractSapisid() {
        // Standard SAPISID cookie
        val cookie1 = "HSID=xxx; SSID=yyy; SAPISID=actual_sapisid_token_123; APISID=zzz"
        assertEquals("actual_sapisid_token_123", AuthManager.extractSapisid(cookie1))

        // Fallback to __Secure-3PAPISID
        val cookie2 = "HSID=xxx; __Secure-3PAPISID=secure_sapisid_456; SSID=yyy"
        assertEquals("secure_sapisid_456", AuthManager.extractSapisid(cookie2))

        // No SAPISID present
        val cookie3 = "HSID=xxx; SSID=yyy; OTHER=zzz"
        assertNull(AuthManager.extractSapisid(cookie3))
    }

    @Test
    fun testCryptoManagerEncryptDecrypt() {
        val crypto = CryptoManager()
        val originalText = "SAPISID=sample_token_for_encryption_test; Path=/; Domain=.youtube.com"

        val encrypted = crypto.encrypt(originalText)
        assertNotNull(encrypted)
        assertTrue(encrypted.isNotEmpty())

        val decrypted = crypto.decrypt(encrypted)
        assertEquals(originalText, decrypted)
    }
}
