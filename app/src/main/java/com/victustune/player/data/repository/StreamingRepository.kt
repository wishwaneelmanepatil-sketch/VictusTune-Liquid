package com.victustune.player.data.repository

import com.victustune.player.data.auth.AuthManager
import com.victustune.player.data.local.dao.SearchHistoryDao
import com.victustune.player.data.local.dao.SyncedPlaylistDao
import com.victustune.player.data.local.entity.SearchHistoryEntity
import com.victustune.player.data.local.entity.SyncedPlaylistEntity
import com.victustune.player.data.model.*
import com.victustune.player.data.remote.extractor.StreamResolver
import com.victustune.player.data.remote.innertube.InnerTubeClient
import com.victustune.player.data.remote.innertube.SearchFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StreamingRepository(
    val innerTubeClient: InnerTubeClient,
    val streamResolver: StreamResolver,
    private val searchHistoryDao: SearchHistoryDao,
    val authManager: AuthManager,
    private val syncedPlaylistDao: SyncedPlaylistDao
) {

    val authState: Flow<AuthState> = authManager.authState

    val syncedPlaylists: Flow<List<RemotePlaylist>> = syncedPlaylistDao.getSyncedPlaylists().map { list ->
        list.map {
            RemotePlaylist(
                browseId = it.browseId,
                title = it.title,
                author = it.author,
                thumbnailUrl = it.thumbnailUrl
            )
        }
    }

    val recentSearches: Flow<List<String>> = searchHistoryDao.getRecentSearches()

    suspend fun handleLoginSuccess(cookieString: String): Boolean {
        val saved = authManager.saveCredentials(cookieString)
        if (saved) {
            validateSession()
            syncUserPlaylists()
        }
        return saved
    }

    suspend fun validateSession(): Boolean {
        if (!authManager.isLoggedIn()) return false
        val profile = innerTubeClient.getAccountInfo()
        return if (profile != null) {
            authManager.saveUserProfile(profile)
            true
        } else {
            authManager.setSessionExpired()
            false
        }
    }

    suspend fun syncUserPlaylists(): List<RemotePlaylist> {
        if (!authManager.isLoggedIn()) return emptyList()

        return try {
            val (playlists, _) = innerTubeClient.getUserPlaylists()
            if (playlists.isNotEmpty()) {
                val entities = playlists.map { p ->
                    SyncedPlaylistEntity(
                        browseId = p.browseId,
                        title = p.title,
                        author = p.author,
                        thumbnailUrl = p.thumbnailUrl,
                        isLikedMusic = p.browseId == "VLLM" || p.browseId == "LM"
                    )
                }
                syncedPlaylistDao.insertPlaylists(entities)
            }
            playlists
        } catch (e: Exception) {
            // Check if failure is due to expired auth
            validateSession()
            emptyList()
        }
    }

    suspend fun getPlaylistTracks(
        playlistId: String,
        continuation: String? = null
    ): Pair<List<RemoteTrack>, String?> {
        return innerTubeClient.getPlaylistTracks(playlistId, continuation)
    }

    suspend fun signOut() {
        authManager.clearCredentials()
        syncedPlaylistDao.clearAll()
    }

    suspend fun saveSearchQuery(query: String) {
        if (query.isNotBlank()) {
            searchHistoryDao.insertSearch(SearchHistoryEntity(query.trim()))
        }
    }

    suspend fun deleteSearchQuery(query: String) {
        searchHistoryDao.deleteSearch(query)
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao.clearSearchHistory()
    }

    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.ALL,
        continuation: String? = null
    ): SearchResult {
        return innerTubeClient.search(query, filter, continuation)
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
        return innerTubeClient.getSearchSuggestions(query)
    }

    suspend fun getHomeFeed(): BrowseResult {
        return innerTubeClient.getHomeFeed()
    }

    suspend fun getExploreFeed(): BrowseResult {
        return innerTubeClient.browse("FEmusic_explore")
    }

    suspend fun browse(browseId: String, continuation: String? = null): BrowseResult {
        return innerTubeClient.browse(browseId, continuation)
    }

    suspend fun getNextQueue(videoId: String, playlistId: String? = null): List<RemoteTrack> {
        return innerTubeClient.next(videoId, playlistId)
    }

    suspend fun resolveStreamUrl(videoId: String, quality: AudioQuality = AudioQuality.AUTO): String {
        return streamResolver.resolveStreamUrl(videoId, quality)
    }

    fun invalidateStream(videoId: String) {
        streamResolver.invalidate(videoId)
    }
}
