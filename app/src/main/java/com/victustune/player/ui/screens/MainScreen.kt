package com.victustune.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.victustune.player.VictusTuneApp
import com.victustune.player.data.download.DownloadStatus
import com.victustune.player.data.model.*
import com.victustune.player.data.transfer.ExportFormat
import com.victustune.player.ui.components.MiniPlayer
import com.victustune.player.ui.components.NavDockItem
import com.victustune.player.ui.components.NavigationDock
import com.victustune.player.ui.screens.auth.LoginScreen
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
import com.victustune.player.ui.viewmodel.ExportTarget
import com.victustune.player.ui.viewmodel.ImportDialogState
import com.victustune.player.ui.viewmodel.PlayerViewModel
import com.victustune.player.ui.viewmodel.SearchViewModel

enum class MainTab(val title: String) {
    EXPLORE("Explore"),
    TRACKS("Tracks"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    DOWNLOADS("Downloads")
}

sealed class SubScreen {
    object Main : SubScreen()
    object Search : SubScreen()
    object Equalizer : SubScreen()
    object Login : SubScreen()
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
    val sortedSongs by viewModel.sortedSongs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val sleepTimerSeconds by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()

    val app = LocalContext.current.applicationContext as VictusTuneApp
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(app))

    val exploreState by viewModel.exploreState.collectAsStateWithLifecycle()
    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
    val currentCacheBytes by viewModel.currentCacheBytes.collectAsStateWithLifecycle()
    val maxCacheSizeMb by viewModel.maxCacheSizeMb.collectAsStateWithLifecycle()
    val sliderPreferences by viewModel.sliderPreferences.collectAsStateWithLifecycle()

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val syncedPlaylists by viewModel.syncedPlaylists.collectAsStateWithLifecycle()
    val isSyncingPlaylists by viewModel.isSyncingPlaylists.collectAsStateWithLifecycle()

