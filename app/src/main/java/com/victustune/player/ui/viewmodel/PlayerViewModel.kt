package com.victustune.player.ui.viewmodel

import android.net.Uri
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
import com.victustune.player.data.transfer.ExportFormat
import com.victustune.player.data.transfer.ImportProgress
import com.victustune.player.data.transfer.PlaylistExporter
import com.victustune.player.data.transfer.PlaylistImporter
import com.victustune.player.data.transfer.PlaylistTransferEntry
import com.victustune.player.playback.EqualizerController
import com.victustune.player.playback.MusicController
import com.victustune.player.playback.cache.MediaCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ExploreUiState {
    object Loading : ExploreUiState()
    data class Success(val result: BrowseResult) : ExploreUiState()
    data class Error(val message: String) : ExploreUiState()
}

sealed class ImportDialogState {
    object Idle : ImportDialogState()
    object Parsing : ImportDialogState()
    data class Preview(
        val defaultName: String,
        val format: ExportFormat,
        val entries: List<PlaylistTransferEntry>
    ) : ImportDialogState()
    data class Importing(val progress: ImportProgress) : ImportDialogState()
    data class Summary(val progress: ImportProgress) : ImportDialogState()
    data class Error(val message: String) : ImportDialogState()
}

sealed class ExportTarget(val displayName: String) {
    data class LocalPlaylist(val playlist: Playlist) : ExportTarget(playlist.name)
    object Favorites : ExportTarget("Favorite Songs")
    object Downloads : ExportTarget("Offline Downloads")
    data class RemotePlaylistTarget(val playlist: RemotePlaylist) : ExportTarget(playlist.title)
    data class CustomList(val name: String, val songs: List<Song>) : ExportTarget(name)
}

