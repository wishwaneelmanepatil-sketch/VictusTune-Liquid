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

@Entity(tableName = "synced_playlists")
data class SyncedPlaylistEntity(
    @PrimaryKey
    val browseId: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val trackCount: Int = 0,
    val isLikedMusic: Boolean = false,
    val syncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey
    val videoId: String,
    val songId: Long = (videoId.hashCode().toLong() and 0x7FFFFFFFFFFFFFFFL),
    val title: String,
    val artist: String,
    val album: String = "Downloaded",
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val artworkPath: String? = null,
    val status: String = "QUEUED", // QUEUED, DOWNLOADING, COMPLETED, FAILED
    val progress: Float = 0f,
    val sizeBytes: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song {
        val artUri = artworkPath?.let { Uri.fromFile(java.io.File(it)) }
            ?: thumbnailUrl?.let { Uri.parse(it) }
        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            contentUri = Uri.parse("stream://$videoId"),
            albumArtUri = artUri,
            sizeBytes = sizeBytes,
            isDownloaded = (status == "COMPLETED"),
            source = TrackSource.Remote(
                videoId = videoId,
                thumbnailUrl = thumbnailUrl
            )
        )
    }

    companion object {
        fun fromSong(
            song: Song,
            status: String = "QUEUED",
            artworkPath: String? = null
        ): DownloadedTrackEntity {
            val vid = song.remoteVideoId ?: song.id.toString()
            return DownloadedTrackEntity(
                videoId = vid,
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                thumbnailUrl = song.albumArtUri?.toString(),
                artworkPath = artworkPath,
                status = status,
                progress = if (status == "COMPLETED") 100f else 0f,
                sizeBytes = song.sizeBytes,
                downloadedAt = System.currentTimeMillis()
            )
        }
    }
}

@Entity(tableName = "saved_tracks")
data class SavedTrackEntity(
    @PrimaryKey
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String = "Online Stream",
    val durationMs: Long = 0L,
    val contentUri: String,
    val albumArtUri: String? = null,
    val isRemote: Boolean = false,
    val remoteVideoId: String? = null,
    val channelId: String? = null
) {
    fun toSong(): Song {
        val parsedContentUri = if (contentUri.isNotEmpty()) Uri.parse(contentUri) else Uri.EMPTY
        val parsedArtUri = albumArtUri?.let { Uri.parse(it) }
        val src = if (isRemote) {
            TrackSource.Remote(
                videoId = remoteVideoId ?: songId.toString(),
                thumbnailUrl = albumArtUri,
                channelId = channelId
            )
        } else {
            TrackSource.Local(parsedContentUri)
        }
        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            contentUri = parsedContentUri,
            albumArtUri = parsedArtUri,
            source = src
        )
    }

    companion object {
        fun fromSong(song: Song): SavedTrackEntity {
            return SavedTrackEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                contentUri = song.contentUri.toString(),
                albumArtUri = song.albumArtUri?.toString(),
                isRemote = song.isRemote,
                remoteVideoId = song.remoteVideoId,
                channelId = (song.source as? TrackSource.Remote)?.channelId
            )
        }
    }
}
