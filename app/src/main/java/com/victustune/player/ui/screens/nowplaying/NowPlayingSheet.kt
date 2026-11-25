package com.victustune.player.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.victustune.player.data.download.DownloadStatus
import com.victustune.player.data.model.LyricLine
import com.victustune.player.data.model.PlaybackState
import com.victustune.player.data.model.RepeatMode
import com.victustune.player.data.remote.innertube.ThumbnailUtils
import com.victustune.player.ui.components.MarqueeText
import com.victustune.player.ui.components.WaveformScrubber
import com.victustune.player.ui.theme.*

@Composable
fun NowPlayingSheet(
    playbackState: PlaybackState,
    lyrics: List<LyricLine>,
    accentColor: Color,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    downloadProgress: Float = 0f,
    onToggleDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return
    var showLyrics by remember { mutableStateOf(false) }

    // If lyrics overlay is showing, back dismisses lyrics first
    BackHandler(enabled = showLyrics) {
        showLyrics = false
    }

    var verticalDragOffset by remember { mutableFloatStateOf(0f) }
    var horizontalDragOffset by remember { mutableFloatStateOf(0f) }

    // Dynamic gradient background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.45f),
            DarkBackground.copy(alpha = 0.95f),
            DarkBackground
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .background(backgroundBrush)
            .draggable(
                state = rememberDraggableState { delta ->
                    verticalDragOffset += delta
                },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (verticalDragOffset > 100f || velocity > 600f) {
                        onDismiss()
                    }
                    verticalDragOffset = 0f
                }
            )
            .draggable(
                state = rememberDraggableState { delta ->
                    horizontalDragOffset += delta
                },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    if (horizontalDragOffset < -80f || velocity < -500f) {
                        onSkipNext()
                    } else if (horizontalDragOffset > 80f || velocity > 500f) {
                        onSkipPrevious()
                    }
                    horizontalDragOffset = 0f
                }
            )
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top App Bar: Dismiss Chevron, Title, Lyrics Toggle, Queue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = VictusIcons.KeyboardArrowDown,
                        contentDescription = "Collapse player",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )

                Row {
                    // Lyrics button toggle
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = VictusIcons.Lyrics,
                            contentDescription = "Lyrics",
                            tint = if (showLyrics) accentColor else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Up Next Queue Button
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            imageVector = VictusIcons.QueueMusic,
                            contentDescription = "Queue",
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Main Content Area: Album Artwork OR Synced Lyrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "artworkOrLyrics",
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { isLyrics ->
                    if (!isLyrics) {
                        // Large Rounded Album Artwork with shadow
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .aspectRatio(1f)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = RoundedCornerShape(28.dp),
                                    spotColor = accentColor,
                                    ambientColor = accentColor
                                )
                                .clip(RoundedCornerShape(28.dp))
                                .background(DarkSurfaceVariant)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(song.albumArtUri?.toString()?.let { ThumbnailUtils.upgrade(it) } ?: song.albumArtUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "${song.title} artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        LyricsOverlay(
                            lyrics = lyrics,
                            currentPositionMs = playbackState.currentPositionMs,
                            accentColor = accentColor,
                            onSeek = onSeek,
                            onClose = { showLyrics = false }
                        )
                    }
                }
            }

            // Track Meta: Title, Artist, Favorite Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${song.artist} • ${song.album}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Download Button
                    if (onToggleDownload != null || song.isRemote) {
                        IconButton(
                            onClick = { onToggleDownload?.invoke() },
                            modifier = Modifier.size(44.dp)
                        ) {
                            when {
                                downloadStatus == DownloadStatus.DOWNLOADING -> {
                                    CircularProgressIndicator(
                                        progress = { (downloadProgress / 100f).coerceIn(0.05f, 1f) },
                                        strokeWidth = 2.5.dp,
                                        color = VividCyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                downloadStatus == DownloadStatus.QUEUED -> {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = TextTertiary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                song.isDownloaded || downloadStatus == DownloadStatus.COMPLETED -> {
                                    Icon(
                                        imageVector = VictusIcons.DownloadDone,
                                        contentDescription = "Downloaded",
                                        tint = VividCyan,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = VictusIcons.Download,
                                        contentDescription = "Download song",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Favorite Button
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(48.dp)
                    ) {
                        val isFav = song.isFavorite
                        val heartScale by animateFloatAsState(
                            targetValue = if (isFav) 1.15f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "heartScale"
                        )
                        Icon(
                            imageVector = if (isFav) Icons.Filled.Favorite else VictusIcons.FavoriteBorder,
                            contentDescription = if (isFav) "Favorited" else "Favorite",
                            tint = if (isFav) NeonPink else TextSecondary,
                            modifier = Modifier
                                .size(28.dp)
                                .scale(heartScale)
                        )
                    }
                }
            }

            // Custom Smooth Scrubber / Waveform Slider with Elapsed & Remaining timestamps
            WaveformScrubber(
                currentPositionMs = playbackState.currentPositionMs,
                totalDurationMs = playbackState.totalDurationMs,
                accentColor = accentColor,
                isPlaying = playbackState.isPlaying,
                onSeek = onSeek,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Transport Controls: Shuffle, Prev, Play/Pause Morph, Next, Repeat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Toggle
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = VictusIcons.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffleEnabled) accentColor else TextTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous Track
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = VictusIcons.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause animated button
                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = accentColor,
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(elevation = 12.dp, shape = CircleShape, spotColor = accentColor)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) VictusIcons.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Next Track
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = VictusIcons.SkipNext,
                        contentDescription = "Next",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Mode Toggle (OFF / ALL / ONE)
                IconButton(onClick = onCycleRepeat) {
                    val repeatIcon = when (playbackState.repeatMode) {
                        RepeatMode.ONE -> VictusIcons.RepeatOne
                        else -> VictusIcons.Repeat
                    }
                    val repeatTint = when (playbackState.repeatMode) {
                        RepeatMode.OFF -> TextTertiary
                        else -> accentColor
                    }
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = repeatTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