    val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
    val filteredDownloadedSongs by viewModel.filteredDownloadedSongs.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val downloadSearchQuery by viewModel.downloadSearchQuery.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val totalDownloadSizeBytes by viewModel.totalDownloadSizeBytes.collectAsStateWithLifecycle()
    val downloadWifiOnly by viewModel.downloadWifiOnly.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val importDialogState by viewModel.importDialogState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.transferMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // SAF Import launcher
    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.preparePlaylistImport(uri)
        }
    }

    // Export Format Picker & SAF CreateDocument launchers
    var exportTarget by remember { mutableStateOf<ExportTarget?>(null) }
    var pendingExportTarget by remember { mutableStateOf<ExportTarget?>(null) }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType)
    ) { uri ->
        val target = pendingExportTarget
        pendingExportTarget = null
        if (uri != null && target != null) {
            viewModel.exportPlaylist(target, uri, ExportFormat.CSV)
        }
    }

    val createM3u8Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.M3U8.mimeType)
    ) { uri ->
        val target = pendingExportTarget
        pendingExportTarget = null
        if (uri != null && target != null) {
            viewModel.exportPlaylist(target, uri, ExportFormat.M3U8)
        }
    }

    fun launchImportPicker() {
        importDocumentLauncher.launch(
            arrayOf(
                "text/csv",
                "text/comma-separated-values",
                "audio/x-mpegurl",
                "application/vnd.apple.mpegurl",
                "text/plain"
            )
        )
    }

    fun launchExportCreateDocument(target: ExportTarget, format: ExportFormat) {
        pendingExportTarget = target
        val safeName = target.displayName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifEmpty { "playlist" }
        val fileName = "$safeName.${format.extension}"
        when (format) {
            ExportFormat.CSV -> createCsvLauncher.launch(fileName)
            ExportFormat.M3U8 -> createM3u8Launcher.launch(fileName)
        }
    }

    var currentTab by remember { mutableStateOf(MainTab.EXPLORE) }
    val backStack = remember { mutableStateListOf<SubScreen>() }
    val currentSubScreen = backStack.lastOrNull() ?: SubScreen.Main

    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }
    var isSleepTimerOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isSearchQueryActive by remember { mutableStateOf(false) }

    // Add to playlist picker dialog state
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    fun navigateTo(subScreen: SubScreen) {
        if (subScreen is SubScreen.Main) {
            backStack.clear()
        } else {
            if (backStack.lastOrNull() != subScreen) {
                backStack.add(subScreen)
            }
        }
    }

    fun navigateBack(): Boolean {
        if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
            return true
        }
        return false
    }

    // --- Back Navigation Priority Hierarchy ---

    // 1. Dialogs & Bottom Sheets: Close any open Queue sheet, Equalizer panel, Track options menu, or Playlist picker first.
    val hasActiveDialogOrSheet = songToAddToPlaylist != null ||
            isSleepTimerOpen ||
            isSettingsOpen ||
            isQueueOpen ||
            importDialogState !is ImportDialogState.Idle ||
            exportTarget != null

    BackHandler(enabled = songToAddToPlaylist != null) {
        songToAddToPlaylist = null
    }

    BackHandler(enabled = isSleepTimerOpen) {
        isSleepTimerOpen = false
    }

    BackHandler(enabled = isSettingsOpen) {
        isSettingsOpen = false
    }

    BackHandler(enabled = isQueueOpen) {
        isQueueOpen = false
    }

    // 2. Now Playing Screen: If full-screen Now Playing is expanded, Back collapses it back down to Mini-Player
    BackHandler(enabled = isNowPlayingExpanded && !hasActiveDialogOrSheet) {
        isNowPlayingExpanded = false
    }

    // 4. Detail Screens: Pop back to previous screen (SearchScreen's own BackHandler handles active query/focus first as Priority 3)
    BackHandler(
        enabled = backStack.isNotEmpty() && !isNowPlayingExpanded && !hasActiveDialogOrSheet &&
                !(currentSubScreen is SubScreen.Search && isSearchQueryActive)
    ) {
        navigateBack()
    }

    // 5. Secondary Tabs: If on a non-home tab (Tracks, Albums, Artists, Playlists), navigate back to Home/Start tab (Explore)
    BackHandler(
        enabled = backStack.isEmpty() && currentTab != MainTab.EXPLORE && !isNowPlayingExpanded && !hasActiveDialogOrSheet
    ) {
        currentTab = MainTab.EXPLORE
    }

    // 6. Home Tab (Root):
    // When backStack is empty, currentTab is MainTab.EXPLORE, and no overlays/dialogs are open,
    // all BackHandlers above are disabled. The system Android Back naturally exits/minimizes the app.

    // Dynamic accent & muted matte surface tint extracted from current playing track cover art
    val dynamicColors = rememberDynamicColors(playbackState.currentSong?.albumArtUri)
    val accentColor = dynamicColors.accent
    val surfaceTint = dynamicColors.surfaceTint

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
                        IconButton(onClick = {
                            searchViewModel.startNewSearchSession()
                            navigateTo(SubScreen.Search)
                        }) {
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
                        IconButton(onClick = { navigateTo(SubScreen.Equalizer) }) {
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
                                    navigateTo(SubScreen.RemoteDetail(
                                        browseId = album.browseId,
                                        title = album.title,
                                        subtitle = album.artist,
                                        thumbnailUrl = album.thumbnailUrl
                                    ))
                                },
                                onArtistClick = { artist ->
                                    navigateTo(SubScreen.RemoteDetail(
                                        browseId = artist.browseId,
                                        title = artist.name,
                                        subtitle = "Artist",
                                        thumbnailUrl = artist.thumbnailUrl
                                    ))
                                },
                                onPlaylistClick = { playlist ->
                                    navigateTo(SubScreen.RemoteDetail(
                                        browseId = playlist.browseId,
                                        title = playlist.title,
                                        subtitle = playlist.author,
                                        thumbnailUrl = playlist.thumbnailUrl
                                    ))
                                }
                            )
                            MainTab.TRACKS -> TracksScreen(
                                songs = sortedSongs,
                                currentPlayingSongId = playbackState.currentSong?.id,
                                sortOption = sortOption,
                                onSortOptionSelected = { viewModel.setSortOption(it) },
                                onPlayAll = { viewModel.playQueue(sortedSongs, 0) },
                                onShuffleAll = {
                                    viewModel.playQueue(sortedSongs.shuffled(), 0)
                                    viewModel.toggleShuffle()
                                },
                                onSongClick = { viewModel.playSong(it, sortedSongs) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onPlayNext = { viewModel.playSongNext(it) },
                                onAddToQueue = { viewModel.addToQueue(it) },
                                onAddToPlaylist = { songToAddToPlaylist = it },
                                onGoToAlbum = { albumId ->
                                    albums.find { it.id == albumId }?.let {
                                        navigateTo(SubScreen.AlbumDetail(it))
                                    }
                                },
                                onGoToArtist = { artistName ->
                                    artists.find { it.name == artistName }?.let {
                                        navigateTo(SubScreen.ArtistDetail(it))
                                    }
                                },
                                downloadStates = downloadStates,
                                downloadProgress = downloadProgress,
                                onToggleDownload = { song ->
                                    val vid = song.remoteVideoId ?: song.id.toString()
                                    if (downloadStates[vid] == DownloadStatus.COMPLETED || song.isDownloaded) {
                                        viewModel.removeDownload(vid)
                                    } else {
                                        viewModel.downloadSong(song)
                                    }
                                }
                            )
                            MainTab.ALBUMS -> AlbumsScreen(
                                albums = albums,
                                onAlbumClick = { navigateTo(SubScreen.AlbumDetail(it)) }
                            )
                            MainTab.ARTISTS -> ArtistsScreen(
                                artists = artists,
                                onArtistClick = { navigateTo(SubScreen.ArtistDetail(it)) }
                            )
                            MainTab.PLAYLISTS -> PlaylistsScreen(
                                playlists = playlists,
                                favoriteSongs = favoriteSongs,
                                onOpenFavorites = { navigateTo(SubScreen.FavoritesDetail) },
                                onPlaylistClick = { navigateTo(SubScreen.PlaylistDetail(it)) },
                                onCreatePlaylist = { viewModel.createPlaylist(it) },
                                onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                syncedPlaylists = syncedPlaylists,
                                authState = authState,
                                isSyncingPlaylists = isSyncingPlaylists,
                                onRemotePlaylistClick = { playlist ->
                                    navigateTo(SubScreen.RemoteDetail(
                                        browseId = playlist.browseId,
                                        title = playlist.title,
                                        subtitle = playlist.author,
                                        thumbnailUrl = playlist.thumbnailUrl
                                    ))
                                },
                                onSyncPlaylists = { viewModel.syncPlaylists() },
                                onOpenLogin = { navigateTo(SubScreen.Login) },
                                onOpenDownloads = { currentTab = MainTab.DOWNLOADS },
                                downloadedCount = downloadedSongs.size,
                                onImportPlaylistClick = { launchImportPicker() },
                                onExportLocalPlaylist = { exportTarget = ExportTarget.LocalPlaylist(it) },
                                onExportFavorites = { exportTarget = ExportTarget.Favorites },
                                onExportDownloads = { exportTarget = ExportTarget.Downloads },
                                onExportRemotePlaylist = { exportTarget = ExportTarget.RemotePlaylistTarget(it) }
                            )
                            MainTab.DOWNLOADS -> DownloadsScreen(
                                downloadedSongsCount = downloadedSongs.size,
                                filteredCompletedSongs = filteredDownloadedSongs,
                                activeDownloads = activeDownloads,
                                searchQuery = downloadSearchQuery,
                                onSearchQueryChange = { viewModel.setDownloadSearchQuery(it) },
                                downloadProgress = downloadProgress,
                                totalSizeBytes = totalDownloadSizeBytes,
                                currentPlayingSongId = playbackState.currentSong?.id,
                                onPlaySong = { song, list -> viewModel.playSong(song, list) },
                                onPlayAll = { list -> viewModel.playQueue(list, 0) },
                                onShuffleAll = { list ->
                                    viewModel.playQueue(list.shuffled(), 0)
                                    viewModel.toggleShuffle()
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onPlayNext = { viewModel.playSongNext(it) },
                                onAddToQueue = { viewModel.addToQueue(it) },
                                onAddToPlaylist = { songToAddToPlaylist = it },
                                onRemoveDownload = { viewModel.removeDownload(it) }
                            )
                        }
                    }
                    is SubScreen.Search -> {
                        SearchScreen(
                            searchViewModel = searchViewModel,
                            currentPlayingSongId = playbackState.currentSong?.id,
                            onBack = { navigateBack() },
                            onSongClick = { song, list -> viewModel.playSong(song, list) },
                            onRemoteTrackClick = { viewModel.playRemoteTrack(it) },
                            onRemoteAlbumClick = { album ->
                                navigateTo(SubScreen.RemoteDetail(
                                    browseId = album.browseId,
                                    title = album.title,
                                    subtitle = album.artist,
                                    thumbnailUrl = album.thumbnailUrl
                                ))
                            },
                            onRemoteArtistClick = { artist ->
                                navigateTo(SubScreen.RemoteDetail(
                                    browseId = artist.browseId,
                                    title = artist.name,
                                    subtitle = "Artist",
                                    thumbnailUrl = artist.thumbnailUrl
                                ))
                            },
                            onRemotePlaylistClick = { playlist ->
                                navigateTo(SubScreen.RemoteDetail(
                                    browseId = playlist.browseId,
                                    title = playlist.title,
                                    subtitle = playlist.author,
                                    thumbnailUrl = playlist.thumbnailUrl
                                ))
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playSongNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            onGoToAlbum = { albumId ->
                                albums.find { it.id == albumId }?.let {
                                    navigateTo(SubScreen.AlbumDetail(it))
                                }
                            },
                            onGoToArtist = { artistName ->
                                artists.find { it.name == artistName }?.let {
                                    navigateTo(SubScreen.ArtistDetail(it))
                                }
                            },
                            onQueryActiveChange = { isSearchQueryActive = it },
                            isOverlayActive = isNowPlayingExpanded || hasActiveDialogOrSheet,
                            downloadStates = downloadStates,
                            downloadProgress = downloadProgress,
                            onToggleDownload = { song ->
                                val vid = song.remoteVideoId ?: song.id.toString()
                                if (downloadStates[vid] == DownloadStatus.COMPLETED || song.isDownloaded) {
                                    viewModel.removeDownload(vid)
                                } else {
                                    viewModel.downloadSong(song)
                                }
                            },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                    is SubScreen.Equalizer -> {
                        EqualizerScreen(
                            equalizerController = viewModel.equalizerController,
                            onBack = { navigateBack() },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                    is SubScreen.Login -> {
                        LoginScreen(
                            onBack = { navigateBack() },
                            onLoginSuccess = { cookieString ->
                                viewModel.handleLoginSuccess(cookieString)
                                navigateBack()
                            }
                        )
                    }
                    is SubScreen.AlbumDetail -> {
                        val albumSongs by produceState(initialValue = emptyList(), songs, sub.album) {
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                songs.filter { it.albumId == sub.album.id || it.album == sub.album.title }
                            }
                        }
                        DetailScreen(
                            title = sub.album.title,
                            subtitle = sub.album.artist,
                            artworkUri = sub.album.albumArtUri,
                            songs = albumSongs,
                            currentPlayingSongId = playbackState.currentSong?.id,
                            onBack = { navigateBack() },
                            onPlayAll = { viewModel.playQueue(albumSongs, 0) },
                            onShuffleAll = { viewModel.playQueue(albumSongs.shuffled(), 0) },
                            onSongClick = { viewModel.playSong(it, albumSongs) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playSongNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            onDownloadAll = { viewModel.downloadAll(albumSongs) },
                            downloadStates = downloadStates,
                            downloadProgress = downloadProgress,
                            onToggleDownload = { song ->
                                val vid = song.remoteVideoId ?: song.id.toString()
                                if (downloadStates[vid] == DownloadStatus.COMPLETED || song.isDownloaded) {
                                    viewModel.removeDownload(vid)
                                } else {
                                    viewModel.downloadSong(song)
                                }
                            },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                    is SubScreen.ArtistDetail -> {
                        val artistSongs by produceState(initialValue = emptyList(), songs, sub.artist) {
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                songs.filter { it.artist == sub.artist.name }
                            }
                        }
                        DetailScreen(
                            title = sub.artist.name,
                            subtitle = "Artist",
                            artworkUri = artistSongs.firstOrNull()?.albumArtUri,
                            songs = artistSongs,
                            currentPlayingSongId = playbackState.currentSong?.id,
                            onBack = { navigateBack() },
                            onPlayAll = { viewModel.playQueue(artistSongs, 0) },
                            onShuffleAll = { viewModel.playQueue(artistSongs.shuffled(), 0) },
                            onSongClick = { viewModel.playSong(it, artistSongs) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playSongNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            onDownloadAll = { viewModel.downloadAll(artistSongs) },
                            downloadStates = downloadStates,
                            downloadProgress = downloadProgress,
                            onToggleDownload = { song ->
                                val vid = song.remoteVideoId ?: song.id.toString()
                                if (downloadStates[vid] == DownloadStatus.COMPLETED || song.isDownloaded) {
                                    viewModel.removeDownload(vid)
                                } else {
                                    viewModel.downloadSong(song)
                                }
                            },
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
                            onBack = { navigateBack() },
                            onPlayAll = { viewModel.playQueue(plSongs, 0) },
                            onShuffleAll = { viewModel.playQueue(plSongs.shuffled(), 0) },
                            onSongClick = { viewModel.playSong(it, plSongs) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playSongNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            onDownloadAll = { viewModel.downloadAll(plSongs) },
                            downloadStates = downloadStates,
                            downloadProgress = downloadProgress,
                            onToggleDownload = { song ->
                                val vid = song.remoteVideoId ?: song.id.toString()
                                if (downloadStates[vid] == DownloadStatus.COMPLETED || song.isDownloaded) {
                                    viewModel.removeDownload(vid)
                                } else {
                                    viewModel.downloadSong(song)
                                }
                            },
                            onExportPlaylist = { exportTarget = ExportTarget.LocalPlaylist(sub.playlist) },
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
                            onBack = { navigateBack() },
                            onPlayAll = { viewModel.playQueue(favoriteSongs, 0) },
                            onShuffleAll = { viewModel.playQueue(favoriteSongs.shuffled(), 0) },
                            onSongClick = { viewModel.playSong(it, favoriteSongs) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playSongNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            onDownloadAll = { viewModel.downloadAll(favoriteSongs) },
                            downloadStates = downloadStates,
                            downloadProgress = downloadProgress,
                            onToggleDownload = { song ->
                                val vid = song.remoteVideoId ?: song.id.toString()
                                if (downloadStates[vid] == DownloadStatus.COMPLETED || song.isDownloaded) {
                                    viewModel.removeDownload(vid)
                                } else {
                                    viewModel.downloadSong(song)
                                }
                            },
                            onExportPlaylist = { exportTarget = ExportTarget.Favorites },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                    is SubScreen.RemoteDetail -> {
                        RemoteDetailScreen(
                            browseId = sub.browseId,
                            initialTitle = sub.title,
                            initialSubtitle = sub.subtitle,
                            initialThumbnailUrl = sub.thumbnailUrl,
                            onBack = { navigateBack() },
                            loadDetail = { id -> viewModel.loadBrowseDetail(id) },
                            onPlayAll = { tracks -> viewModel.playRemoteTracks(tracks, 0) },
                            onShuffleAll = { tracks -> viewModel.playRemoteTracks(tracks.shuffled(), 0) },
                            onTrackClick = { track, tracks ->
                                val index = tracks.indexOf(track).coerceAtLeast(0)
                                viewModel.playRemoteTracks(tracks, index)
                            },
                            onAddToQueue = { track -> viewModel.addToQueue(track.toSong()) },
                            onAddToPlaylist = { songToAddToPlaylist = it },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadAll = { tracks -> viewModel.downloadAll(tracks.map { it.toSong() }) },
                            downloadStates = downloadStates,
                            downloadProgress = downloadProgress,
                            onToggleDownload = { track ->
                                val song = track.toSong()
                                val vid = track.videoId
                                if (downloadStates[vid] == DownloadStatus.COMPLETED) {
                                    viewModel.removeDownload(vid)
                                } else {
                                    viewModel.downloadSong(song)
                                }
                            },
                            onExportPlaylist = { title, tracks ->
                                exportTarget = ExportTarget.CustomList(title, tracks.map { it.toSong() })
                            }
                        )
                    }
                }
            }
        }

        // Subtle bottom vertical gradient scrim so scrolling lists fade cleanly behind the dock
        if (!isNowPlayingExpanded && currentSubScreen !is SubScreen.Login) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                DesignTokens.CanvasBackground.copy(alpha = 0.85f),
                                DesignTokens.CanvasBackground
                            )
                        )
                    )
            )
        }

        // Persistent Mini-Player floating cleanly aligned 8dp above the navigation dock
        if (playbackState.currentSong != null && !isNowPlayingExpanded && currentSubScreen !is SubScreen.Login) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 78.dp, start = DesignTokens.SpaceMd, end = DesignTokens.SpaceMd)
            ) {
                MiniPlayer(
                    playbackState = playbackState,
                    progressStateFlow = viewModel.progressState,
                    accentColor = accentColor,
                    surfaceTint = surfaceTint,
                    onExpand = { isNowPlayingExpanded = true },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSkipNext = { viewModel.playNext() },
                    onSkipPrevious = { viewModel.playPrevious() }
                )
            }
        }

        // Matte Floating Pill Navigation Dock (Home, Search, Library, Playlists)
        if (!isNowPlayingExpanded && currentSubScreen !is SubScreen.Login) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = DesignTokens.SpaceSm)
            ) {
                NavigationDock(
                    currentTab = currentTab,
                    currentSubScreen = currentSubScreen,
                    accentColor = accentColor,
                    onItemSelected = { item ->
                        when (item) {
                            NavDockItem.HOME -> {
                                backStack.clear()
                                currentTab = MainTab.EXPLORE
                            }
                            NavDockItem.SEARCH -> {
                                if (currentSubScreen !is SubScreen.Search) {
                                    searchViewModel.startNewSearchSession()
                                    navigateTo(SubScreen.Search)
                                }
                            }
                            NavDockItem.LIBRARY -> {
                                backStack.clear()
                                if (currentTab != MainTab.TRACKS && currentTab != MainTab.ALBUMS && currentTab != MainTab.ARTISTS && currentTab != MainTab.DOWNLOADS) {
                                    currentTab = MainTab.TRACKS
                                }
                            }
                            NavDockItem.PLAYLISTS -> {
                                backStack.clear()
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
                progressStateFlow = viewModel.progressState,
                lyrics = lyrics,
                accentColor = accentColor,
                surfaceTint = surfaceTint,
                sliderPreferences = sliderPreferences,
                onCycleSliderStyle = { viewModel.cycleSliderStyle() },
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
                onOpenQueue = { isQueueOpen = true },
                downloadStatus = playbackState.currentSong?.let { s ->
                    val vid = s.remoteVideoId ?: s.id.toString()
                    downloadStates[vid] ?: if (s.isDownloaded) DownloadStatus.COMPLETED else DownloadStatus.NOT_DOWNLOADED
                } ?: DownloadStatus.NOT_DOWNLOADED,
                downloadProgress = playbackState.currentSong?.let { s ->
                    val vid = s.remoteVideoId ?: s.id.toString()
                    downloadProgress[vid] ?: 0f
                } ?: 0f,
                onToggleDownload = {
                    playbackState.currentSong?.let { song ->
                        val vid = song.remoteVideoId ?: song.id.toString()
                        if (downloadStates[vid] == DownloadStatus.COMPLETED || song.isDownloaded) {
                            viewModel.removeDownload(vid)
                        } else {
                            viewModel.downloadSong(song)
                        }
                    }
                },
                onSetLoopStart = { ms -> viewModel.setLoopStart(ms, fromDrag = false) },
                onSetLoopEnd = { ms -> viewModel.setLoopEnd(ms, fromDrag = false) },
                onDragLoopStart = { ms -> viewModel.setLoopStart(ms, fromDrag = true) },
                onDragLoopEnd = { ms -> viewModel.setLoopEnd(ms, fromDrag = true) },
                onNudgeLoopStart = { delta -> viewModel.nudgeLoopStart(delta) },
                onNudgeLoopEnd = { delta -> viewModel.nudgeLoopEnd(delta) },
                onToggleLoop = { viewModel.toggleLoop() },
                onClearLoop = { viewModel.clearLoop() }
            )
        }

        // Up Next Queue Bottom Sheet
        if (isQueueOpen) {
            QueueBottomSheet(
                queueEntries = playbackState.queueEntries.ifEmpty {
                    playbackState.queue.mapIndexed { idx, s -> QueueEntry(uid = "${s.id}_$idx", song = s) }
                },
                currentQueueIndex = playbackState.currentQueueIndex,
                accentColor = accentColor,
                onDismiss = { isQueueOpen = false },
                onSongClick = { index ->
                    viewModel.playQueueIndex(index)
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
                authState = authState,
                isSyncingPlaylists = isSyncingPlaylists,
                onOpenLogin = { navigateTo(SubScreen.Login) },
                onSyncPlaylists = { viewModel.syncPlaylists() },
                onSignOut = { viewModel.signOut() },
                totalDownloadSizeBytes = totalDownloadSizeBytes,
                downloadQuality = downloadQuality,
                onSelectDownloadQuality = { viewModel.setDownloadQuality(it) },
                downloadWifiOnly = downloadWifiOnly,
                onToggleDownloadWifiOnly = { viewModel.setDownloadWifiOnly(it) },
                onDeleteAllDownloads = { viewModel.removeAllDownloads() },
                sliderPreferences = sliderPreferences,
                onSelectSliderStyle = { viewModel.setSliderStyle(it) },
                onSelectSliderColorMode = { viewModel.setSliderColorMode(it) },
                onToggleShowSliderThumb = { viewModel.setShowSliderThumb(it) },
                accentColor = accentColor,
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
                                        viewModel.addSongToPlaylist(pl.id, targetSong)
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

        // Playlist Import Dialogs (Parsing / Preview / Progress / Summary / Error)
        PlaylistImportDialogs(
            state = importDialogState,
            onStartImport = { name, matchOnline ->
                viewModel.startPlaylistImport(name, matchOnline)
            },
            onCancelImport = { viewModel.cancelPlaylistImport() },
            onDismiss = { viewModel.dismissImportDialog() },
            onOpenPlaylist = { playlist ->
                currentTab = MainTab.PLAYLISTS
                navigateTo(SubScreen.PlaylistDetail(playlist))
            }
        )

        // Playlist Export Format Picker Dialog (CSV vs M3U8)
        exportTarget?.let { target ->
            ExportFormatPickerDialog(
                target = target,
                onSelectFormat = { format ->
                    exportTarget = null
                    launchExportCreateDocument(target, format)
                },
                onDismiss = { exportTarget = null }
            )
        }

        // Transfer Feedback Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 148.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = DarkSurfaceVariant,
                contentColor = TextPrimary,
                actionColor = VividCyan
            )
        }
    }
}
