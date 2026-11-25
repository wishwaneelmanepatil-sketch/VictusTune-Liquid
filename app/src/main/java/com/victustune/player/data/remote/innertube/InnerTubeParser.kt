package com.victustune.player.data.remote.innertube

import com.victustune.player.data.model.*
import kotlinx.serialization.json.*

object InnerTubeParser {

    fun parseSearch(json: JsonObject): SearchResult {
        val tracks = mutableListOf<RemoteTrack>()
        val albums = mutableListOf<RemoteAlbum>()
        val artists = mutableListOf<RemoteArtist>()
        val playlists = mutableListOf<RemotePlaylist>()
        var continuationToken: String? = null

        // Try to find continuation
        continuationToken = findContinuationToken(json)

        // Traverse all responsive list items and two row items
        findAllObjects(json, "musicResponsiveListItemRenderer") { item ->
            val track = parseTrackFromResponsiveItem(item)
            if (track != null) {
                tracks.add(track)
            } else {
                val album = parseAlbumFromResponsiveItem(item)
                if (album != null) albums.add(album)
                val artist = parseArtistFromResponsiveItem(item)
                if (artist != null) artists.add(artist)
                val playlist = parsePlaylistFromResponsiveItem(item)
                if (playlist != null) playlists.add(playlist)
            }
        }

        findAllObjects(json, "musicTwoRowItemRenderer") { item ->
            val browseId = item["navigationEndpoint"]?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull

            val videoId = item["navigationEndpoint"]?.jsonObject
                ?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull

            val title = extractText(item["title"])
            val subtitle = extractText(item["subtitle"])
            val thumb = extractThumbnail(item)

            if (videoId != null && title.isNotEmpty()) {
                tracks.add(
                    RemoteTrack(
                        videoId = videoId,
                        title = title,
                        artist = subtitle.ifEmpty { "Unknown Artist" },
                        thumbnailUrl = thumb
                    )
                )
            } else if (browseId != null) {
                when {
                    browseId.startsWith("MPREb_") || browseId.startsWith("FEmusic_library_corpus_track") -> {
                        albums.add(RemoteAlbum(browseId = browseId, title = title, artist = subtitle, thumbnailUrl = thumb))
                    }
                    browseId.startsWith("UC") || browseId.contains("artist") -> {
                        artists.add(RemoteArtist(browseId = browseId, name = title, thumbnailUrl = thumb))
                    }
                    browseId.startsWith("VL") || browseId.startsWith("PL") -> {
                        playlists.add(RemotePlaylist(browseId = browseId, title = title, author = subtitle, thumbnailUrl = thumb))
                    }
                    else -> {
                        if (subtitle.contains("Album", ignoreCase = true)) {
                            albums.add(RemoteAlbum(browseId = browseId, title = title, artist = subtitle, thumbnailUrl = thumb))
                        } else if (subtitle.contains("Artist", ignoreCase = true)) {
                            artists.add(RemoteArtist(browseId = browseId, name = title, thumbnailUrl = thumb))
                        } else {
                            playlists.add(RemotePlaylist(browseId = browseId, title = title, author = subtitle, thumbnailUrl = thumb))
                        }
                    }
                }
            }
        }

        return SearchResult(
            tracks = tracks.distinctBy { it.videoId },
            albums = albums.distinctBy { it.browseId },
            artists = artists.distinctBy { it.browseId },
            playlists = playlists.distinctBy { it.browseId },
            continuationToken = continuationToken
        )
    }

