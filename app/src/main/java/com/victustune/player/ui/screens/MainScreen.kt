package com.victustune.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victustune.player.data.model.*
import com.victustune.player.ui.components.FloatingNavDock
import com.victustune.player.ui.components.MiniPlayer
import com.victustune.player.ui.components.NavDockItem
import com.victustune.player.ui.screens.explore.ExploreScreen
import com.victustune.player.ui.screens.explore.RemoteDetailScreen
import com.victustune.player.ui.screens.extras.EqualizerScreen
import com.victustune.player.ui.screens.extras.SettingsDialog
import com.victustune.player.ui.screens.extras.SleepTimerDialog
import com.victustune.player.ui.screens.library.*
import com.victustune.player.ui.screens.nowplaying.NowPlayingSheet
import com.victustune.player.ui.screens.nowplaying.QueueBottomSheet
import com.victustune.player.ui.screens.search.SearchScreen
import com.victustune.player.ui.theme.*
import com.victustune.player.ui.viewmodel.PlayerViewModel

enum class MainTab(val title: String) {
    EXPLORE("Explore"),
    TRACKS("Tracks"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists")
}

sealed class SubScreen {
    object Main : SubScreen()
    object Search : SubScreen()
    object Equalizer : SubScreen()
    data class AlbumDetail(val album: Album) : SubScreen()
    data class ArtistDetail(val artist: Artist) : SubScreen()
    data class PlaylistDetail(val playlist: Playlist) : SubScreen()
    object FavoritesDetail : SubScreen()
    data class RemoteDetail(
        val browseId: String,
        val title: String,
        val subtitle: String? = null,
        val thumbnailUrl: String? = null
    ) : SubScreen()
}

@Composable
fun MainScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val sleepTimerSeconds by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()

