package com.victustune.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.victustune.player.data.download.DownloadStatus
import com.victustune.player.data.model.Song
import com.victustune.player.ui.theme.*

@Composable
fun TrackItem(
    song: Song,
    isPlayingCurrent: Boolean,
    onSongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    downloadProgress: Float = 0f,
    onToggleDownload: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isItemDownloaded = song.isDownloaded || downloadStatus == DownloadStatus.COMPLETED

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isPlayingCurrent) ElectricPurple.copy(alpha = 0.10f) else Color.Transparent
            )
            .clickable(onClick = onSongClick)
            .padding(horizontal = DesignTokens.SpaceMd, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art thumbnail (explicitly downsampled for smooth list scrolling)
        AsyncImage(
            model = com.victustune.player.data.coil.rememberThumbnailRequest(song.albumArtUri, sizePx = 144),
            contentDescription = "${song.title} artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(DesignTokens.ThumbShape)
                .background(DesignTokens.InteractiveSurface)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontWeight = if (isPlayingCurrent) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isPlayingCurrent) VividCyan else DesignTokens.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.album}",
                fontSize = 12.sp,
                color = DesignTokens.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Download Status Indicator
        when {
            downloadStatus == DownloadStatus.DOWNLOADING -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                    CircularProgressIndicator(
                        progress = { (downloadProgress / 100f).coerceIn(0.05f, 1f) },
                        strokeWidth = 2.dp,
                        color = VividCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            downloadStatus == DownloadStatus.QUEUED -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            isItemDownloaded -> {
                Icon(
                    imageVector = VictusIcons.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = VividCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        // Duration text with tabular numerals
        Text(
            text = song.durationFormatted,
            style = TabularNumericStyle,
            fontSize = 12.sp,
            color = DesignTokens.TextTertiary
        )

        // Favorite icon toggle
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Filled.Favorite else VictusIcons.FavoriteBorder,
                contentDescription = if (song.isFavorite) "Favorited" else "Add to favorites",
                tint = if (song.isFavorite) NeonPink else TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Overflow dropdown menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More actions",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(DarkSurfaceVariant)
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    }
                )
                if (onToggleDownload != null || song.isRemote) {
                    val isDl = isItemDownloaded
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isDl) "Remove Download" else "Download",
                                color = if (isDl) Color(0xFFFF5252) else VividCyan
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleDownload?.invoke()
                        }
                    )
                }
                if (onGoToAlbum != null) {
                    DropdownMenuItem(
                        text = { Text("Go to Album", color = TextPrimary) },
                        onClick = {
                            showMenu = false
                            onGoToAlbum()
                        }
                    )
                }
                if (onGoToArtist != null) {
                    DropdownMenuItem(
                        text = { Text("Go to Artist", color = TextPrimary) },
                        onClick = {
                            showMenu = false
                            onGoToArtist()
                        }
                    )
                }
            }
        }
    }
}
