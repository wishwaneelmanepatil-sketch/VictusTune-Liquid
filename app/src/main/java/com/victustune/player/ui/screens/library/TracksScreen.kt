package com.victustune.player.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victustune.player.data.download.DownloadStatus
import com.victustune.player.data.local.datastore.SortOption
import com.victustune.player.data.model.Song
import com.victustune.player.ui.components.TrackItem
import com.victustune.player.ui.theme.*

@Composable
fun TracksScreen(
    songs: List<Song>,
    currentPlayingSongId: Long?,
    sortOption: SortOption,
    onSortOptionSelected: (SortOption) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onSongClick: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (String) -> Unit,
    downloadStates: Map<String, DownloadStatus> = emptyMap(),
    downloadProgress: Map<String, Float> = emptyMap(),
    onToggleDownload: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top Action Bar: Count, Sort dropdown, Play & Shuffle buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${songs.size} tracks",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sort Menu
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = VictusIcons.Sort,
                            contentDescription = "Sort tracks",
                            tint = TextSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(DarkSurfaceVariant)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Title", color = if (sortOption == SortOption.TITLE) ElectricPurple else TextPrimary) },
                            onClick = {
                                onSortOptionSelected(SortOption.TITLE)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Artist", color = if (sortOption == SortOption.ARTIST) ElectricPurple else TextPrimary) },
                            onClick = {
                                onSortOptionSelected(SortOption.ARTIST)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Date Added", color = if (sortOption == SortOption.DATE_ADDED) ElectricPurple else TextPrimary) },
                            onClick = {
                                onSortOptionSelected(SortOption.DATE_ADDED)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duration", color = if (sortOption == SortOption.DURATION) ElectricPurple else TextPrimary) },
                            onClick = {
                                onSortOptionSelected(SortOption.DURATION)
                                showSortMenu = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = onShuffleAll,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = VictusIcons.Shuffle,
                        contentDescription = "Shuffle",
                        tint = VividCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle", fontSize = 13.sp, color = TextPrimary)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play", fontSize = 13.sp, color = Color.White)
                }
            }
        }

        // Track List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            items(
                items = songs,
                key = { it.id },
                contentType = { "track" }
            ) { song ->
                val videoId = song.remoteVideoId ?: song.id.toString()
                val status = downloadStates[videoId] ?: if (song.isDownloaded) DownloadStatus.COMPLETED else DownloadStatus.NOT_DOWNLOADED
                val progress = downloadProgress[videoId] ?: 0f
                TrackItem(
                    song = song,
                    isPlayingCurrent = song.id == currentPlayingSongId,
                    onSongClick = { onSongClick(song) },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onGoToAlbum = { onGoToAlbum(song.albumId) },
                    onGoToArtist = { onGoToArtist(song.artist) },
                    downloadStatus = status,
                    downloadProgress = progress,
                    onToggleDownload = { onToggleDownload?.invoke(song) }
                )
            }
        }
    }
}
