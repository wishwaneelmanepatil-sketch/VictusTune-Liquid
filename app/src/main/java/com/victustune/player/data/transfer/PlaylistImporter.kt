package com.victustune.player.data.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.victustune.player.data.model.Playlist
import com.victustune.player.data.model.RemoteTrack
import com.victustune.player.data.model.Song
import com.victustune.player.data.remote.innertube.SearchFilter
import com.victustune.player.data.repository.AudioRepository
import com.victustune.player.data.repository.PlaylistRepository
import com.victustune.player.data.repository.StreamingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class ParsedPlaylistPreview(
    val defaultName: String,
    val format: ExportFormat,
    val entries: List<PlaylistTransferEntry>
)

data class ImportProgress(
    val processedCount: Int,
    val totalCount: Int,
    val currentTrackName: String,
    val matchedTracks: List<Song>,
    val unmatchedEntries: List<PlaylistTransferEntry>,
    val isCompleted: Boolean = false,
    val createdPlaylist: Playlist? = null,
    val errorMessage: String? = null
)

class PlaylistImporter(
    private val context: Context,
    private val audioRepository: AudioRepository,
    private val streamingRepository: StreamingRepository,
    private val playlistRepository: PlaylistRepository
) {

    suspend fun parseFromUri(uri: Uri): ParsedPlaylistPreview = withContext(Dispatchers.IO) {
        val fileName = resolveFileName(uri) ?: "Imported Playlist"
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IllegalStateException("Unable to read selected file")

        val cleanedStart = content.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val isM3u = fileName.endsWith(".m3u", ignoreCase = true) ||
            fileName.endsWith(".m3u8", ignoreCase = true) ||
            cleanedStart.startsWith("#EXTM3U", ignoreCase = true) ||
            cleanedStart.startsWith("#EXTINF", ignoreCase = true)

        val format = if (isM3u) ExportFormat.M3U8 else ExportFormat.CSV
        val entries = if (isM3u) M3uCodec.parse(content) else CsvCodec.parse(content)

        val baseName = fileName
            .substringBeforeLast('.')
            .trim()
            .ifEmpty { "Imported Playlist" }

        ParsedPlaylistPreview(
            defaultName = baseName,
            format = format,
            entries = entries
        )
    }

    fun importPlaylist(
        playlistName: String,
        entries: List<PlaylistTransferEntry>,
        matchOnline: Boolean
    ): Flow<ImportProgress> = channelFlow {
        val total = entries.size
        if (total == 0) {
            send(
                ImportProgress(
                    processedCount = 0,
                    totalCount = 0,
                    currentTrackName = "",
                    matchedTracks = emptyList(),
                    unmatchedEntries = emptyList(),
                    isCompleted = true,
                    errorMessage = "No tracks found in file"
                )
            )
            return@channelFlow
        }

        val localSongs = audioRepository.songsFlow.value
        val results = arrayOfNulls<Song>(total)
        val unmatched = mutableListOf<Pair<Int, PlaylistTransferEntry>>()
        val mutex = Mutex()
        val semaphore = Semaphore(3)
        var processed = 0

        send(
            ImportProgress(
                processedCount = 0,
                totalCount = total,
                currentTrackName = entries.first().title.ifBlank { entries.first().sourcePathOrUrl.orEmpty() },
                matchedTracks = emptyList(),
                unmatchedEntries = emptyList()
            )
        )

        coroutineScope {
            entries.mapIndexed { index, entry ->
                async {
                    semaphore.withPermit {
                        val matched = try {
                            matchEntry(entry, localSongs, matchOnline)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }

                        val snapshot = mutex.withLock {
                            if (matched != null) {
                                results[index] = matched
                            } else {
                                unmatched.add(index to entry)
                            }
                            processed++
                            val currentMatched = results.filterNotNull()
                            val currentUnmatched = unmatched.sortedBy { it.first }.map { it.second }
                            ImportProgress(
                                processedCount = processed,
                                totalCount = total,
                                currentTrackName = entry.title.ifBlank { entry.artist }.ifBlank { "Track ${index + 1}" },
                                matchedTracks = currentMatched,
                                unmatchedEntries = currentUnmatched
                            )
                        }
                        send(snapshot)
                    }
                }
            }.awaitAll()
        }

        val finalMatched = results.filterNotNull()
        val finalUnmatched = unmatched.sortedBy { it.first }.map { it.second }
        val finalName = playlistName.trim().ifEmpty { "Imported Playlist" }

        val createdPlaylist = if (finalMatched.isNotEmpty()) {
            playlistRepository.createPlaylistWithSongs(finalName, finalMatched)
        } else {
            null
        }

        send(
            ImportProgress(
                processedCount = total,
                totalCount = total,
                currentTrackName = "",
                matchedTracks = finalMatched,
                unmatchedEntries = finalUnmatched,
                isCompleted = true,
                createdPlaylist = createdPlaylist
            )
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun matchEntry(
        entry: PlaylistTransferEntry,
        localSongs: List<Song>,
        matchOnline: Boolean
    ): Song? {
        // 1. Direct YouTube / YouTube Music videoId match
        val videoId = extractYouTubeVideoId(entry.sourcePathOrUrl)
        if (videoId != null) {
            return Song.fromRemote(
                videoId = videoId,
                title = entry.title.ifBlank { "YouTube Track ($videoId)" },
                artist = entry.artist.ifBlank { "Unknown Artist" },
                album = entry.album.ifBlank { "Imported" },
                durationMs = entry.durationMs
            )
        }

        // 2. Local MediaStore library match
        val localMatch = matchLocalTrack(entry, localSongs)
        if (localMatch != null) {
            return localMatch
        }

        // 3. Online InnerTube fallback
        if (matchOnline) {
            val query = listOf(entry.title, entry.artist)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()
            if (query.isNotEmpty()) {
                val searchResult = streamingRepository.search(query, SearchFilter.SONGS)
                val bestRemote = pickBestRemoteTrack(entry, searchResult.tracks)
                if (bestRemote != null) {
                    return bestRemote.toSong().let { remoteSong ->
                        if (entry.album.isNotBlank() && remoteSong.album == "Online Stream") {
                            remoteSong.copy(album = entry.album)
                        } else {
                            remoteSong
                        }
                    }
                }
            }
        }

        return null
    }

    private fun pickBestRemoteTrack(entry: PlaylistTransferEntry, tracks: List<RemoteTrack>): RemoteTrack? {
        if (tracks.isEmpty()) return null
        val targetTitle = normalizeForMatch(entry.title)
        if (targetTitle.isEmpty()) return tracks.first()

        return tracks.take(3).firstOrNull { candidate ->
            val candTitle = normalizeForMatch(candidate.title)
            candTitle == targetTitle || candTitle.contains(targetTitle) || targetTitle.contains(candTitle)
        } ?: tracks.first()
    }

    private fun resolveFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            } ?: uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    companion object {
        private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
        private val WATCH_PARAM_REGEX = Regex("[?&]v=([A-Za-z0-9_-]{11})")
        private val SHORT_URL_REGEX = Regex("(?:youtu\\.be/|youtube\\.com/(?:shorts|embed|v)/)([A-Za-z0-9_-]{11})")
        private val NOISE_PAREN_REGEX = Regex(
            "[\\(\\[]\\s*(?:official\\s*(?:music\\s*)?(?:video|audio|lyric(?:s)?|visualizer)|lyric(?:s)?\\s*video|audio|hd|hq|4k|remaster(?:ed)?|live|feat\\.?[^\\)\\]]*|ft\\.?[^\\)\\]]*)[\\)\\]]",
            RegexOption.IGNORE_CASE
        )
        private val FEAT_TAIL_REGEX = Regex("\\s+(?:feat\\.?|ft\\.?)\\s+.*$", RegexOption.IGNORE_CASE)
        private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{N}\\s]")
        private val MULTI_SPACE_REGEX = Regex("\\s+")

        fun extractYouTubeVideoId(source: String?): String? {
            val s = source?.trim() ?: return null
            if (s.isEmpty()) return null

            if (s.startsWith("stream://", ignoreCase = true)) {
                val id = s.removePrefix("stream://").substringBefore('?').trim()
                if (VIDEO_ID_REGEX.matches(id)) return id
            }

            if (s.contains("youtube.com", ignoreCase = true) || s.contains("youtu.be", ignoreCase = true)) {
                WATCH_PARAM_REGEX.find(s)?.groupValues?.getOrNull(1)?.let { return it }
                SHORT_URL_REGEX.find(s)?.groupValues?.getOrNull(1)?.let { return it }
            }

            return null
        }

        fun normalizeForMatch(text: String): String {
            if (text.isBlank()) return ""
            var cleaned = text.lowercase()
            cleaned = NOISE_PAREN_REGEX.replace(cleaned, " ")
            cleaned = FEAT_TAIL_REGEX.replace(cleaned, " ")
            cleaned = NON_ALNUM_REGEX.replace(cleaned, " ")
            cleaned = MULTI_SPACE_REGEX.replace(cleaned, " ").trim()
            return cleaned
        }

        fun matchLocalTrack(entry: PlaylistTransferEntry, localSongs: List<Song>): Song? {
            if (localSongs.isEmpty()) return null

            // 1. Path / URI match (non-HTTP sources)
            val src = entry.sourcePathOrUrl?.trim()
            if (!src.isNullOrEmpty() &&
                !src.startsWith("http://", ignoreCase = true) &&
                !src.startsWith("https://", ignoreCase = true)
            ) {
                localSongs.firstOrNull { it.contentUri.toString() == src }?.let { return it }

                val targetFile = src.substringAfterLast('/').substringAfterLast('\\')
                    .substringBeforeLast('.', src).lowercase()
                if (targetFile.isNotEmpty()) {
                    localSongs.firstOrNull { song ->
                        val uriPath = song.contentUri.lastPathSegment ?: song.contentUri.toString()
                        val uriStem = uriPath.substringAfterLast('/').substringBeforeLast('.').lowercase()
                        uriStem == targetFile || normalizeForMatch(song.title) == normalizeForMatch(targetFile)
                    }?.let { return it }
                }
            }

            // 2. Normalized title + artist match
            val targetTitle = normalizeForMatch(entry.title)
            if (targetTitle.isEmpty()) return null
            val targetArtist = normalizeForMatch(entry.artist)

            return localSongs.firstOrNull { song ->
                val songTitle = normalizeForMatch(song.title)
                if (songTitle != targetTitle) return@firstOrNull false

                if (targetArtist.isEmpty()) {
                    true
                } else {
                    val songArtist = normalizeForMatch(song.artist)
                    songArtist == targetArtist ||
                        songArtist.contains(targetArtist) ||
                        targetArtist.contains(songArtist)
                }
            }
        }
    }
}
