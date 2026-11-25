package com.victustune.player.data.transfer

import com.victustune.player.data.model.Song

object M3uCodec {

    fun parse(content: String): List<PlaylistTransferEntry> {
        val cleaned = content.removePrefix("\uFEFF")
        val lines = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val entries = mutableListOf<PlaylistTransferEntry>()

        var pendingDurationMs: Long? = null
        var pendingArtist: String? = null
        var pendingTitle: String? = null

        for (line in lines) {
            if (line.startsWith("#")) {
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    val payload = line.substringAfter(":", "")
                    val commaIndex = payload.indexOf(',')
                    val durationPart = if (commaIndex >= 0) payload.substring(0, commaIndex).trim() else payload.trim()
                    val displayPart = if (commaIndex >= 0) payload.substring(commaIndex + 1).trim() else ""

                    // Duration in #EXTINF can also carry extra attributes separated by space, e.g. "215 tvg-id=..."
                    val durationToken = durationPart.split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull()
                    pendingDurationMs = if (durationToken != null && durationToken > 0) {
                        (durationToken * 1000.0).toLong()
                    } else {
                        0L
                    }

                    val sepIndex = displayPart.indexOf(" - ")
                    if (sepIndex > 0) {
                        pendingArtist = displayPart.substring(0, sepIndex).trim()
                        pendingTitle = displayPart.substring(sepIndex + 3).trim()
                    } else {
                        pendingArtist = ""
                        pendingTitle = displayPart
                    }
                }
                continue
            }

            // Location line (local path, content:// URI, or HTTP/YouTube URL)
            val fallbackTitle = if (pendingTitle != null) {
                pendingTitle
            } else if (!line.startsWith("http://", ignoreCase = true) && !line.startsWith("https://", ignoreCase = true)) {
                val filename = line.substringAfterLast('/').substringAfterLast('\\')
                    .substringBeforeLast('.', line)
                val sep = filename.indexOf(" - ")
                if (sep > 0) {
                    if (pendingArtist == null) pendingArtist = filename.substring(0, sep).trim()
                    filename.substring(sep + 3).trim()
                } else {
                    filename.trim()
                }
            } else {
                ""
            }

            entries.add(
                PlaylistTransferEntry(
                    title = fallbackTitle,
                    artist = pendingArtist.orEmpty(),
                    album = "",
                    durationMs = pendingDurationMs ?: 0L,
                    sourcePathOrUrl = line
                )
            )

            pendingDurationMs = null
            pendingArtist = null
            pendingTitle = null
        }

        return entries
    }

    fun encode(songs: List<Song>): String {
        val entries = songs.map { song ->
            val location = if (song.isRemote) {
                val videoId = song.remoteVideoId ?: song.id.toString()
                "https://music.youtube.com/watch?v=$videoId"
            } else {
                song.contentUri.toString()
            }
            PlaylistTransferEntry(
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                sourcePathOrUrl = location
            )
        }
        return encodeEntries(entries)
    }

    fun encodeEntries(entries: List<PlaylistTransferEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        for (entry in entries) {
            val durationSec = if (entry.durationMs > 0L) entry.durationMs / 1000L else -1L
            val cleanArtist = entry.artist.trim()
            val cleanTitle = entry.title.trim()
            val display = if (cleanArtist.isNotEmpty() && !cleanArtist.equals("Unknown Artist", ignoreCase = true)) {
                "$cleanArtist - $cleanTitle"
            } else {
                cleanTitle
            }
            sb.appendLine("#EXTINF:$durationSec,$display")
            sb.appendLine(entry.sourcePathOrUrl.orEmpty())
        }
        return sb.toString()
    }
}