    fun parseBrowse(json: JsonObject): BrowseResult {
        var pageTitle = extractHeaderTitle(json)
        val headerThumb = extractHeaderThumbnail(json)
        val description = extractHeaderDescription(json)
        val sections = mutableListOf<BrowseSection>()
        val pageTracks = mutableListOf<RemoteTrack>()
        val continuationToken = findContinuationToken(json)

        // Parse section lists (Home / Explore feeds)
        findAllObjects(json, "musicCarouselShelfRenderer") { shelf ->
            val sectionTitle = extractText(shelf["header"]?.jsonObject?.get("musicCarouselShelfBasicHeaderRenderer")?.jsonObject?.get("title"))
            val sectionSubtitle = extractText(shelf["header"]?.jsonObject?.get("musicCarouselShelfBasicHeaderRenderer")?.jsonObject?.get("strapline"))
            val secTracks = mutableListOf<RemoteTrack>()
            val secAlbums = mutableListOf<RemoteAlbum>()
            val secArtists = mutableListOf<RemoteArtist>()
            val secPlaylists = mutableListOf<RemotePlaylist>()

            shelf["contents"]?.jsonArray?.forEach { contentElement ->
                val twoRow = contentElement.jsonObject["musicTwoRowItemRenderer"]?.jsonObject
                val responsive = contentElement.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject

                if (twoRow != null) {
                    val browseId = twoRow["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull
                    val videoId = twoRow["navigationEndpoint"]?.jsonObject?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
                    val title = extractText(twoRow["title"])
                    val subtitle = extractText(twoRow["subtitle"])
                    val thumb = extractThumbnail(twoRow)

                    if (videoId != null && title.isNotEmpty()) {
                        secTracks.add(RemoteTrack(videoId = videoId, title = title, artist = subtitle, thumbnailUrl = thumb))
                    } else if (browseId != null) {
                        if (browseId.startsWith("MPREb_") || subtitle.contains("Album", ignoreCase = true)) {
                            secAlbums.add(RemoteAlbum(browseId = browseId, title = title, artist = subtitle, thumbnailUrl = thumb))
                        } else if (browseId.startsWith("UC") || subtitle.contains("Artist", ignoreCase = true)) {
                            secArtists.add(RemoteArtist(browseId = browseId, name = title, thumbnailUrl = thumb))
                        } else {
                            secPlaylists.add(RemotePlaylist(browseId = browseId, title = title, author = subtitle, thumbnailUrl = thumb))
                        }
                    }
                } else if (responsive != null) {
                    val track = parseTrackFromResponsiveItem(responsive)
                    if (track != null) secTracks.add(track)
                }
            }

            if (secTracks.isNotEmpty() || secAlbums.isNotEmpty() || secArtists.isNotEmpty() || secPlaylists.isNotEmpty()) {
                sections.add(
                    BrowseSection(
                        title = sectionTitle.ifEmpty { "Featured" },
                        subtitle = sectionSubtitle.ifEmpty { null },
                        tracks = secTracks,
                        albums = secAlbums,
                        artists = secArtists,
                        playlists = secPlaylists
                    )
                )
            }
        }

        // Parse album/playlist/artist direct track list
        findAllObjects(json, "musicResponsiveListItemRenderer") { item ->
            val track = parseTrackFromResponsiveItem(item)
            if (track != null) {
                pageTracks.add(track)
            }
        }

        if (pageTitle.isEmpty()) {
            pageTitle = extractText(json["title"])
        }

        return BrowseResult(
            title = pageTitle.ifEmpty { null },
            headerThumbnailUrl = headerThumb,
            description = description.ifEmpty { null },
            sections = sections,
            tracks = pageTracks.distinctBy { it.videoId },
            continuationToken = continuationToken
        )
    }

    fun parseNext(json: JsonObject): List<RemoteTrack> {
        val tracks = mutableListOf<RemoteTrack>()
        findAllObjects(json, "playlistPanelVideoRenderer") { item ->
            val videoId = item["videoId"]?.jsonPrimitive?.contentOrNull
            val title = extractText(item["title"])
            val artist = extractText(item["longBylineText"]).ifEmpty { extractText(item["shortBylineText"]) }
            val durationText = extractText(item["lengthText"])
            val thumb = extractThumbnail(item)

            if (videoId != null && title.isNotEmpty()) {
                val durMs = parseDurationMs(durationText)
                tracks.add(
                    RemoteTrack(
                        videoId = videoId,
                        title = title,
                        artist = artist.ifEmpty { "Unknown Artist" },
                        durationMs = durMs,
                        durationText = durationText,
                        thumbnailUrl = thumb
                    )
                )
            }
        }
        return tracks.distinctBy { it.videoId }
    }

    private fun parseTrackFromResponsiveItem(item: JsonObject): RemoteTrack? {
        val flexColumns = item["flexColumns"]?.jsonArray ?: return null
        if (flexColumns.isEmpty()) return null

        val col0 = flexColumns.getOrNull(0)?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
        val title = extractText(col0?.get("text"))
        if (title.isEmpty()) return null

        val navEndpoint = col0?.get("text")?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("navigationEndpoint")?.jsonObject
            ?: item["navigationEndpoint"]?.jsonObject
            ?: item["overlay"]?.jsonObject?.get("musicItemThumbnailOverlayRenderer")?.jsonObject?.get("content")?.jsonObject?.get("musicPlayButtonRenderer")?.jsonObject?.get("playNavigationEndpoint")?.jsonObject

        val videoId = navEndpoint?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: item["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: return null

        val col1 = flexColumns.getOrNull(1)?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
        val col1Runs = col1?.get("text")?.jsonObject?.get("runs")?.jsonArray

        var artist = ""
        var album = ""
        var durationText = ""
        var channelId: String? = null

        col1Runs?.forEachIndexed { index, runElement ->
            val runObj = runElement.jsonObject
            val runText = runObj["text"]?.jsonPrimitive?.contentOrNull ?: ""
            if (runText == " • " || runText == "•") return@forEachIndexed

            val runBrowseId = runObj["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull

            if (runBrowseId != null && runBrowseId.startsWith("UC")) {
                artist = runText
                channelId = runBrowseId
            } else if (runBrowseId != null && runBrowseId.startsWith("MPREb_")) {
                album = runText
            } else if (runText.matches(Regex("\\d+:\\d+(:\\d+)?"))) {
                durationText = runText
            } else if (artist.isEmpty()) {
                artist = runText
            } else if (album.isEmpty() && !runText.contains("views", ignoreCase = true)) {
                album = runText
            }
        }

        // Additional columns might contain duration
        if (durationText.isEmpty() && flexColumns.size > 2) {
            for (i in 2 until flexColumns.size) {
                val txt = extractText(flexColumns[i].jsonObject["musicResponsiveListItemFlexColumnRenderer"]?.jsonObject?.get("text"))
                if (txt.matches(Regex("\\d+:\\d+(:\\d+)?"))) {
                    durationText = txt
                    break
                }
            }
        }

        val thumb = extractThumbnail(item)

        return RemoteTrack(
            videoId = videoId,
            title = title,
            artist = artist.ifEmpty { "Unknown Artist" },
            album = album.ifEmpty { null },
            durationMs = parseDurationMs(durationText),
            durationText = durationText.ifEmpty { null },
            thumbnailUrl = thumb,
            channelId = channelId
        )
    }

    private fun parseAlbumFromResponsiveItem(item: JsonObject): RemoteAlbum? {
        val flexColumns = item["flexColumns"]?.jsonArray ?: return null
        val col0 = flexColumns.getOrNull(0)?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject ?: return null
        val title = extractText(col0["text"])
        val browseId = item["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull
            ?: col0["text"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull
            ?: return null

        if (!browseId.startsWith("MPREb_") && !browseId.startsWith("FEmusic_library_corpus_track")) return null

        val col1 = flexColumns.getOrNull(1)?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
        val subtitle = extractText(col1?.get("text"))
        val thumb = extractThumbnail(item)

        return RemoteAlbum(
            browseId = browseId,
            title = title,
            artist = subtitle,
            thumbnailUrl = thumb
        )
    }

    private fun parseArtistFromResponsiveItem(item: JsonObject): RemoteArtist? {
        val flexColumns = item["flexColumns"]?.jsonArray ?: return null
        val col0 = flexColumns.getOrNull(0)?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject ?: return null
        val name = extractText(col0["text"])
        val browseId = item["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull
            ?: col0["text"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull
            ?: return null

        if (!browseId.startsWith("UC")) return null
        val thumb = extractThumbnail(item)

        return RemoteArtist(
            browseId = browseId,
            name = name,
            thumbnailUrl = thumb
        )
    }

    private fun parsePlaylistFromResponsiveItem(item: JsonObject): RemotePlaylist? {
        val flexColumns = item["flexColumns"]?.jsonArray ?: return null
        val col0 = flexColumns.getOrNull(0)?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject ?: return null
        val title = extractText(col0["text"])
        val browseId = item["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull
            ?: col0["text"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull
            ?: return null

        if (!browseId.startsWith("VL") && !browseId.startsWith("PL")) return null
        val col1 = flexColumns.getOrNull(1)?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
        val subtitle = extractText(col1?.get("text"))
        val thumb = extractThumbnail(item)

        return RemotePlaylist(
            browseId = browseId,
            title = title,
            author = subtitle,
            thumbnailUrl = thumb
        )
    }

    private fun extractHeaderTitle(json: JsonObject): String {
        val header = json["header"]?.jsonObject ?: return ""
        val detailHeader = header["musicDetailHeaderRenderer"]?.jsonObject
            ?: header["musicVisualHeaderRenderer"]?.jsonObject
            ?: header["musicImmersiveHeaderRenderer"]?.jsonObject
            ?: header["musicResponsiveHeaderRenderer"]?.jsonObject
            ?: return ""
        return extractText(detailHeader["title"])
    }

    private fun extractHeaderThumbnail(json: JsonObject): String? {
        val header = json["header"]?.jsonObject ?: return null
        val detailHeader = header["musicDetailHeaderRenderer"]?.jsonObject
            ?: header["musicVisualHeaderRenderer"]?.jsonObject
            ?: header["musicImmersiveHeaderRenderer"]?.jsonObject
            ?: header["musicResponsiveHeaderRenderer"]?.jsonObject
            ?: return null
        return extractThumbnail(detailHeader)
    }

    private fun extractHeaderDescription(json: JsonObject): String {
        val header = json["header"]?.jsonObject ?: return ""
        val detailHeader = header["musicDetailHeaderRenderer"]?.jsonObject
            ?: header["musicVisualHeaderRenderer"]?.jsonObject
            ?: header["musicResponsiveHeaderRenderer"]?.jsonObject
            ?: return ""
        return extractText(detailHeader["description"])
    }

    private fun extractText(element: JsonElement?): String {
        if (element == null) return ""
        if (element is JsonPrimitive && element.isString) return element.content

        if (element is JsonObject) {
            val simple = element["simpleText"]?.jsonPrimitive?.contentOrNull
            if (!simple.isNullOrEmpty()) return simple

            val runs = element["runs"]?.jsonArray
            if (runs != null && runs.isNotEmpty()) {
                return runs.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("")
            }
        }
        return ""
    }

    private fun extractThumbnail(item: JsonObject): String? {
        val thumbs = item["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
            ?: item["thumbnail"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
            ?: item["thumbnailRenderer"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
            ?: item["thumbnails"]?.jsonArray

        val best = thumbs?.maxByOrNull {
            it.jsonObject["width"]?.jsonPrimitive?.intOrNull ?: 0
        }?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            ?: thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            ?: thumbs?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        return best?.let { ThumbnailUtils.upgrade(cleanThumbnailUrl(it)) }
    }

    private fun cleanThumbnailUrl(url: String): String {
        return if (url.startsWith("//")) "https:$url" else url
    }

    private fun findContinuationToken(json: JsonObject): String? {
        var token: String? = null
        findAllObjects(json, "continuationEndpoint") { endpoint ->
            token = endpoint["continuationCommand"]?.jsonObject?.get("token")?.jsonPrimitive?.contentOrNull
        }
        if (token != null) return token

        findAllObjects(json, "nextContinuationData") { contData ->
            token = contData["continuation"]?.jsonPrimitive?.contentOrNull
        }
        return token
    }

    private fun findAllObjects(element: JsonElement, targetKey: String, action: (JsonObject) -> Unit) {
        when (element) {
            is JsonObject -> {
                if (element.containsKey(targetKey)) {
                    val target = element[targetKey]
                    if (target is JsonObject) {
                        action(target)
                    }
                }
                for (entry in element.entries) {
                    findAllObjects(entry.value, targetKey, action)
                }
            }
            is JsonArray -> {
                for (item in element) {
                    findAllObjects(item, targetKey, action)
                }
            }
            else -> {}
        }
    }

    fun parseDurationMs(durationStr: String): Long {
        if (durationStr.isEmpty()) return 0L
        val parts = durationStr.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            else -> 0L
        }
    }

    fun parseAccountInfo(json: JsonObject): com.victustune.player.data.model.UserProfile? {
        var profile: com.victustune.player.data.model.UserProfile? = null
        findAllObjects(json, "activeAccountHeaderRenderer") { header ->
            val name = extractText(header["accountName"])
            val email = extractText(header["email"]).ifEmpty { extractText(header["channelHandle"]) }
            val avatar = extractThumbnail(header)
            if (name.isNotEmpty()) {
                profile = com.victustune.player.data.model.UserProfile(
                    name = name,
                    handle = email,
                    avatarUrl = avatar
                )
            }
        }
        return profile
    }

    fun parseUserPlaylists(json: JsonObject): List<RemotePlaylist> {
        val playlists = mutableListOf<RemotePlaylist>()

        findAllObjects(json, "musicTwoRowItemRenderer") { item ->
            val browseId = item["navigationEndpoint"]?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull

            val title = extractText(item["title"])
            val subtitle = extractText(item["subtitle"])
            val thumb = extractThumbnail(item)

            if (browseId != null && title.isNotEmpty()) {
                playlists.add(
                    RemotePlaylist(
                        browseId = browseId,
                        title = title,
                        author = subtitle.ifEmpty { "YouTube Music" },
                        thumbnailUrl = thumb
                    )
                )
            }
        }

        findAllObjects(json, "musicResponsiveListItemRenderer") { item ->
            val playlist = parsePlaylistFromResponsiveItem(item)
            if (playlist != null) {
                playlists.add(playlist)
            }
        }

        return playlists.distinctBy { it.browseId }
    }
}
