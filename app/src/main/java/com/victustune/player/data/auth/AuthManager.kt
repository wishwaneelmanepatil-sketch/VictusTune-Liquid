package com.victustune.player.data.auth

import android.content.Context
import android.webkit.CookieManager
import com.victustune.player.data.model.AuthState
import com.victustune.player.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class AuthManager(
    private val context: Context,
    private val cryptoManager: CryptoManager = CryptoManager()
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedCookie: String? = null

    @Volatile
    private var cachedSapisid: String? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        loadStoredCredentials()
    }

    private fun loadStoredCredentials() {
        val encryptedCookie = prefs.getString(KEY_ENCRYPTED_COOKIE, null)
        val encryptedSapisid = prefs.getString(KEY_ENCRYPTED_SAPISID, null)

        val cookie = encryptedCookie?.let { cryptoManager.decrypt(it) }
        val sapisid = encryptedSapisid?.let { cryptoManager.decrypt(it) }

        if (!cookie.isNullOrEmpty() && !sapisid.isNullOrEmpty()) {
            cachedCookie = cookie
            cachedSapisid = sapisid

            val name = prefs.getString(KEY_PROFILE_NAME, null)
            val handle = prefs.getString(KEY_PROFILE_HANDLE, "") ?: ""
            val avatar = prefs.getString(KEY_PROFILE_AVATAR, null)

            val profile = if (!name.isNullOrEmpty()) {
                UserProfile(name = name, handle = handle, avatarUrl = avatar)
            } else {
                UserProfile(name = "YouTube Music User", handle = "", avatarUrl = null)
            }
            _authState.value = AuthState.LoggedIn(profile)
        } else {
            _authState.value = AuthState.LoggedOut
        }
    }

    fun getCookie(): String? = cachedCookie

    fun getSapisid(): String? = cachedSapisid

    fun isLoggedIn(): Boolean = _authState.value is AuthState.LoggedIn

    fun saveCredentials(cookieString: String): Boolean {
        val sapisid = extractSapisid(cookieString) ?: return false

        cachedCookie = cookieString
        cachedSapisid = sapisid

        val encryptedCookie = cryptoManager.encrypt(cookieString)
        val encryptedSapisid = cryptoManager.encrypt(sapisid)

        prefs.edit()
            .putString(KEY_ENCRYPTED_COOKIE, encryptedCookie)
            .putString(KEY_ENCRYPTED_SAPISID, encryptedSapisid)
            .apply()

        val existingName = prefs.getString(KEY_PROFILE_NAME, null)
        val existingHandle = prefs.getString(KEY_PROFILE_HANDLE, "") ?: ""
        val existingAvatar = prefs.getString(KEY_PROFILE_AVATAR, null)

        val profile = if (!existingName.isNullOrEmpty()) {
            UserProfile(name = existingName, handle = existingHandle, avatarUrl = existingAvatar)
        } else {
            UserProfile(name = "YouTube Music User", handle = "", avatarUrl = null)
        }
        _authState.value = AuthState.LoggedIn(profile)
        return true
    }

    fun saveUserProfile(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_PROFILE_NAME, profile.name)
            .putString(KEY_PROFILE_HANDLE, profile.handle)
            .putString(KEY_PROFILE_AVATAR, profile.avatarUrl)
            .apply()

        _authState.value = AuthState.LoggedIn(profile)
    }

    fun setSessionExpired() {
        val currentProfile = (_authState.value as? AuthState.LoggedIn)?.profile
        _authState.value = AuthState.Expired(currentProfile)
    }

    fun clearCredentials() {
        cachedCookie = null
        cachedSapisid = null

        prefs.edit().clear().apply()

        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _authState.value = AuthState.LoggedOut
    }

    companion object {
        private const val PREFS_NAME = "victustune_secure_auth"
        private const val KEY_ENCRYPTED_COOKIE = "enc_cookie"
        private const val KEY_ENCRYPTED_SAPISID = "enc_sapisid"
        private const val KEY_PROFILE_NAME = "profile_name"
        private const val KEY_PROFILE_HANDLE = "profile_handle"
        private const val KEY_PROFILE_AVATAR = "profile_avatar"

        fun extractSapisid(cookieString: String): String? {
            val cookieMap = cookieString.split(";")
                .mapNotNull {
                    val parts = it.trim().split("=", limit = 2)
                    if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                }
                .toMap()

            return cookieMap["SAPISID"]
                ?: cookieMap["__Secure-3PAPISID"]
                ?: cookieMap["__Secure-1PAPISID"]
        }

        fun generateSapisidHash(
            sapisid: String,
            timestampSeconds: Long = System.currentTimeMillis() / 1000L
        ): String {
            val message = "$timestampSeconds $sapisid https://music.youtube.com"
            val digest = MessageDigest.getInstance("SHA-1").digest(message.toByteArray(Charsets.UTF_8))
            val sha1Hex = digest.joinToString("") { "%02x".format(it) }
            return "SAPISIDHASH ${timestampSeconds}_$sha1Hex"
        }
    }
}
