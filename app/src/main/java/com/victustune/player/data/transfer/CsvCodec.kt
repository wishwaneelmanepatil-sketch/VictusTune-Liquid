package com.victustune.player.data.transfer

import com.victustune.player.data.model.Song

object CsvCodec {

    private val TITLE_HEADERS = setOf("track name", "title", "song", "name", "track")
    private val ARTIST_HEADERS = setOf("artist name(s)", "artist", "artists", "artist name")
    private val ALBUM_HEADERS = setOf("album name", "album")
    private val DURATION_MS_HEADERS = setOf("duration (ms)", "durationms", "duration_ms")
    private val DURATION_GENERIC_HEADERS = setOf("duration", "length", "duration (s)")
    private val SOURCE_HEADERS = setOf(
        "source", "sourceref", "source_ref", "url", "videoid", "video_id", "uri", "track uri", "path"
    )
    private val SOURCE_TYPE_HEADERS = setOf("sourcetype", "source_type")
    private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    fun parse(content: String): List<PlaylistTransferEntry> {
        val records = parseRecords(content.removePrefix("\uFEFF"))
            .filter { row -> row.any { it.isNotBlank() } }
        if (records.isEmpty()) return emptyList()

        val headerRow = records.first()
        val normalizedHeaders = headerRow.map { it.trim().lowercase() }

        val titleIdx = normalizedHeaders.indexOfFirst { it in TITLE_HEADERS }
        val artistIdx = normalizedHeaders.indexOfFirst { it in ARTIST_HEADERS }
        val albumIdx = normalizedHeaders.indexOfFirst { it in ALBUM_HEADERS }
        val durationMsIdx = normalizedHeaders.indexOfFirst { it in DURATION_MS_HEADERS }
        val durationGenericIdx = normalizedHeaders.indexOfFirst { it in DURATION_GENERIC_HEADERS }
        val sourceIdx = normalizedHeaders.indexOfFirst { it in SOURCE_HEADERS }
        val sourceTypeIdx = normalizedHeaders.indexOfFirst { it in SOURCE_TYPE_HEADERS }

        val hasHeader = titleIdx >= 0 || artistIdx >= 0 || sourceIdx >= 0
        val effectiveTitleIdx = if (hasHeader) titleIdx else 0
        val effectiveArtistIdx = if (hasHeader) artistIdx else if (headerRow.size > 1) 1 else -1
        val dataRows = if (hasHeader) records.drop(1) else records

        val entries = mutableListOf<PlaylistTransferEntry>()
        for (row in dataRows) {
            val title = row.getOrNull(effectiveTitleIdx)?.trim().orEmpty()
            val artist = row.getOrNull(effectiveArtistIdx)?.trim().orEmpty()
            val album = row.getOrNull(albumIdx)?.trim().orEmpty()

            val durationMs = when {
                durationMsIdx >= 0 -> parseDurationToMs(row.getOrNull(durationMsIdx), assumeMs = true)
                durationGenericIdx >= 0 -> parseDurationToMs(row.getOrNull(durationGenericIdx), assumeMs = false)
                else -> 0L
            }

            val rawSource = row.getOrNull(sourceIdx)?.trim()?.takeIf { it.isNotEmpty() }
            val sourceType = row.getOrNull(sourceTypeIdx)?.trim().orEmpty()
            val sourceHeaderName = if (sourceIdx >= 0) normalizedHeaders[sourceIdx] else ""

            val normalizedSource = if (
                rawSource != null &&
                VIDEO_ID_REGEX.matches(rawSource) &&
                (sourceType.equals("Remote", ignoreCase = true) ||
                    sourceHeaderName == "videoid" ||
                    sourceHeaderName == "video_id")
            ) {
                "https://music.youtube.com/watch?v=$rawSource"
            } else {
                rawSource
            }

            if (title.isEmpty() && normalizedSource.isNullOrEmpty()) {
                continue
            }

            entries.add(
                PlaylistTransferEntry(
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    sourcePathOrUrl = normalizedSource
                )
            )
        }
        return entries
    }

    fun encode(songs: List<Song>, includeBom: Boolean = true): String {
        val rows = songs.map { song ->
            val isRemote = song.isRemote
            val sourceRef = if (isRemote) {
                val vid = song.remoteVideoId ?: song.id.toString()
                "https://music.youtube.com/watch?v=$vid"
            } else {
                song.contentUri.toString()
            }
            PlaylistTransferEntry(
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                sourcePathOrUrl = sourceRef
            ) to isRemote
        }
        return encodeEntriesWithType(rows, includeBom)
    }

    fun encodeEntries(entries: List<PlaylistTransferEntry>, includeBom: Boolean = true): String {
        val rows = entries.map { entry ->
            val src = entry.sourcePathOrUrl.orEmpty()
            val isRemote = src.startsWith("http://", ignoreCase = true) ||
                src.startsWith("https://", ignoreCase = true) ||
                src.startsWith("stream://", ignoreCase = true)
            entry to isRemote
        }
        return encodeEntriesWithType(rows, includeBom)
    }

    private fun encodeEntriesWithType(
        entries: List<Pair<PlaylistTransferEntry, Boolean>>,
        includeBom: Boolean
    ): String {
        val sb = StringBuilder()
        if (includeBom) {
            sb.append('\uFEFF')
        }
        sb.appendLine("Title,Artist,Album,DurationMs,SourceType,SourceRef")
        for ((entry, isRemote) in entries) {
            val sourceType = if (isRemote) "Remote" else "Local"
            val row = listOf(
                escapeCsvField(entry.title),
                escapeCsvField(entry.artist),
                escapeCsvField(entry.album),
                entry.durationMs.toString(),
                sourceType,
                escapeCsvField(entry.sourcePathOrUrl.orEmpty())
            ).joinToString(",")
            sb.appendLine(row)
        }
        return sb.toString()
    }

    internal fun escapeCsvField(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuoting) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    internal fun parseRecords(content: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = content.length

        while (i < len) {
            val c = content[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && content[i + 1] == '"') {
                        currentField.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> {
                        if (currentField.isEmpty()) {
                            inQuotes = true
                        } else {
                            currentField.append(c)
                        }
                    }
                    ',' -> {
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < len && content[i + 1] == '\n') {
                            i++
                        }
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                        records.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                        records.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    else -> currentField.append(c)
                }
            }
            i++
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            records.add(currentRow)
        }

        return records
    }

    private fun parseDurationToMs(raw: String?, assumeMs: Boolean): Long {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return 0L

        if (trimmed.contains(':')) {
            val parts = trimmed.split(':').mapNotNull { it.toLongOrNull() }
            return when (parts.size) {
                2 -> (parts[0] * 60 + parts[1]) * 1000L
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
                else -> 0L
            }
        }

        val numeric = trimmed.toLongOrNull() ?: return 0L
        if (numeric <= 0L) return 0L
        return if (assumeMs || numeric >= 10_000L) numeric else numeric * 1000L
    }
}
