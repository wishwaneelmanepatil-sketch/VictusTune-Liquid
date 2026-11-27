package com.victustune.player.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int = 0,
    val trackCount: Int = 0,
    val albumArtUri: Uri? = null
)

@Immutable
data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
    val albumCount: Int = 0
)

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val trackCount: Int = 0
)
