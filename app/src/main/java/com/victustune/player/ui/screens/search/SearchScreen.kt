package com.victustune.player.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.victustune.player.data.download.DownloadStatus
import com.victustune.player.data.model.*
import com.victustune.player.ui.components.TrackItem
import com.victustune.player.ui.theme.*
import com.victustune.player.ui.viewmodel.OnlineSearchUiState
import com.victustune.player.ui.viewmodel.SearchCategory
import com.victustune.player.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    currentPlayingSongId: Long?,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onRemoteTrackClick: (RemoteTrack) -> Unit,
    onRemoteAlbumClick: (RemoteAlbum) -> Unit,
    onRemoteArtistClick: (RemoteArtist) -> Unit,
    onRemotePlaylistClick: (RemotePlaylist) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (String) -> Unit,
    onQueryActiveChange: (Boolean) -> Unit = {},
    isOverlayActive: Boolean = false,
    downloadStates: Map<String, DownloadStatus> = emptyMap(),
    downloadProgress: Map<String, Float> = emptyMap(),
    onToggleDownload: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val searchQuery by searchViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by searchViewModel.selectedCategory.collectAsStateWithLifecycle()
    val onlineSearchState by searchViewModel.onlineSearchState.collectAsStateWithLifecycle()
    val searchSuggestions by searchViewModel.searchSuggestions.collectAsStateWithLifecycle()
    val isOfflineFallback by searchViewModel.isOfflineFallback.collectAsStateWithLifecycle()
    val filteredLocalSongs by searchViewModel.filteredLocalSongs.collectAsStateWithLifecycle()
    val recentSearches by searchViewModel.recentSearches.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current

    LaunchedEffect(searchQuery) {
        onQueryActiveChange(searchQuery.isNotEmpty())
    }

    // Priority 3: Clear active search query and dismiss keyboard before closing search screen
    BackHandler(enabled = searchQuery.isNotEmpty() && !isOverlayActive) {
        searchViewModel.clearQuery()
        focusManager.clearFocus()
        onQueryActiveChange(false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchViewModel.onQueryChanged(it) },
                placeholder = { Text("Search YouTube Music or local...", color = TextTertiary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchViewModel.clearQuery()
                            focusManager.clearFocus()
                            onQueryActiveChange(false)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        searchViewModel.submitSearch()
                    }
                ),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = VividCyan,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
        }

        // Filter Chips Row: Songs (default), Albums, Artists, Playlists, Local
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SearchCategory.values()) { category ->
                val isSelected = category == selectedCategory
                FilterChip(
                    selected = isSelected,
                    onClick = { searchViewModel.onCategorySelected(category) },
                    label = {
                        Text(
                            text = category.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VividCyan.copy(alpha = 0.22f),
                        selectedLabelColor = VividCyan,
                        containerColor = DarkSurfaceVariant,
                        labelColor = TextSecondary
                    ),
                    border = if (isSelected) {
                        FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = true,
                            selectedBorderColor = VividCyan.copy(alpha = 0.6f),
                            selectedBorderWidth = 1.dp
                        )
                    } else {
                        null
                    }
                )
            }
        }

        // Live InnerTube Search Suggestions while typing online
        if (searchQuery.isNotBlank() &&
            selectedCategory != SearchCategory.LOCAL &&
            !isOfflineFallback &&
            searchSuggestions.isNotEmpty()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = searchSuggestions,
                    key = { it },
                    contentType = { "search_suggestion" }
                ) { suggestion ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.7f))
                            .border(
                                width = 0.5.dp,
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                focusManager.clearFocus()
                                searchViewModel.submitSearch(suggestion)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = VividCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = suggestion,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Content Area
        if (searchQuery.isEmpty()) {
            // Recent Searches View
            if (recentSearches.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Searches",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        TextButton(onClick = { searchViewModel.clearRecentSearches() }) {
                            Text("Clear All", color = TextTertiary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 160.dp)
                    ) {
                        items(
                            items = recentSearches,
                            key = { it },
                            contentType = { "recent_search" }
                        ) { query ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        focusManager.clearFocus()
                                        searchViewModel.submitSearch(query)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = VictusIcons.History,
                                        contentDescription = null,
                                        tint = TextTertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = query,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = { searchViewModel.deleteRecentSearch(query) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = VictusIcons.Close,
                                        contentDescription = "Delete",
                                        tint = TextTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Search YouTube Music & your library",
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        } else if (selectedCategory == SearchCategory.LOCAL || isOfflineFallback) {
            // Local & Downloaded Offline Results (explicit Local filter OR automatic Offline Fallback)
            Column(modifier = Modifier.fillMaxSize()) {
                if (isOfflineFallback && selectedCategory != SearchCategory.LOCAL) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = VividCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Offline — showing local & downloaded tracks",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = { searchViewModel.retrySearch() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Retry", color = VividCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (filteredLocalSongs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isOfflineFallback) {
                                "No local or downloaded tracks found for \"$searchQuery\""
                            } else {
                                "No local tracks found for \"$searchQuery\""
                            },
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    Text(
                        text = "${filteredLocalSongs.size} local & downloaded results",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 160.dp)
                    ) {
                        items(
                            items = filteredLocalSongs,
                            key = { it.id },
                            contentType = { "local_search_track" }
                        ) { song ->
                            val videoId = song.remoteVideoId ?: song.id.toString()
                            val dlStatus = downloadStates[videoId]
                                ?: if (song.isDownloaded) DownloadStatus.COMPLETED else DownloadStatus.NOT_DOWNLOADED
                            val dlProgress = downloadProgress[videoId] ?: 0f

                            TrackItem(
                                song = song,
                                isPlayingCurrent = song.id == currentPlayingSongId,
                                onSongClick = {
                                    searchViewModel.recordInteractionSearchHistory()
                                    onSongClick(song, filteredLocalSongs)
                                },
                                onToggleFavorite = { onToggleFavorite(song) },
                                onPlayNext = { onPlayNext(song) },
                                onAddToQueue = { onAddToQueue(song) },
                                onAddToPlaylist = { onAddToPlaylist(song) },
                                downloadStatus = dlStatus,
                                downloadProgress = dlProgress,
                                onToggleDownload = { onToggleDownload?.invoke(song) },
                                onGoToAlbum = if (!song.isRemote) ({ onGoToAlbum(song.albumId) }) else null,
                                onGoToArtist = { onGoToArtist(song.artist) }
                            )
                        }
                    }
                }
            }
        } else {
            // Online Search Results (Songs, Albums, Artists, Playlists)
            when (val state = onlineSearchState) {
                is OnlineSearchUiState.Loading -> {
                    SearchResultsLoadingSkeleton()
                }
                is OnlineSearchUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = state.message,
                                color = Color(0xFFFF5252),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { searchViewModel.retrySearch() },
                                colors = ButtonDefaults.buttonColors(containerColor = VividCyan),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is OnlineSearchUiState.Idle -> {
                    SearchResultsLoadingSkeleton()
                }
                is OnlineSearchUiState.Success -> {
                    val result = state.result
                    val isEmptyForCategory = when (selectedCategory) {
                        SearchCategory.SONGS -> result.tracks.isEmpty()
                        SearchCategory.ALBUMS -> result.albums.isEmpty()
                        SearchCategory.ARTISTS -> result.artists.isEmpty()
                        SearchCategory.PLAYLISTS -> result.playlists.isEmpty()
                        SearchCategory.LOCAL -> true
                    }

                    if (isEmptyForCategory) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No ${selectedCategory.title.lowercase()} found for \"$searchQuery\"",
                                color = TextTertiary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (selectedCategory) {
                                SearchCategory.SONGS -> {
                                    items(
                                        items = result.tracks,
                                        key = { it.videoId },
                                        contentType = { "remote_search_track" }
                                    ) { track ->
                                        val status = downloadStates[track.videoId] ?: DownloadStatus.NOT_DOWNLOADED
                                        val prog = downloadProgress[track.videoId] ?: 0f
                                        RemoteTrackSearchItem(
                                            track = track,
                                            onClick = {
                                                searchViewModel.recordInteractionSearchHistory()
                                                onRemoteTrackClick(track)
                                            },
                                            onAddToQueue = { onAddToQueue(track.toSong()) },
                                            onAddToPlaylist = { onAddToPlaylist(track.toSong()) },
                                            onToggleFavorite = { onToggleFavorite(track.toSong()) },
                                            downloadStatus = status,
                                            downloadProgress = prog,
                                            onToggleDownload = { onToggleDownload?.invoke(track.toSong()) }
                                        )
                                    }
                                }
                                SearchCategory.ALBUMS -> {
                                    items(
                                        items = result.albums,
                                        key = { it.browseId },
                                        contentType = { "remote_search_album" }
                                    ) { album ->
                                        RemoteAlbumSearchItem(
                                            album = album,
                                            onClick = {
                                                searchViewModel.recordInteractionSearchHistory()
                                                onRemoteAlbumClick(album)
                                            }
                                        )
                                    }
                                }
                                SearchCategory.ARTISTS -> {
                                    items(
                                        items = result.artists,
                                        key = { it.browseId },
                                        contentType = { "remote_search_artist" }
                                    ) { artist ->
                                        RemoteArtistSearchItem(
                                            artist = artist,
                                            onClick = {
                                                searchViewModel.recordInteractionSearchHistory()
                                                onRemoteArtistClick(artist)
                                            }
                                        )
                                    }
                                }
                                SearchCategory.PLAYLISTS -> {
                                    items(
                                        items = result.playlists,
                                        key = { it.browseId },
                                        contentType = { "remote_search_playlist" }
                                    ) { playlist ->
                                        RemotePlaylistSearchItem(
                                            playlist = playlist,
                                            onClick = {
                                                searchViewModel.recordInteractionSearchHistory()
                                                onRemotePlaylistClick(playlist)
                                            }
                                        )
                                    }
                                }
                                SearchCategory.LOCAL -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsLoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "searchSkeletonShimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "searchSkeletonAlpha"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(7) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.68f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceVariant.copy(alpha = alpha))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceVariant.copy(alpha = alpha * 0.85f))
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteTrackSearchItem(
    track: RemoteTrack,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    downloadProgress: Float = 0f,
    onToggleDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = com.victustune.player.data.coil.rememberThumbnailRequest(track.thumbnailUrl, sizePx = 144),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist}${track.album?.let { " • $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        track.durationText?.let { dur ->
            Text(
                text = dur,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        when (downloadStatus) {
            DownloadStatus.DOWNLOADING -> {
                CircularProgressIndicator(
                    progress = { (downloadProgress / 100f).coerceIn(0.05f, 1f) },
                    strokeWidth = 2.dp,
                    color = VividCyan,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
            }
            DownloadStatus.QUEUED -> {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = TextTertiary,
                    modifier = Modifier.size(14.dp).padding(end = 4.dp)
                )
            }
            DownloadStatus.COMPLETED -> {
                Icon(
                    imageVector = VictusIcons.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = VividCyan,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
            }
            else -> {}
        }

        Box {
            IconButton(onClick = { isMenuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextSecondary
                )
            }

            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                modifier = Modifier.background(DarkSurfaceVariant)
            ) {
                DropdownMenuItem(
                    text = { Text("Add to Queue", color = TextPrimary) },
                    onClick = {
                        onAddToQueue()
                        isMenuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist", color = TextPrimary) },
                    onClick = {
                        onAddToPlaylist()
                        isMenuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Favorite", color = TextPrimary) },
                    onClick = {
                        onToggleFavorite()
                        isMenuOpen = false
                    }
                )
                if (onToggleDownload != null) {
                    val isDl = downloadStatus == DownloadStatus.COMPLETED
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isDl) "Remove Download" else "Download",
                                color = if (isDl) Color(0xFFFF5252) else VividCyan
                            )
                        },
                        onClick = {
                            onToggleDownload()
                            isMenuOpen = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteAlbumSearchItem(
    album: RemoteAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = com.victustune.player.data.coil.rememberThumbnailRequest(album.thumbnailUrl, sizePx = 144),
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Album • ${album.artist}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RemoteArtistSearchItem(
    artist: RemoteArtist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = com.victustune.player.data.coil.rememberThumbnailRequest(artist.thumbnailUrl, sizePx = 144),
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Artist${artist.subscribers?.let { " • $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun RemotePlaylistSearchItem(
    playlist: RemotePlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = com.victustune.player.data.coil.rememberThumbnailRequest(playlist.thumbnailUrl, sizePx = 144),
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Playlist${playlist.author?.let { " • $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
