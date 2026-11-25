package com.victustune.player.data.transfer

import android.content.Context
import android.net.Uri
import com.victustune.player.data.model.Song
import com.victustune.player.data.repository.DownloadRepository
import com.victustune.player.data.repository.PlaylistRepository
import com.victustune.player.data.repository.StreamingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class PlaylistExporter(
    private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val streamingRepository: StreamingRepository,
    private val downloadRepository: DownloadRepository
) {

    suspend fun exportLocalPlaylist(
        playlistId: Long,
        uri: Uri,
        format: ExportFormat
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = playlistRepository.getSongsForPlaylist(playlistId).first()
            writeSongsToUri(songs, uri, format)
        }
    }

    suspend fun exportFavorites(
        uri: Uri,
        format: ExportFormat
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = playlistRepository.getFavoriteSongs().first()
            writeSongsToUri(songs, uri, format)
        }
    }

    suspend fun exportDownloads(
        uri: Uri,
        format: ExportFormat
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = downloadRepository.completedDownloadsFlow.first()
            writeSongsToUri(songs, uri, format)
        }
    }

    suspend fun exportRemotePlaylist(
        browseId: String,
        uri: Uri,
        format: ExportFormat
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val result = streamingRepository.browse(browseId)
            val songs = result.tracks.map { it.toSong() }
            writeSongsToUri(songs, uri, format)
        }
    }

    suspend fun exportSongs(
        songs: List<Song>,
        uri: Uri,
        format: ExportFormat
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            writeSongsToUri(songs, uri, format)
        }
    }

    private fun writeSongsToUri(songs: List<Song>, uri: Uri, format: ExportFormat): Int {
        val serialized = when (format) {
            ExportFormat.CSV -> CsvCodec.encode(songs, includeBom = true)
            ExportFormat.M3U8 -> M3uCodec.encode(songs)
        }
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Unable to open destination file for writing")

        stream.use { out ->
            out.write(serialized.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        return songs.size
    }
}
