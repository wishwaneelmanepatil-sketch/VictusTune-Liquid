package com.victustune.player.data.remote.innertube

import com.victustune.player.data.model.BrowseResult
import com.victustune.player.data.model.RemoteTrack
import com.victustune.player.data.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

enum class SearchFilter(val param: String?) {
    ALL(null),
    SONGS("Eg-KAQwIARAUGAMqAggB"),
    ALBUMS("Eg-KAQwIBAAUGAMqAggB"),
    ARTISTS("Eg-KAQwIAgAUGAMqAggB"),
    PLAYLISTS("Eg-KAQwIBBAUGAMqAggB")
}

class InnerTubeClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    var authManager: com.victustune.player.data.auth.AuthManager? = null
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseUrl = "https://music.youtube.com/youtubei/v1"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var apiKey: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    @Volatile
    private var clientVersion: String = "1.20261122.17.00"

    private val refreshMutex = Mutex()
    private val apiKeyPattern = Pattern.compile("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
    private val clientVersionPattern = Pattern.compile("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"")

    private fun buildContext(): JsonObject {
        return buildJsonObject {
            putJsonObject("client") {
                put("clientName", "WEB_REMIX")
                put("clientVersion", clientVersion)
                put("hl", "en")
                put("gl", "US")
            }
        }
    }

    private fun createBaseRequestBuilder(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("X-Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", clientVersion)

        val auth = authManager
        val cookie = auth?.getCookie()
        val sapisid = auth?.getSapisid()
        if (!cookie.isNullOrEmpty() && !sapisid.isNullOrEmpty()) {
            builder.header("Cookie", cookie)
            builder.header("Authorization", com.victustune.player.data.auth.AuthManager.generateSapisidHash(sapisid))
        }

        return builder
    }

    private suspend fun refreshKeyIfNeeded() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            try {
                val req = Request.Builder()
                    .url("https://music.youtube.com")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""

                val keyMatcher = apiKeyPattern.matcher(body)
                if (keyMatcher.find()) {
                    apiKey = keyMatcher.group(1) ?: apiKey
                }

                val verMatcher = clientVersionPattern.matcher(body)
                if (verMatcher.find()) {
                    clientVersion = verMatcher.group(1) ?: clientVersion
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.ALL,
        continuation: String? = null
    ): SearchResult = withContext(Dispatchers.IO) {
        try {
            executeSearch(query, filter, continuation)
        } catch (e: IOException) {
            // If 404 or request failed, attempt key refresh and retry once
            refreshKeyIfNeeded()
            executeSearch(query, filter, continuation)
        }
    }

    private fun executeSearch(
        query: String,
        filter: SearchFilter,
        continuation: String?
    ): SearchResult {
        val url = if (continuation != null) {
            "$baseUrl/search?key=$apiKey&continuation=$continuation&ctoken=$continuation"
        } else {
            "$baseUrl/search?key=$apiKey"
        }

        val requestBodyJson = buildJsonObject {
            put("context", buildContext())
            if (continuation == null) {
                put("query", query)
                if (filter.param != null) {
                    put("params", filter.param)
                }
            }
        }

        val request = createBaseRequestBuilder(url)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("InnerTube search failed with HTTP ${response.code}")
        }

        val bodyString = response.body?.string() ?: "{}"
        val parsedJson = json.parseToJsonElement(bodyString).jsonObject
        return InnerTubeParser.parseSearch(parsedJson)
    }

    suspend fun browse(
        browseId: String,
        continuation: String? = null
    ): BrowseResult = withContext(Dispatchers.IO) {
        try {
            executeBrowse(browseId, continuation)
        } catch (e: IOException) {
            // If 404 or request failed, attempt key refresh and retry once
            refreshKeyIfNeeded()
            executeBrowse(browseId, continuation)
        }
    }

    private fun executeBrowse(
        browseId: String,
        continuation: String?
    ): BrowseResult {
        val url = if (continuation != null) {
            "$baseUrl/browse?key=$apiKey&continuation=$continuation&ctoken=$continuation"
        } else {
            "$baseUrl/browse?key=$apiKey"
        }

        val requestBodyJson = buildJsonObject {
            put("context", buildContext())
            if (continuation == null) {
                put("browseId", browseId)
            }
        }

        val request = createBaseRequestBuilder(url)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("InnerTube browse failed with HTTP ${response.code}")
        }

        val bodyString = response.body?.string() ?: "{}"
        val parsedJson = json.parseToJsonElement(bodyString).jsonObject
        return InnerTubeParser.parseBrowse(parsedJson)
    }

    suspend fun getHomeFeed(): BrowseResult {
        return try {
            val home = browse("FEmusic_home")
            if (home.sections.isEmpty() && home.tracks.isEmpty()) {
                browse("FEmusic_explore")
            } else {
                home
            }
        } catch (e: Exception) {
            browse("FEmusic_explore")
        }
    }

    suspend fun next(
        videoId: String,
        playlistId: String? = null
    ): List<RemoteTrack> = withContext(Dispatchers.IO) {
        try {
            executeNext(videoId, playlistId)
        } catch (e: IOException) {
            refreshKeyIfNeeded()
            executeNext(videoId, playlistId)
        }
    }

    private fun executeNext(
        videoId: String,
        playlistId: String?
    ): List<RemoteTrack> {
        val url = "$baseUrl/next?key=$apiKey"
        val requestBodyJson = buildJsonObject {
            put("context", buildContext())
            put("videoId", videoId)
            if (playlistId != null) {
                put("playlistId", playlistId)
            }
        }

        val request = createBaseRequestBuilder(url)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("InnerTube next failed with HTTP ${response.code}")
        }

        val bodyString = response.body?.string() ?: "{}"
        val parsedJson = json.parseToJsonElement(bodyString).jsonObject
        return InnerTubeParser.parseNext(parsedJson)
    }

    suspend fun getAccountInfo(): com.victustune.player.data.model.UserProfile? = withContext(Dispatchers.IO) {
        val auth = authManager
        if (auth?.getCookie().isNullOrEmpty() || auth?.getSapisid().isNullOrEmpty()) {
            return@withContext null
        }

        try {
            val url = "$baseUrl/account/account_menu?key=$apiKey"
            val requestBodyJson = buildJsonObject {
                put("context", buildContext())
            }

            val request = createBaseRequestBuilder(url)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext null
            }

            val bodyString = response.body?.string() ?: "{}"
            val parsedJson = json.parseToJsonElement(bodyString).jsonObject
            InnerTubeParser.parseAccountInfo(parsedJson)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserPlaylists(
        continuation: String? = null
    ): Pair<List<com.victustune.player.data.model.RemotePlaylist>, String?> = withContext(Dispatchers.IO) {
        try {
            executeUserPlaylists(continuation)
        } catch (e: IOException) {
            refreshKeyIfNeeded()
            executeUserPlaylists(continuation)
        }
    }

    private fun executeUserPlaylists(
        continuation: String?
    ): Pair<List<com.victustune.player.data.model.RemotePlaylist>, String?> {
        val url = if (continuation != null) {
            "$baseUrl/browse?key=$apiKey&continuation=$continuation&ctoken=$continuation"
        } else {
            "$baseUrl/browse?key=$apiKey"
        }

        val requestBodyJson = buildJsonObject {
            put("context", buildContext())
            if (continuation == null) {
                put("browseId", "FEmusic_liked_playlists")
            }
        }

        val request = createBaseRequestBuilder(url)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("InnerTube user playlists browse failed with HTTP ${response.code}")
        }

        val bodyString = response.body?.string() ?: "{}"
        val parsedJson = json.parseToJsonElement(bodyString).jsonObject
        val playlists = InnerTubeParser.parseUserPlaylists(parsedJson).toMutableList()

        // Ensure Liked Music is accessible if on the first page
        if (continuation == null && playlists.none { it.browseId == "VLLM" || it.browseId == "LM" }) {
            playlists.add(
                0,
                com.victustune.player.data.model.RemotePlaylist(
                    browseId = "VLLM",
                    title = "Liked Music",
                    author = "Auto playlist",
                    thumbnailUrl = null
                )
            )
        }

        val nextContinuation = findContinuationToken(parsedJson)
        return Pair(playlists, nextContinuation)
    }

    private fun findContinuationToken(json: JsonObject): String? {
        var token: String? = null
        fun findToken(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    if (element.containsKey("continuationCommand")) {
                        token = element["continuationCommand"]?.jsonObject?.get("token")?.jsonPrimitive?.contentOrNull
                        if (token != null) return
                    }
                    if (element.containsKey("nextContinuationData")) {
                        token = element["nextContinuationData"]?.jsonObject?.get("continuation")?.jsonPrimitive?.contentOrNull
                        if (token != null) return
                    }
                    for (entry in element.entries) {
                        findToken(entry.value)
                        if (token != null) return
                    }
                }
                is JsonArray -> {
                    for (item in element) {
                        findToken(item)
                        if (token != null) return
                    }
                }
                else -> {}
            }
        }
        findToken(json)
        return token
    }

    suspend fun getPlaylistTracks(
        playlistId: String,
        continuation: String? = null
    ): Pair<List<RemoteTrack>, String?> = withContext(Dispatchers.IO) {
        val browseId = when {
            playlistId.startsWith("VL") -> playlistId
            playlistId == "LM" -> "VLLM"
            else -> "VL$playlistId"
        }
        val result = browse(browseId, continuation)
        Pair(result.tracks, result.continuationToken)
    }
}
