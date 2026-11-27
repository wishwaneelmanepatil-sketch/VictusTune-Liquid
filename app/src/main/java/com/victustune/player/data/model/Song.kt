package com.victustune.player.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.victustune.player.data.remote.innertube.ThumbnailUtils

@Immutable
sealed class TrackSource {
    @Immutable
    data class Local(val uri: Uri) : TrackSource()
    @Immutable
    data class Remote(
        val videoId: String,
        val thumbnailUrl: String? = null,
        val channelId: String? = null
    ) : TrackSource()
}

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long = 0L,
    val durationMs: Long,
    val contentUri: Uri,
    val albumArtUri: Uri? = null,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val dateAdded: Long = 0L,
    val sizeBytes: Long = 0L,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val source: TrackSource = TrackSource.Local(contentUri)
) {
    val isRemote: Boolean
        get() = source is TrackSource.Remote

    val remoteVideoId: String?
        get() = (source as? TrackSource.Remote)?.videoId

    val durationFormatted: String = formatDurationMs(durationMs)

    companion object {
        fun formatDurationMs(durationMs: Long): String {
            val totalSeconds = (durationMs.coerceAtLeast(0L)) / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            val secPrefix = if (seconds < 10L) "0" else ""
            return "$minutes:$secPrefix$seconds"
        }
        fun fromRemote(
            videoId: String,
            title: String,
            artist: String,
            album: String = "Online Stream",
            durationMs: Long = 0L,
            thumbnailUrl: String? = null,
            channelId: String? = null,
            isDownloaded: Boolean = false
        ): Song {
            val highResThumbnail = ThumbnailUtils.upgrade(thumbnailUrl) ?: thumbnailUrl
            val stableId = (videoId.hashCode().toLong() and 0x7FFFFFFFFFFFFFFFL)
            return Song(
                id = stableId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                contentUri = Uri.parse("stream://$videoId"),
                albumArtUri = highResThumbnail?.let { Uri.parse(it) },
                isDownloaded = isDownloaded,
                source = TrackSource.Remote(
                    videoId = videoId,
                    thumbnailUrl = highResThumbnail,
                    channelId = channelId
                )
            )
        }
    }
}

typealias Track = Song
