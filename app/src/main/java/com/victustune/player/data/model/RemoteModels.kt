package com.victustune.player.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class AudioQuality {
    AUTO,
    HIGH,
    MEDIUM,
    LOW
}

@Immutable
data class RemoteTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val durationText: String? = null,
    val thumbnailUrl: String? = null,
    val channelId: String? = null
) {
    fun toSong(): Song = Song.fromRemote(
        videoId = videoId,
        title = title,
        artist = artist,
        album = album ?: "YouTube Music",
        durationMs = durationMs,
        thumbnailUrl = thumbnailUrl,
        channelId = channelId
    )
}

@Immutable
data class RemoteAlbum(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: String? = null,
    val thumbnailUrl: String? = null,
    val trackCount: Int = 0
)

@Immutable
data class RemoteArtist(
    val browseId: String,
    val name: String,
    val subscribers: String? = null,
    val thumbnailUrl: String? = null
)

@Immutable
data class RemotePlaylist(
    val browseId: String,
    val title: String,
    val author: String? = null,
    val trackCountText: String? = null,
    val thumbnailUrl: String? = null
)

@Immutable
data class SearchResult(
    val tracks: List<RemoteTrack> = emptyList(),
    val albums: List<RemoteAlbum> = emptyList(),
    val artists: List<RemoteArtist> = emptyList(),
    val playlists: List<RemotePlaylist> = emptyList(),
    val continuationToken: String? = null
)

@Immutable
data class BrowseSection(
    val title: String,
    val subtitle: String? = null,
    val tracks: List<RemoteTrack> = emptyList(),
    val albums: List<RemoteAlbum> = emptyList(),
    val playlists: List<RemotePlaylist> = emptyList(),
    val artists: List<RemoteArtist> = emptyList()
)

@Immutable
data class BrowseResult(
    val title: String? = null,
    val headerThumbnailUrl: String? = null,
    val description: String? = null,
    val sections: List<BrowseSection> = emptyList(),
    val tracks: List<RemoteTrack> = emptyList(),
    val continuationToken: String? = null
)
