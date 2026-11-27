package com.victustune.player.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.victustune.player.VictusTuneApp
import com.victustune.player.data.model.SearchResult
import com.victustune.player.data.model.Song
import com.victustune.player.data.remote.innertube.SearchFilter
import com.victustune.player.data.repository.AudioRepository
import com.victustune.player.data.repository.DownloadRepository
import com.victustune.player.data.repository.PlaylistRepository
import com.victustune.player.data.repository.StreamingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.UnknownHostException

enum class SearchCategory(val title: String, val filter: SearchFilter?) {
    SONGS("Songs", SearchFilter.SONGS),
    ALBUMS("Albums", SearchFilter.ALBUMS),
    ARTISTS("Artists", SearchFilter.ARTISTS),
    PLAYLISTS("Playlists", SearchFilter.PLAYLISTS),
    LOCAL("Local", null)
}

sealed class OnlineSearchUiState {
    object Idle : OnlineSearchUiState()
    object Loading : OnlineSearchUiState()
    data class Success(val result: SearchResult) : OnlineSearchUiState()
    data class Error(val message: String) : OnlineSearchUiState()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val applicationContext: Context,
    private val audioRepository: AudioRepository,
    private val downloadRepository: DownloadRepository,
    private val playlistRepository: PlaylistRepository,
    private val streamingRepository: StreamingRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Default priority: Online Songs (InnerTube)
    private val _selectedCategory = MutableStateFlow(SearchCategory.SONGS)
    val selectedCategory: StateFlow<SearchCategory> = _selectedCategory.asStateFlow()

    private val _onlineSearchState = MutableStateFlow<OnlineSearchUiState>(OnlineSearchUiState.Idle)
    val onlineSearchState: StateFlow<OnlineSearchUiState> = _onlineSearchState.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _isOfflineFallback = MutableStateFlow(false)
    val isOfflineFallback: StateFlow<Boolean> = _isOfflineFallback.asStateFlow()

