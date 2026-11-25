package com.victustune.player.data.model

data class UserProfile(
    val name: String,
    val handle: String = "",
    val avatarUrl: String? = null
)

sealed class AuthState {
    object LoggedOut : AuthState()
    object Checking : AuthState()
    data class LoggedIn(val profile: UserProfile) : AuthState()
    data class Expired(val profile: UserProfile?) : AuthState()
}