    val exploreState by viewModel.exploreState.collectAsStateWithLifecycle()
    val onlineSearchState by viewModel.onlineSearchState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
    val currentCacheBytes by viewModel.currentCacheBytes.collectAsStateWithLifecycle()
    val maxCacheSizeMb by viewModel.maxCacheSizeMb.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.EXPLORE) }
    var currentSubScreen by remember { mutableStateOf<SubScreen>(SubScreen.Main) }
    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }
    var isSleepTimerOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    // Add to playlist picker dialog state
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    // Dynamic accent color extracted from current playing track cover art!
    val accentColor by rememberDynamicAccentColor(playbackState.currentSong?.albumArtUri)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Main Top Bar (when on Main tab screen)
            if (currentSubScreen is SubScreen.Main) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Victus Tune",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Search Button
                        IconButton(onClick = { currentSubScreen = SubScreen.Search }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = TextSecondary
                            )
                        }

                        // Sleep Timer Button
                        IconButton(onClick = { isSleepTimerOpen = true }) {
                            Icon(
                                imageVector = VictusIcons.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = if (sleepTimerSeconds != null) VividCyan else TextSecondary
                            )
                        }

                        // Equalizer Button
                        IconButton(onClick = { currentSubScreen = SubScreen.Equalizer }) {
                            Icon(
                                imageVector = VictusIcons.Tune,
                                contentDescription = "Equalizer",
                                tint = TextSecondary
                            )
                        }

                        // Settings Button
                        IconButton(onClick = {
                            viewModel.updateCacheSize()
                            isSettingsOpen = true
                        }) {
                            Icon(
                                imageVector = VictusIcons.Settings,
                                contentDescription = "Settings",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                // Library Sub-Filter Chips when on Library
                if (currentTab == MainTab.TRACKS || currentTab == MainTab.ALBUMS || currentTab == MainTab.ARTISTS) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            MainTab.TRACKS to "Tracks",
                            MainTab.ALBUMS to "Albums",
                            MainTab.ARTISTS to "Artists"
                        ).forEach { (tab, label) ->
                            val isSelected = currentTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) accentColor else DarkSurfaceVariant
                                    )
                                    .clickable { currentTab = tab }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Body Area (SubScreens or Tab content)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val sub = currentSubScreen) {
                    is SubScreen.Main -> {
                        when (currentTab) {
                            MainTab.EXPLORE -> ExploreScreen(
                                exploreState = exploreState,
                                onRetry = { viewModel.loadExploreFeed() },
                                onTrackClick = { viewModel.playRemoteTrack(it) },
                                onAlbumClick = { album ->
                                    currentSubScreen = SubScreen.RemoteDetail(
                                        browseId = album.browseId,
                                        title = album.title,
                                        subtitle = album.artist,
                                        thumbnailUrl = album.thumbnailUrl
                                    )
                                },
                                onArtistClick = { artist ->
                                    currentSubScreen = SubScreen.RemoteDetail(
                                        browseId = artist.browseId,
                                        title = artist.name,
                                        subtitle = "Artist",
                                        thumbnailUrl = artist.thumbnailUrl
                                    )
                                },
                                onPlaylistClick = { playlist ->
                                    currentSubScreen = SubScreen.RemoteDetail(
                                        browseId = playlist.browseId,
                                        title = playlist.title,
                                        subtitle = playlist.author,
                                        thumbnailUrl = playlist.thumbnailUrl
                                    )
                                }
                            )
                            MainTab.TRACKS -> TracksScreen(
                                songs = songs,
                                currentPlayingSongId = playbackState.currentSong?.id,
                                sortOption = sortOption,
                                onSortOptionSelected = { viewModel.setSortOption(it) },
                                onPlayAll = { viewModel.playQueue(songs, 0) },
                                onShuffleAll = {
                                    viewModel.playQueue(songs.shuffled(), 0)
                                    viewModel.toggleShuffle()
                                },
                                onSongClick = { viewModel.playSong(it, songs) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onPlayNext = { viewModel.playSongNext(it) },
                                onAddToQueue = { viewModel.addToQueue(it) },
                                onAddToPlaylist = { songToAddToPlaylist = it },
                                onGoToAlbum = { albumId ->
                                    albums.find { it.id == albumId }?.let {
                                        currentSubScreen = SubScreen.AlbumDetail(it)
                                    }
                                },
                                onGoToArtist = { artistName ->
                                    artists.find { it.name == artistName }?.let {
                                        currentSubScreen = SubScreen.ArtistDetail(it)
                                    }
                                }
                            )
                            MainTab.ALBUMS -> AlbumsScreen(
                                albums = albums,
                                onAlbumClick = { currentSubScreen = SubScreen.AlbumDetail(it) }
                            )
                            MainTab.ARTISTS -> ArtistsScreen(
                                artists = artists,
                                onArtistClick = { currentSubScreen = SubScreen.ArtistDetail(it) }
                            )
                            MainTab.PLAYLISTS -> PlaylistsScreen(
                                playlists = playlists,
                                favoriteSongs = favoriteSongs,
                                onOpenFavorites = { currentSubScreen = SubScreen.FavoritesDetail },
                                onPlaylistClick = { currentSubScreen = SubScreen.PlaylistDetail(it) },
                                onCreatePlaylist = { viewModel.createPlaylist(it) },
                                onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) }
                            )
                        }
                    }
                    is SubScreen.Search -> {
                        SearchScreen(
                            allSongs = songs,
                            currentPlayingSongId = playbackState.currentSong?.id,
                            recentSearches = recentSearches,
                            onlineSearchState = onlineSearchState,
                            onBack = { currentSubScreen = SubScreen.Main },
                            onPerformSearch = { query, filter ->
                                viewModel.searchOnline(query, filter)
                            },
                            onDeleteRecentSearch = { viewModel.deleteRecentSearch(it) },
                            onClearRecentSearches = { viewModel.clearRecentSearches() },
                            onSongClick = { viewModel.playSong(it, songs) },
                            onRemoteTrackClick = { viewModel.playRemoteTrack(it) },
                            onRemoteAlbumClick = { album ->
                                currentSubScreen = SubScreen.RemoteDetail(
                                    browseId = album.browseId,
                                    title = album.title,
                                    subtitle = album.artist,
                                    thumbnailUrl = album.thumbnailUrl
                                )
                            },
                            onRemoteArtistClick = { artist ->
                                currentSubScreen = SubScreen.RemoteDetail(
                                    browseId = artist.browseId,
                                    title = artist.name,
                                    subtitle = "Artist",
                                    thumbnailUrl = artist.thumbnailUrl
                                )
                            },
                            onRemotePlaylistClick = { playlist ->
                                currentSubScreen = SubScreen.RemoteDetail(
                                    browseId = playlist.browseId,
                                    title = playlist.title,
                                    subtitle = playlist.author,
                                    thumbnailUrl = playlist.thumbnailUrl
                                )
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playSongNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            onGoToAlbum = { albumId ->
                                albums.find { it.id == albumId }?.let {
                                    currentSubScreen = SubScreen.AlbumDetail(it)
                                }
                            },
                            onGoToArtist = { artistName ->
                                artists.find { it.name == artistName }?.let {
                                    currentSubScreen = SubScreen.ArtistDetail(it)
                                }
                            },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                    is SubScreen.Equalizer -> {
                        EqualizerScreen(
                            equalizerController = viewModel.equalizerController,
                            onBack = { currentSubScreen = SubScreen.Main },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                    is SubScreen.AlbumDetail -> {
                        val albumSongs = remember(songs, sub.album) {
                            songs.filter { it.albumId == sub.album.id || it.album == sub.album.title }
                        }
                        DetailScreen(
                            title = sub.album.title,
                            subtitle = sub.album.artist,
                            artworkUri = sub.album.albumArtUri,
                            songs = albumSongs,
                            currentPlayingSongId = playbackState.currentSong?.id,
                            onBack = { currentSubScreen = SubScreen.Main },
                            onPlayAll = { viewModel.playQueue(albumSongs, 0) },
                            onShuffleAll = { viewModel.playQueue(albumSongs.shuffled(), 0) },
                            onSongClick = { viewModel.playSong(it, albumSongs) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playSongNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                    is SubScreen.ArtistDetail -> {
                        val artistSongs = remember(songs, sub.artist) {
                            songs.filter { it.artist == sub.artist.name }
                        }
                        DetailScreen(
                            title = sub.artist.name,
                            subtitle = "Artist",
                            artworkUri = artistSongs.firstOrNull()?.albumArtUri,
                            songs = artistSongs,
                            currentPlayingSongId = playbackState.currentSong?.id,
                            onBack = { currentSubScreen = SubScreen.Main },
                            onPlayAll = { viewModel.playQueue(artistSongs, 0) },
                            onShuffleAll = { viewModel.playQueue(artistSongs.shuffled(), 0) },
                            onSongClick = { viewModel.playSong(it, artistSongs) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playSongNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                    is SubScreen.PlaylistDetail -> {
                        val playlistSongsFlow = remember(sub.playlist.id) {
                            viewModel.getSongsForPlaylist(sub.playlist.id)
                        }
                        val plSongs by playlistSongsFlow.collectAsState(initial = emptyList())

                        DetailScreen(
                            title = sub.playlist.name,
                            subtitle = "Custom Playlist",
                            artworkUri = plSongs.firstOrNull()?.albumArtUri,
                            songs = plSongs,
                            currentPlayingSongId = playbackState.currentSong?.id,
                            onBack = { currentSubScreen = SubScreen.Main },
                            onPlayAll = { viewModel.playQueue(plSongs, 0) },
                            onShuffleAll = { viewModel.playQueue(plSongs.shuffled(), 0) },
                            onSongClick = { viewModel.playSong(it, plSongs) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playSongNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                    is SubScreen.FavoritesDetail -> {
                        DetailScreen(
                            title = "Favorites",
                            subtitle = "Liked Songs",
                            artworkUri = favoriteSongs.firstOrNull()?.albumArtUri,
                            songs = favoriteSongs,
                            currentPlayingSongId = playbackState.currentSong?.id,
                            onBack = { currentSubScreen = SubScreen.Main },
                            onPlayAll = { viewModel.playQueue(favoriteSongs, 0) },
                            onShuffleAll = { viewModel.playQueue(favoriteSongs.shuffled(), 0) },
                            onSongClick = { viewModel.playSong(it, favoriteSongs) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playSongNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                    is SubScreen.RemoteDetail -> {
                        RemoteDetailScreen(
                            browseId = sub.browseId,
                            initialTitle = sub.title,
                            initialSubtitle = sub.subtitle,
                            initialThumbnailUrl = sub.thumbnailUrl,
                            onBack = { currentSubScreen = SubScreen.Main },
                            loadDetail = { id -> viewModel.loadBrowseDetail(id) },
                            onPlayAll = { tracks -> viewModel.playRemoteTracks(tracks, 0) },
                            onShuffleAll = { tracks -> viewModel.playRemoteTracks(tracks.shuffled(), 0) },
                            onTrackClick = { track, tracks ->
                                val index = tracks.indexOf(track).coerceAtLeast(0)
                                viewModel.playRemoteTracks(tracks, index)
                            },
                            onAddToQueue = { track -> viewModel.addToQueue(track.toSong()) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )
                    }
                }
            }
        }

        // Persistent Mini-Player floating docked above the navigation dock
        if (playbackState.currentSong != null && !isNowPlayingExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 78.dp, start = 12.dp, end = 12.dp)
            ) {
                MiniPlayer(
                    playbackState = playbackState,
                    accentColor = accentColor,
                    onExpand = { isNowPlayingExpanded = true },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSkipNext = { viewModel.playNext() },
                    onSkipPrevious = { viewModel.playPrevious() }
                )
            }
        }

        // Floating Navigation Dock (Home, Search, Library, Playlists)
        if (!isNowPlayingExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                FloatingNavDock(
                    currentTab = currentTab,
                    currentSubScreen = currentSubScreen,
                    accentColor = accentColor,
                    onItemSelected = { item ->
                        when (item) {
                            NavDockItem.HOME -> {
                                currentSubScreen = SubScreen.Main
                                currentTab = MainTab.EXPLORE
                            }
                            NavDockItem.SEARCH -> {
                                currentSubScreen = SubScreen.Search
                            }
                            NavDockItem.LIBRARY -> {
                                currentSubScreen = SubScreen.Main
                                if (currentTab != MainTab.TRACKS && currentTab != MainTab.ALBUMS && currentTab != MainTab.ARTISTS) {
                                    currentTab = MainTab.TRACKS
                                }
                            }
                            NavDockItem.PLAYLISTS -> {
                                currentSubScreen = SubScreen.Main
                                currentTab = MainTab.PLAYLISTS
                            }
                        }
                    }
                )
            }
        }

        // Full-screen Now Playing Overlay with smooth expansion transition
        AnimatedVisibility(
            visible = isNowPlayingExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350)
            ) + fadeIn(tween(250)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            ) + fadeOut(tween(200))
        ) {
            NowPlayingSheet(
                playbackState = playbackState,
                lyrics = lyrics,
                accentColor = accentColor,
                onDismiss = { isNowPlayingExpanded = false },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSkipNext = { viewModel.playNext() },
                onSkipPrevious = { viewModel.playPrevious() },
                onSeek = { viewModel.seekTo(it) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeat = { viewModel.cycleRepeatMode() },
                onToggleFavorite = {
                    playbackState.currentSong?.let { viewModel.toggleFavorite(it) }
                },
                onOpenQueue = { isQueueOpen = true }
            )
        }

        // Up Next Queue Bottom Sheet
        if (isQueueOpen) {
            QueueBottomSheet(
                queue = playbackState.queue,
                currentQueueIndex = playbackState.currentQueueIndex,
                accentColor = accentColor,
                onDismiss = { isQueueOpen = false },
                onSongClick = { index ->
                    viewModel.playQueue(playbackState.queue, index)
                },
                onRemove = { index ->
                    viewModel.removeFromQueue(index)
                },
                onMoveItem = { from, to ->
                    viewModel.moveQueueItem(from, to)
                }
            )
        }

        // Sleep Timer Dialog
        if (isSleepTimerOpen) {
            SleepTimerDialog(
                remainingSeconds = sleepTimerSeconds,
                onSetTimerMinutes = { viewModel.setSleepTimer(it) },
                onCancelTimer = { viewModel.cancelSleepTimer() },
                onDismiss = { isSleepTimerOpen = false }
            )
        }

        // Streaming Settings Dialog
        if (isSettingsOpen) {
            SettingsDialog(
                currentQuality = audioQuality,
                onSelectQuality = { viewModel.setAudioQuality(it) },
                currentCacheBytes = currentCacheBytes,
                maxCacheSizeMb = maxCacheSizeMb,
                onSelectMaxCacheSizeMb = { viewModel.setMaxCacheSizeMb(it) },
                onClearCache = { viewModel.clearCache() },
                onDismiss = { isSettingsOpen = false }
            )
        }

        // Add to Playlist Picker Dialog
        songToAddToPlaylist?.let { targetSong ->
            AlertDialog(
                onDismissRequest = { songToAddToPlaylist = null },
                title = { Text("Add to Playlist", color = TextPrimary) },
                text = {
                    if (playlists.isEmpty()) {
                        Text("No custom playlists created yet. Create one from the Playlists tab!", color = TextSecondary)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            playlists.forEach { pl ->
                                OutlinedButton(
                                    onClick = {
                                        viewModel.addSongToPlaylist(pl.id, targetSong.id)
                                        songToAddToPlaylist = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                                ) {
                                    Text(pl.name)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { songToAddToPlaylist = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface
            )
        }
    }
}
