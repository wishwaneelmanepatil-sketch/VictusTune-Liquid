package com.victustune.player.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.victustune.player.data.model.AuthState
import com.victustune.player.data.model.Playlist
import com.victustune.player.data.model.RemotePlaylist
import com.victustune.player.data.model.Song
import com.victustune.player.data.remote.innertube.ThumbnailUtils
import com.victustune.player.ui.theme.*

enum class PlaylistFilter(val label: String) {
    ALL("All"),
    LOCAL("Local"),
    YOUTUBE_MUSIC("YouTube Music")
}

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    favoriteSongs: List<Song>,
    onOpenFavorites: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    syncedPlaylists: List<RemotePlaylist> = emptyList(),
    authState: AuthState = AuthState.LoggedOut,
    isSyncingPlaylists: Boolean = false,
    onRemotePlaylistClick: (RemotePlaylist) -> Unit = {},
    onSyncPlaylists: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
    onOpenDownloads: (() -> Unit)? = null,
    downloadedCount: Int = 0,
    onImportPlaylistClick: () -> Unit = {},
    onExportLocalPlaylist: (Playlist) -> Unit = {},
    onExportFavorites: () -> Unit = {},
    onExportDownloads: () -> Unit = {},
    onExportRemotePlaylist: (RemotePlaylist) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTargetPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var playlistNameInput by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PlaylistFilter.ALL) }

    // Close dialogs on back press before navigating away
    BackHandler(enabled = showCreateDialog) {
        showCreateDialog = false
    }
    BackHandler(enabled = renameTargetPlaylist != null) {
        renameTargetPlaylist = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val totalCount = playlists.size + (if (authState is AuthState.LoggedIn) syncedPlaylists.size else 0) + 1
            Text(
                text = "$totalCount playlists",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (authState is AuthState.LoggedIn) {
                    IconButton(
                        onClick = onSyncPlaylists,
                        enabled = !isSyncingPlaylists,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isSyncingPlaylists) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = VividCyan
                            )
                        } else {
                            Icon(
                                imageVector = VictusIcons.Sync,
                                contentDescription = "Sync YouTube Music Playlists",
                                tint = VividCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onImportPlaylistClick,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VividCyan)
                ) {
                    Icon(
                        imageVector = VictusIcons.ImportPlaylist,
                        contentDescription = "Import Playlist",
                        tint = VividCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Import", fontSize = 13.sp, color = VividCyan)
                }

                Button(
                    onClick = {
                        playlistNameInput = ""
                        showCreateDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", fontSize = 13.sp, color = Color.White)
                }
            }
        }

        // Filter chips: All, Local, YouTube Music
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PlaylistFilter.values()) { filter ->
                val isSelected = filter == selectedFilter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            text = filter.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VividCyan.copy(alpha = 0.2f),
                        selectedLabelColor = VividCyan,
                        containerColor = DarkSurfaceVariant,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            // Expired Session Banner
            if (authState is AuthState.Expired) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33FF5252))
                            .clickable(onClick = onOpenLogin)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Session expired — tap to sign in again",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Favorites Quick Card (shown for ALL and LOCAL filters)
            if (selectedFilter != PlaylistFilter.YOUTUBE_MUSIC) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF831843), Color(0xFFBE185D), NeonPink)
                                )
                            )
                            .clickable(onClick = onOpenFavorites)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorites",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Favorite Songs",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${favoriteSongs.size} tracks",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        var showFavMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showFavMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Favorites options",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showFavMenu,
                                onDismissRequest = { showFavMenu = false },
                                modifier = Modifier.background(DarkSurfaceVariant)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export Playlist", color = VividCyan) },
                                    onClick = {
                                        showFavMenu = false
                                        onExportFavorites()
                                    }
                                )
                            }
                        }
                    }
                }

                // Offline Downloads Quick Card (shown for ALL and LOCAL filters)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF0F766E), Color(0xFF0D9488), VividCyan)
                                )
                            )
                            .clickable { onOpenDownloads?.invoke() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = VictusIcons.Download,
                                contentDescription = "Downloads",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline Downloads",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$downloadedCount tracks available offline",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        var showDlMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showDlMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Downloads options",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showDlMenu,
                                onDismissRequest = { showDlMenu = false },
                                modifier = Modifier.background(DarkSurfaceVariant)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export Playlist", color = VividCyan) },
                                    onClick = {
                                        showDlMenu = false
                                        onExportDownloads()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Local Custom Playlists
            if (selectedFilter != PlaylistFilter.YOUTUBE_MUSIC) {
                items(playlists, key = { "local_${it.id}" }) { playlist ->
                    var showMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                            .clickable { onPlaylistClick(playlist) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElectricPurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = VictusIcons.QueueMusic,
                                contentDescription = null,
                                tint = ElectricPurple,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Local Playlist",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                    tint = TextSecondary
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(DarkSurfaceVariant)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename", color = TextPrimary) },
                                    onClick = {
                                        showMenu = false
                                        playlistNameInput = playlist.name
                                        renameTargetPlaylist = playlist
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Playlist", color = VividCyan) },
                                    onClick = {
                                        showMenu = false
                                        onExportLocalPlaylist(playlist)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = Color(0xFFEF4444)) },
                                    onClick = {
                                        showMenu = false
                                        onDeletePlaylist(playlist.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // YouTube Music Playlists Section
            if (selectedFilter != PlaylistFilter.LOCAL) {
                when {
                    authState is AuthState.LoggedOut -> {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = VictusIcons.CloudDone,
                                            contentDescription = null,
                                            tint = VividCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "YouTube Music Cloud Sync",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = TextPrimary
                                        )
                                    }
                                    Text(
                                        text = "Sign in to play your personal YouTube Music playlists and liked songs directly in Victus Tune.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                    Button(
                                        onClick = onOpenLogin,
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Sign In with YouTube Music", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    syncedPlaylists.isNotEmpty() -> {
                        item {
                            Text(
                                text = "YouTube Music Playlists",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = VividCyan,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }

                        items(syncedPlaylists, key = { "yt_${it.browseId}" }) { remotePlaylist ->
                            val isLikedMusic = remotePlaylist.browseId == "VLLM" || remotePlaylist.browseId == "LM"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                    .clickable { onRemotePlaylistClick(remotePlaylist) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isLikedMusic) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF831843), NeonPink)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(remotePlaylist.thumbnailUrl?.let { ThumbnailUtils.upgrade(it) } ?: remotePlaylist.thumbnailUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = remotePlaylist.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(DarkSurface)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = remotePlaylist.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isLikedMusic) "Auto Playlist • YouTube Music" else "${remotePlaylist.author ?: "Playlist"} • YouTube Music",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                var showRemoteMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showRemoteMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Playlist options",
                                            tint = TextSecondary
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showRemoteMenu,
                                        onDismissRequest = { showRemoteMenu = false },
                                        modifier = Modifier.background(DarkSurfaceVariant)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Export Playlist", color = VividCyan) },
                                            onClick = {
                                                showRemoteMenu = false
                                                onExportRemotePlaylist(remotePlaylist)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    authState is AuthState.LoggedIn -> {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "No synced playlists found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                OutlinedButton(
                                    onClick = onSyncPlaylists,
                                    enabled = !isSyncingPlaylists,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VividCyan)
                                ) {
                                    if (isSyncingPlaylists) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = VividCyan
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Syncing...")
                                    } else {
                                        Text("Sync Playlists Now")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Playlist", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    placeholder = { Text("Playlist name", color = TextTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricPurple,
                        unfocusedBorderColor = DarkSurfaceBorder
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistNameInput.isNotBlank()) {
                            onCreatePlaylist(playlistNameInput.trim())
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create", color = ElectricPurple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Rename Playlist Dialog
    if (renameTargetPlaylist != null) {
        AlertDialog(
            onDismissRequest = { renameTargetPlaylist = null },
            title = { Text("Rename Playlist", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricPurple,
                        unfocusedBorderColor = DarkSurfaceBorder
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameTargetPlaylist?.let { pl ->
                            if (playlistNameInput.isNotBlank()) {
                                onRenamePlaylist(pl.id, playlistNameInput.trim())
                            }
                        }
                        renameTargetPlaylist = null
                    }
                ) {
                    Text("Save", color = ElectricPurple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetPlaylist = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}
