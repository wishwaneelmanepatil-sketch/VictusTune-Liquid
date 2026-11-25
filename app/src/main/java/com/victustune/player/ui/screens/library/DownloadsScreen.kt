package com.victustune.player.ui.screens.library

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victustune.player.data.download.DownloadStatus
import com.victustune.player.data.local.entity.DownloadedTrackEntity
import com.victustune.player.data.model.Song
import com.victustune.player.ui.components.TrackItem
import com.victustune.player.ui.theme.*

@Composable
fun DownloadsScreen(
    downloadedSongs: List<Song>,
    allDownloads: List<DownloadedTrackEntity>,
    downloadStates: Map<String, DownloadStatus>,
    downloadProgress: Map<String, Float>,
    totalSizeBytes: Long,
    currentPlayingSongId: Long?,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onRemoveDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredCompletedSongs = remember(downloadedSongs, searchQuery) {
        if (searchQuery.isBlank()) {
            downloadedSongs
        } else {
            val q = searchQuery.trim().lowercase()
            downloadedSongs.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
        }
    }

    val activeDownloads = remember(allDownloads) {
        allDownloads.filter { it.status == "DOWNLOADING" || it.status == "QUEUED" }
    }

    val formattedTotalSize = remember(totalSizeBytes) {
        formatBytes(totalSizeBytes)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search & Filter Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search downloads...", color = TextTertiary, fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VividCyan,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = VividCyan
                    ),
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearchActive = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search", tint = TextSecondary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column {
                    Text(
                        text = "Offline Downloads",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "${downloadedSongs.size} tracks • $formattedTotalSize",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search downloads",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        // Action Buttons Row: Play All, Shuffle
        if (filteredCompletedSongs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onPlayAll(filteredCompletedSongs) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { onShuffleAll(filteredCompletedSongs.shuffled()) },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(VictusIcons.Shuffle, contentDescription = null, tint = VividCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            // Active Downloads Section
            if (activeDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = "Downloading (${activeDownloads.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = VividCyan,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                items(activeDownloads, key = { "active_${it.videoId}" }) { downloadItem ->
                    val progress = downloadProgress[downloadItem.videoId] ?: downloadItem.progress
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                                CircularProgressIndicator(
                                    progress = { (progress / 100f).coerceIn(0.05f, 1f) },
                                    strokeWidth = 3.dp,
                                    color = VividCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = downloadItem.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${downloadItem.artist} • ${progress.toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { onRemoveDownload(downloadItem.videoId) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Cancel", tint = TextTertiary)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = DarkSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Completed Downloads Section
            if (filteredCompletedSongs.isEmpty() && activeDownloads.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = VictusIcons.Download,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No downloads matching '$searchQuery'" else "No offline downloads yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Downloaded tracks will appear here and play completely offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(filteredCompletedSongs, key = { it.id }) { song ->
                    val videoId = song.remoteVideoId ?: song.id.toString()
                    TrackItem(
                        song = song,
                        isPlayingCurrent = song.id == currentPlayingSongId,
                        onSongClick = { onPlaySong(song, filteredCompletedSongs) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        downloadStatus = DownloadStatus.COMPLETED,
                        downloadProgress = 100f,
                        onToggleDownload = { onRemoveDownload(videoId) }
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb < 1.0) {
        "%.1f KB".format(bytes.toDouble() / 1024)
    } else if (mb >= 1024.0) {
        "%.2f GB".format(mb / 1024.0)
    } else {
        "%.1f MB".format(mb)
    }
}
