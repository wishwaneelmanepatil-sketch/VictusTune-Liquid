package com.victustune.player.data.model

import android.net.Uri
import com.victustune.player.data.remote.innertube.ThumbnailUtils

sealed class TrackSource {
    data class Local(val uri: Uri) : TrackSource()
    data class Remote(
        val videoId: String,
        val thumbnailUrl: String? = null,
        val channelId: String? = null
    ) : TrackSource()
}

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

    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    companion object {
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