    val recentSearches: StateFlow<List<String>> = streamingRepository.recentSearches
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Unified local MediaStore + completed offline downloads for Local filter and Offline Fallback
    val localAndDownloadedSongs: StateFlow<List<Song>> = combine(
        audioRepository.songsFlow,
        downloadRepository.completedDownloadsFlow,
        playlistRepository.getFavoriteSongIds()
    ) { localSongs, downloadedSongs, favIds ->
        val localIds = localSongs.map { it.id }.toSet()
        val uniqueDownloaded = downloadedSongs.filter { !localIds.contains(it.id) }
        (localSongs + uniqueDownloaded).map { song ->
            if (favIds.contains(song.id) && !song.isFavorite) {
                song.copy(isFavorite = true)
            } else {
                song
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredLocalSongs: StateFlow<List<Song>> = combine(
        _searchQuery,
        localAndDownloadedSongs
    ) { rawQuery, songs ->
        val query = rawQuery.trim().lowercase()
        if (query.isEmpty()) {
            emptyList()
        } else {
            songs.filter { song ->
                song.title.lowercase().contains(query) ||
                    song.artist.lowercase().contains(query) ||
                    song.album.lowercase().contains(query)
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class OnlineQueryRequest(
        val query: String,
        val category: SearchCategory,
        val fetchSuggestions: Boolean,
        val nonce: Int = 0
    )

    private val onlineRequestFlow = MutableStateFlow<OnlineQueryRequest?>(null)
    private var requestNonce = 0

    init {
        // Debounced (~300ms) input triggers onlineRequestFlow; flatMapLatest cancels stale searches automatically
        viewModelScope.launch {
            _searchQuery
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(300L)
                .collect { query ->
                    val category = _selectedCategory.value
                    if (query.isEmpty() || category == SearchCategory.LOCAL) {
                        onlineRequestFlow.value = null
                    } else if (onlineRequestFlow.value?.let { it.query == query && it.category == category } != true) {
                        onlineRequestFlow.value = OnlineQueryRequest(query, category, fetchSuggestions = true, nonce = ++requestNonce)
                    }
                }
        }

        onlineRequestFlow
            .flatMapLatest { request ->
                if (request == null) {
                    _searchSuggestions.value = emptyList()
                    _isOfflineFallback.value = false
                    _onlineSearchState.value = OnlineSearchUiState.Idle
                    emptyFlow()
                } else {
                    flow {
                        if (!isNetworkAvailable()) {
                            _searchSuggestions.value = emptyList()
                            _isOfflineFallback.value = true
                            _onlineSearchState.value = OnlineSearchUiState.Idle
                            emit(Unit)
                            return@flow
                        }

                        _isOfflineFallback.value = false
                        _onlineSearchState.value = OnlineSearchUiState.Loading

                        coroutineScope {
                            if (request.fetchSuggestions) {
                                launch {
                                    val items = try {
                                        streamingRepository.getSearchSuggestions(request.query)
                                    } catch (ce: CancellationException) {
                                        throw ce
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                    if (_searchQuery.value.trim() == request.query &&
                                        _selectedCategory.value != SearchCategory.LOCAL
                                    ) {
                                        _searchSuggestions.value = items.filter { !it.equals(request.query, ignoreCase = true) }
                                    }
                                }
                            }

                            try {
                                val filter = request.category.filter ?: SearchFilter.ALL
                                val result = streamingRepository.search(request.query, filter)
                                _onlineSearchState.value = OnlineSearchUiState.Success(result)
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                if (!isNetworkAvailable() || e is UnknownHostException || e is ConnectException) {
                                    _searchSuggestions.value = emptyList()
                                    _isOfflineFallback.value = true
                                    _onlineSearchState.value = OnlineSearchUiState.Idle
                                } else {
                                    _onlineSearchState.value = OnlineSearchUiState.Error(
                                        e.message ?: "Online search timed out or failed"
                                    )
                                }
                            }
                        }
                        emit(Unit)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun startNewSearchSession() {
        onlineRequestFlow.value = null
        _selectedCategory.value = SearchCategory.SONGS
        _searchQuery.value = ""
        _searchSuggestions.value = emptyList()
        _isOfflineFallback.value = false
        _onlineSearchState.value = OnlineSearchUiState.Idle
    }

    fun onQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            onlineRequestFlow.value = null
            _searchSuggestions.value = emptyList()
            _isOfflineFallback.value = false
            _onlineSearchState.value = OnlineSearchUiState.Idle
        }
    }

    fun onCategorySelected(category: SearchCategory) {
        _selectedCategory.value = category
        val query = _searchQuery.value.trim()
        if (query.isEmpty() || category == SearchCategory.LOCAL) {
            onlineRequestFlow.value = null
            if (category == SearchCategory.LOCAL) {
                _searchSuggestions.value = emptyList()
                _isOfflineFallback.value = false
                _onlineSearchState.value = OnlineSearchUiState.Idle
            }
        } else {
            onlineRequestFlow.value = OnlineQueryRequest(query, category, fetchSuggestions = false, nonce = ++requestNonce)
        }
    }

    fun submitSearch(overrideQuery: String? = null) {
        val raw = overrideQuery ?: _searchQuery.value
        val trimmed = raw.trim()
        if (overrideQuery != null && _searchQuery.value != trimmed) {
            _searchQuery.value = trimmed
        }
        if (trimmed.isEmpty()) return

        saveQueryToHistory(trimmed)

        val category = _selectedCategory.value
        if (category == SearchCategory.LOCAL) {
            onlineRequestFlow.value = null
            _isOfflineFallback.value = false
            _searchSuggestions.value = emptyList()
            _onlineSearchState.value = OnlineSearchUiState.Idle
        } else {
            onlineRequestFlow.value = OnlineQueryRequest(trimmed, category, fetchSuggestions = true, nonce = ++requestNonce)
        }
    }

    fun retrySearch() {
        val query = _searchQuery.value.trim()
        val category = _selectedCategory.value
        if (query.isNotEmpty() && category != SearchCategory.LOCAL) {
            onlineRequestFlow.value = OnlineQueryRequest(query, category, fetchSuggestions = true, nonce = ++requestNonce)
        }
    }

    fun recordInteractionSearchHistory() {
        val query = _searchQuery.value.trim()
        if (query.isNotEmpty()) {
            saveQueryToHistory(query)
        }
    }

    fun clearQuery() {
        onlineRequestFlow.value = null
        _searchQuery.value = ""
        _searchSuggestions.value = emptyList()
        _isOfflineFallback.value = false
        _onlineSearchState.value = OnlineSearchUiState.Idle
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

    private fun saveQueryToHistory(query: String) {
        viewModelScope.launch {
            streamingRepository.saveSearchQuery(query)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        fun factory(app: VictusTuneApp): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val container = app.container
                return SearchViewModel(
                    applicationContext = app.applicationContext,
                    audioRepository = container.audioRepository,
                    downloadRepository = container.downloadRepository,
                    playlistRepository = container.playlistRepository,
                    streamingRepository = container.streamingRepository
                ) as T
            }
        }
    }
}
