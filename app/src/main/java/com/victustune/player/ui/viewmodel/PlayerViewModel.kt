package com.victustune.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.victustune.player.VictusTuneApp
import com.victustune.player.data.local.datastore.PreferencesManager
import com.victustune.player.data.local.datastore.SortOption
import com.victustune.player.data.model.*
import com.victustune.player.data.remote.innertube.SearchFilter
import com.victustune.player.data.repository.AudioRepository
import com.victustune.player.data.repository.LyricsRepository
import com.victustune.player.data.repository.PlaylistRepository
import com.victustune.player.data.repository.StreamingRepository
import com.victustune.player.playback.EqualizerController
import com.victustune.player.playback.MusicController
import com.victustune.player.playback.cache.MediaCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ExploreUiState {
    object Loading : ExploreUiState()
    data class Success(val result: BrowseResult) : ExploreUiState()
    data class Error(val message: String) : ExploreUiState()
}

sealed class OnlineSearchUiState {
    object Idle : OnlineSearchUiState()
    object Loading : OnlineSearchUiState()
    data class Success(val result: SearchResult) : OnlineSearchUiState()
    data class Error(val message: String) : OnlineSearchUiState()
}

class PlayerViewModel(
    private val audioRepository: AudioRepository,
    private val playlistRepository: PlaylistRepository,
    private val streamingRepository: StreamingRepository,
    private val lyricsRepository: LyricsRepository,
    private val musicController: MusicController,
    private val preferencesManager: PreferencesManager,
    val equalizerController: EqualizerController
) : ViewModel() {

    // Local library
    val songs: StateFlow<List<Song>> = audioRepository.songsFlow
    val albums: StateFlow<List<Album>> = audioRepository.albumsFlow
    val artists: StateFlow<List<Artist>> = audioRepository.artistsFlow

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteSongs: StateFlow<List<Song>> = playlistRepository.getFavoriteSongs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteSongIds: StateFlow<Set<Long>> = playlistRepository.getFavoriteSongIds()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val playbackState: StateFlow<PlaybackState> = musicController.playbackState
    val sleepTimerRemainingSeconds: StateFlow<Int?> = musicController.sleepTimerRemainingSeconds

    val sortOption: StateFlow<SortOption> = preferencesManager.sortOptionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortOption.TITLE)

    val audioQuality: StateFlow<AudioQuality> = preferencesManager.audioQualityFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AudioQuality.AUTO)

    val maxCacheSizeMb: StateFlow<Int> = preferencesManager.maxCacheSizeMbFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 512)

    private val _currentCacheBytes = MutableStateFlow(0L)
    val currentCacheBytes: StateFlow<Long> = _currentCacheBytes.asStateFlow()

    // Online Explore Feed
    private val _exploreState = MutableStateFlow<ExploreUiState>(ExploreUiState.Loading)
    val exploreState: StateFlow<ExploreUiState> = _exploreState.asStateFlow()

    // Online Search
    private val _onlineSearchState = MutableStateFlow<OnlineSearchUiState>(OnlineSearchUiState.Idle)
    val onlineSearchState: StateFlow<OnlineSearchUiState> = _onlineSearchState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = streamingRepository.recentSearches
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    init {
        // Load lyrics whenever current song changes
        viewModelScope.launch {
            playbackState
                .map { it.currentSong }
                .distinctUntilChanged()
                .collect { song ->
                    if (song != null) {
                        _lyrics.value = lyricsRepository.loadLyricsForSong(song)
                        playlistRepository.recordHistory(song.id)
                    } else {
                        _lyrics.value = emptyList()
                    }
                }
        }

        // Keep current playing track's favorite status in sync with favorite database in real-time
        viewModelScope.launch {
            combine(
                playbackState.map { it.currentSong?.id }.distinctUntilChanged(),
                favoriteSongIds
            ) { currentId, favIds ->
                if (currentId != null) favIds.contains(currentId) else false
            }.collect { isFav ->
                if (playbackState.value.currentSong?.isFavorite != isFav) {
                    musicController.updateCurrentSongFavorite(isFav)
                }
            }
        }

        // Initial scan & explore load
        refresh()
        loadExploreFeed()
        updateCacheSize()
    }

    fun refresh() {
        viewModelScope.launch {
            audioRepository.refreshMedia()
        }
    }

    fun loadExploreFeed() {
        viewModelScope.launch {
            _exploreState.value = ExploreUiState.Loading
            try {
                val feed = streamingRepository.getHomeFeed()
                _exploreState.value = ExploreUiState.Success(feed)
            } catch (e: Exception) {
                _exploreState.value = ExploreUiState.Error(e.message ?: "Failed to load Explore feed")
            }
        }
    }

    fun searchOnline(query: String, filter: SearchFilter = SearchFilter.ALL) {
        if (query.isBlank()) {
            _onlineSearchState.value = OnlineSearchUiState.Idle
            return
        }

        viewModelScope.launch {
            _onlineSearchState.value = OnlineSearchUiState.Loading
            streamingRepository.saveSearchQuery(query)
            try {
                val result = streamingRepository.search(query, filter)
                _onlineSearchState.value = OnlineSearchUiState.Success(result)
            } catch (e: Exception) {
                _onlineSearchState.value = OnlineSearchUiState.Error(e.message ?: "Online search failed")
            }
        }
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            streamingRepository.deleteSearchQuery(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            streamingRepository.clearSearchHistory()
        }
    }

    suspend fun loadBrowseDetail(browseId: String): BrowseResult {
        return streamingRepository.browse(browseId)
    }

    fun playRemoteTrack(track: RemoteTrack, autoRadio: Boolean = true) {
        val song = track.toSong()
        musicController.playSong(song, listOf(song))

        if (autoRadio) {
            viewModelScope.launch {
                try {
                    val nextTracks = streamingRepository.getNextQueue(track.videoId)
                    if (nextTracks.isNotEmpty()) {
                        val songs = nextTracks.map { it.toSong() }
                        // Append next tracks to queue
                        songs.forEach { s ->
                            if (s.id != song.id) {
                                musicController.addToQueue(s)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun playRemoteTracks(tracks: List<RemoteTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val songs = tracks.map { it.toSong() }
        musicController.playQueue(songs, startIndex)
    }

    fun playSong(song: Song, queue: List<Song> = songs.value) {
        musicController.playSong(song, queue)
    }

    fun playQueue(queue: List<Song>, startIndex: Int = 0) {
        musicController.playQueue(queue, startIndex)
    }

    fun togglePlayPause() = musicController.togglePlayPause()

    fun playNext() = musicController.playNext()

    fun playPrevious() = musicController.playPrevious()

    fun seekTo(positionMs: Long) = musicController.seekTo(positionMs)

    fun toggleShuffle() = musicController.toggleShuffle()

    fun cycleRepeatMode() = musicController.cycleRepeatMode()

    fun addToQueue(song: Song) = musicController.addToQueue(song)

    fun playSongNext(song: Song) = musicController.playSongNext(song)

    fun removeFromQueue(index: Int) = musicController.removeFromQueue(index)

    fun moveQueueItem(fromIndex: Int, toIndex: Int) = musicController.moveQueueItem(fromIndex, toIndex)

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            playlistRepository.toggleFavorite(song)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun renamePlaylist(id: Long, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(id, newName)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(id)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return playlistRepository.getSongsForPlaylist(playlistId)
    }

    fun setSortOption(option: SortOption) {
        viewModelScope.launch {
            preferencesManager.setSortOption(option)
        }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch {
            preferencesManager.setAudioQuality(quality)
        }
    }

    fun setMaxCacheSizeMb(sizeMb: Int) {
        viewModelScope.launch {
            preferencesManager.setMaxCacheSizeMb(sizeMb)
        }
    }

    fun updateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = MediaCacheManager.getCacheSizeBytes()
            _currentCacheBytes.value = bytes
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            MediaCacheManager.clearCache()
            updateCacheSize()
        }
    }

    fun setSleepTimer(minutes: Int) = musicController.setSleepTimer(minutes)

    fun cancelSleepTimer() = musicController.cancelSleepTimer()

    companion object {
        fun factory(app: VictusTuneApp): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val container = app.container
                return PlayerViewModel(
                    audioRepository = container.audioRepository,
                    playlistRepository = container.playlistRepository,
                    streamingRepository = container.streamingRepository,
                    lyricsRepository = container.lyricsRepository,
                    musicController = container.musicController,
                    preferencesManager = container.preferencesManager,
                    equalizerController = container.equalizerController
                ) as T
            }
        }
    }
}
