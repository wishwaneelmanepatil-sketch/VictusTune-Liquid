package com.victustune.player.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import com.victustune.player.data.model.BrowseResult
import com.victustune.player.data.model.RemoteTrack
import com.victustune.player.data.model.Song
import com.victustune.player.data.remote.innertube.ThumbnailUtils
import com.victustune.player.ui.theme.*

@Composable
fun RemoteDetailScreen(
    browseId: String,
    initialTitle: String,
    initialSubtitle: String?,
    initialThumbnailUrl: String?,
    onBack: () -> Unit,
    loadDetail: suspend (String) -> BrowseResult,
    onPlayAll: (List<RemoteTrack>) -> Unit,
    onShuffleAll: (List<RemoteTrack>) -> Unit,
    onTrackClick: (RemoteTrack, List<RemoteTrack>) -> Unit,
    onAddToQueue: (RemoteTrack) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var detailResult by remember { mutableStateOf<BrowseResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(browseId) {
        isLoading = true
        errorMessage = null
        try {
            val result = loadDetail(browseId)
            detailResult = result
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load content"
        } finally {
            isLoading = false
        }
    }

    val displayTitle = detailResult?.title ?: initialTitle
    val displayThumbnail = detailResult?.headerThumbnailUrl ?: initialThumbnailUrl
    val tracks = detailResult?.tracks ?: emptyList()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Header Image & Back Button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    if (displayThumbnail != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(ThumbnailUtils.upgrade(displayThumbnail) ?: displayThumbnail)
                                .crossfade(true)
                                .build(),
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.4f),
                                            Color.Transparent,
                                            DarkBackground.copy(alpha = 0.8f),
                                            DarkBackground
                                        )
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkSurfaceVariant)
                        )
                    }

                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }

            // Metadata & Action Buttons
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    initialSubtitle?.let { sub ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    if (tracks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${tracks.size} tracks",
                            style = MaterialTheme.typography.labelMedium,
                            color = VividCyan
                        )
                    }

                    detailResult?.description?.let { desc ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Transport Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { if (tracks.isNotEmpty()) onPlayAll(tracks) },
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VividCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { if (tracks.isNotEmpty()) onShuffleAll(tracks.shuffled()) },
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Icon(imageVector = VictusIcons.Shuffle, contentDescription = null, tint = TextPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Track List or Loading
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = VividCyan)
                    }
                }
            } else if (errorMessage != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = errorMessage ?: "Error", color = TextSecondary)
                    }
                }
            } else if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No tracks available in this collection", color = TextSecondary)
                    }
                }
            } else {
                itemsIndexed(tracks) { index, track ->
                    var isMenuOpen by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track, tracks) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                            modifier = Modifier.width(28.dp)
                        )

                        track.thumbnailUrl?.let { thumb ->
                            AsyncImage(
                                model = thumb,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
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
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
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
                                    text = { Text("Play Next", color = TextPrimary) },
                                    onClick = {
                                        onAddToQueue(track)
                                        isMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to Queue", color = TextPrimary) },
                                    onClick = {
                                        onAddToQueue(track)
                                        isMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist", color = TextPrimary) },
                                    onClick = {
                                        onAddToPlaylist(track.toSong())
                                        isMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