class PlayerViewModel(
    private val audioRepository: AudioRepository,
    private val playlistRepository: PlaylistRepository,
    private val streamingRepository: StreamingRepository,
    private val lyricsRepository: LyricsRepository,
    private val musicController: MusicController,
    private val preferencesManager: PreferencesManager,
    val equalizerController: EqualizerController,
    private val downloadRepository: com.victustune.player.data.repository.DownloadRepository,
    private val playlistImporter: PlaylistImporter,
    private val playlistExporter: PlaylistExporter
) : ViewModel() {

    private val _importDialogState = MutableStateFlow<ImportDialogState>(ImportDialogState.Idle)
    val importDialogState: StateFlow<ImportDialogState> = _importDialogState.asStateFlow()

    private val _transferMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val transferMessage: SharedFlow<String> = _transferMessage.asSharedFlow()

    private var importJob: Job? = null

    // Downloads
    val downloadedSongs: StateFlow<List<Song>> = downloadRepository.completedDownloadsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allDownloads: StateFlow<List<com.victustune.player.data.local.entity.DownloadedTrackEntity>> = downloadRepository.allDownloadsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val downloadStates: StateFlow<Map<String, com.victustune.player.data.download.DownloadStatus>> = downloadRepository.downloadStatesMap
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val downloadProgress: StateFlow<Map<String, Float>> = downloadRepository.downloadProgressMap
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val totalDownloadSizeBytes: StateFlow<Long> = downloadRepository.totalDownloadSizeBytes
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val downloadWifiOnly: StateFlow<Boolean> = downloadRepository.downloadWifiOnlyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val downloadQuality: StateFlow<String> = downloadRepository.downloadQualityFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "High")

    private val _downloadSearchQuery = MutableStateFlow("")
    val downloadSearchQuery: StateFlow<String> = _downloadSearchQuery.asStateFlow()

    val filteredDownloadedSongs: StateFlow<List<Song>> = combine(
        downloadedSongs,
        _downloadSearchQuery
    ) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<List<com.victustune.player.data.local.entity.DownloadedTrackEntity>> =
        allDownloads
            .map { list -> list.filter { it.status == "DOWNLOADING" || it.status == "QUEUED" } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val playbackState: StateFlow<PlaybackUiState> = musicController.playbackState
    val progressState: StateFlow<PlaybackProgressState> = musicController.progressState
    val sleepTimerRemainingSeconds: StateFlow<Int?> = musicController.sleepTimerRemainingSeconds

    val sortOption: StateFlow<SortOption> = preferencesManager.sortOptionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortOption.TITLE)

    val sortedSongs: StateFlow<List<Song>> = combine(songs, sortOption) { list, sort ->
        when (sort) {
            SortOption.TITLE -> list.sortedBy { it.title.lowercase() }
            SortOption.ARTIST -> list.sortedBy { it.artist.lowercase() }
            SortOption.DATE_ADDED -> list.sortedByDescending { it.dateAdded }
            SortOption.DURATION -> list.sortedByDescending { it.durationMs }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioQuality: StateFlow<AudioQuality> = preferencesManager.audioQualityFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AudioQuality.AUTO)

    val maxCacheSizeMb: StateFlow<Int> = preferencesManager.maxCacheSizeMbFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 512)

    private val _currentCacheBytes = MutableStateFlow(0L)
    val currentCacheBytes: StateFlow<Long> = _currentCacheBytes.asStateFlow()

    // Online Explore Feed
    private val _exploreState = MutableStateFlow<ExploreUiState>(ExploreUiState.Loading)
    val exploreState: StateFlow<ExploreUiState> = _exploreState.asStateFlow()

    // YouTube Music Account & Synced Playlists
    val authState: StateFlow<AuthState> = streamingRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.LoggedOut)

    val syncedPlaylists: StateFlow<List<RemotePlaylist>> = streamingRepository.syncedPlaylists
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isSyncingPlaylists = MutableStateFlow(false)
    val isSyncingPlaylists: StateFlow<Boolean> = _isSyncingPlaylists.asStateFlow()

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

        // Validate YouTube Music session on startup if logged in
        viewModelScope.launch {
            if (streamingRepository.authManager.isLoggedIn()) {
                streamingRepository.validateSession()
                streamingRepository.syncUserPlaylists()
            }
        }
    }

    fun handleLoginSuccess(cookieString: String) {
        viewModelScope.launch {
            _isSyncingPlaylists.value = true
            try {
                streamingRepository.handleLoginSuccess(cookieString)
            } finally {
                _isSyncingPlaylists.value = false
            }
        }
    }

    fun syncPlaylists() {
        viewModelScope.launch {
            _isSyncingPlaylists.value = true
            try {
                streamingRepository.syncUserPlaylists()
            } finally {
                _isSyncingPlaylists.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            streamingRepository.signOut()
        }
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

    fun playQueueIndex(index: Int) = musicController.playQueueIndex(index)

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

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
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

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            downloadRepository.downloadSong(song)
        }
    }

    fun downloadAll(songs: List<Song>) {
        viewModelScope.launch {
            downloadRepository.downloadAll(songs)
        }
    }

    fun removeDownload(videoId: String) {
        viewModelScope.launch {
            downloadRepository.removeDownload(videoId)
        }
    }

    fun removeAllDownloads() {
        viewModelScope.launch {
            downloadRepository.removeAllDownloads()
        }
    }

    fun pauseDownloads() = downloadRepository.pauseDownloads()

    fun resumeDownloads() = downloadRepository.resumeDownloads()

    fun setDownloadWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            downloadRepository.setWifiOnly(enabled)
        }
    }

    fun setDownloadQuality(quality: String) {
        viewModelScope.launch {
            downloadRepository.setDownloadQuality(quality)
        }
    }

    fun preparePlaylistImport(uri: Uri) {
        viewModelScope.launch {
            _importDialogState.value = ImportDialogState.Parsing
            try {
                val preview = playlistImporter.parseFromUri(uri)
                if (preview.entries.isEmpty()) {
                    _importDialogState.value = ImportDialogState.Error(
                        "No tracks were found in the selected file."
                    )
                } else {
                    _importDialogState.value = ImportDialogState.Preview(
                        defaultName = preview.defaultName,
                        format = preview.format,
                        entries = preview.entries
                    )
                }
            } catch (e: Exception) {
                _importDialogState.value = ImportDialogState.Error(
                    e.message ?: "Failed to read playlist file"
                )
            }
        }
    }

    fun startPlaylistImport(playlistName: String, matchOnline: Boolean) {
        val current = _importDialogState.value as? ImportDialogState.Preview ?: return
        importJob?.cancel()
        importJob = viewModelScope.launch {
            try {
                playlistImporter.importPlaylist(
                    playlistName = playlistName,
                    entries = current.entries,
                    matchOnline = matchOnline
                ).collect { progress ->
                    if (progress.isCompleted) {
                        _importDialogState.value = ImportDialogState.Summary(progress)
                    } else {
                        _importDialogState.value = ImportDialogState.Importing(progress)
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                _importDialogState.value = ImportDialogState.Idle
                _transferMessage.tryEmit("Playlist import cancelled")
            } catch (e: Exception) {
                _importDialogState.value = ImportDialogState.Error(
                    e.message ?: "Playlist import failed"
                )
            }
        }
    }

    fun cancelPlaylistImport() {
        importJob?.cancel()
        importJob = null
        _importDialogState.value = ImportDialogState.Idle
    }

    fun dismissImportDialog() {
        importJob?.cancel()
        importJob = null
        _importDialogState.value = ImportDialogState.Idle
    }

    fun exportPlaylist(target: ExportTarget, uri: Uri, format: ExportFormat) {
        viewModelScope.launch {
            val result = when (target) {
                is ExportTarget.LocalPlaylist -> playlistExporter.exportLocalPlaylist(target.playlist.id, uri, format)
                is ExportTarget.Favorites -> playlistExporter.exportFavorites(uri, format)
                is ExportTarget.Downloads -> playlistExporter.exportDownloads(uri, format)
                is ExportTarget.RemotePlaylistTarget -> playlistExporter.exportRemotePlaylist(target.playlist.browseId, uri, format)
                is ExportTarget.CustomList -> playlistExporter.exportSongs(target.songs, uri, format)
            }
            result.onSuccess { count ->
                _transferMessage.tryEmit("Playlist exported successfully ($count tracks)")
            }.onFailure { err ->
                _transferMessage.tryEmit("Export failed: ${err.message ?: "Unable to write file"}")
            }
        }
    }

    fun setAppInForeground(inForeground: Boolean) = musicController.setAppInForeground(inForeground)

    fun setDownloadSearchQuery(query: String) {
        _downloadSearchQuery.value = query
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
                    equalizerController = container.equalizerController,
                    downloadRepository = container.downloadRepository,
                    playlistImporter = container.playlistImporter,
                    playlistExporter = container.playlistExporter
                ) as T
            }
        }
    }
}
