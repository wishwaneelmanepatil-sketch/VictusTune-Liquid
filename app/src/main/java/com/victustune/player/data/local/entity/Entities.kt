package com.victustune.player.data.local.entity

import android.net.Uri
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.victustune.player.data.model.Song
import com.victustune.player.data.model.TrackSource

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("playlistId"), Index("songId")]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val orderIndex: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val songId: Long,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: Long = 0L,
    val durationMs: Long = 0L,
    val contentUri: String = "",
    val albumArtUri: String? = null,
    val isRemote: Boolean = false,
    val remoteVideoId: String? = null,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song {
        val parsedContentUri = if (contentUri.isNotEmpty()) Uri.parse(contentUri) else Uri.EMPTY
        val parsedAlbumArtUri = albumArtUri?.let { Uri.parse(it) }
        val src = if (isRemote) {
            TrackSource.Remote(
                videoId = remoteVideoId ?: songId.toString(),
                thumbnailUrl = albumArtUri
            )
        } else {
            TrackSource.Local(parsedContentUri)
        }
        return Song(
            id = songId,
            title = title.ifEmpty { "Unknown Title" },
            artist = artist.ifEmpty { "Unknown Artist" },
            album = album.ifEmpty { "Unknown Album" },
            albumId = albumId,
            durationMs = durationMs,
            contentUri = parsedContentUri,
            albumArtUri = parsedAlbumArtUri,
            isFavorite = true,
            source = src
        )
    }

    companion object {
        fun fromSong(song: Song): FavoriteEntity {
            return FavoriteEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                albumId = song.albumId,
                durationMs = song.durationMs,
                contentUri = song.contentUri.toString(),
                albumArtUri = song.albumArtUri?.toString(),
                isRemote = song.isRemote,
                remoteVideoId = song.remoteVideoId,
                addedAt = System.currentTimeMillis()
            )
        }
    }
}

@Entity(tableName = "play_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: Long,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
