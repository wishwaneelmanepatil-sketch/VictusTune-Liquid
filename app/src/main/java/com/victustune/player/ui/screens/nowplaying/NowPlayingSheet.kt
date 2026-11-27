package com.victustune.player.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.victustune.player.data.download.DownloadStatus
import com.victustune.player.data.model.LoopRegion
import com.victustune.player.data.model.LyricLine
import com.victustune.player.data.model.PlaybackProgressState
import com.victustune.player.data.model.PlaybackUiState
import com.victustune.player.data.model.RepeatMode
import com.victustune.player.data.model.SliderPreferences
import com.victustune.player.data.remote.innertube.ThumbnailUtils
import com.victustune.player.ui.components.MarqueeText
import com.victustune.player.ui.components.NowPlayingSlider
import com.victustune.player.ui.components.matteSurface
import com.victustune.player.ui.theme.*
import kotlinx.coroutines.flow.StateFlow

@Composable
fun NowPlayingSheet(
    playbackState: PlaybackUiState,
    progressStateFlow: StateFlow<PlaybackProgressState>,
    lyrics: List<LyricLine>,
    accentColor: Color,
    surfaceTint: Color = DesignTokens.ElevatedSurface,
    sliderPreferences: SliderPreferences = SliderPreferences(),
    onCycleSliderStyle: () -> Unit = {},
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
    onSetLoopStart: (Long?) -> Unit = {},
    onSetLoopEnd: (Long?) -> Unit = {},
    onDragLoopStart: (Long) -> Unit = {},
    onDragLoopEnd: (Long) -> Unit = {},
    onNudgeLoopStart: (Long) -> Unit = {},
    onNudgeLoopEnd: (Long) -> Unit = {},
    onToggleLoop: () -> Unit = {},
    onClearLoop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return
    val loopRegion = playbackState.loopRegion
    var showLyrics by remember { mutableStateOf(false) }
    var isLoopModeOpen by remember(song.id) { mutableStateOf(loopRegion.hasBounds || loopRegion.isActive) }

    // If lyrics overlay is showing, back dismisses lyrics first
    BackHandler(enabled = showLyrics) {
        showLyrics = false
    }

    var verticalDragOffset by remember { mutableFloatStateOf(0f) }
    var horizontalDragOffset by remember { mutableFloatStateOf(0f) }

    // Matte ambient gradient fading cleanly into CanvasBackground (#0B0B0E)
    val backgroundBrush = remember(surfaceTint) {
        Brush.verticalGradient(
            colors = listOf(
                surfaceTint,
                androidx.compose.ui.graphics.lerp(surfaceTint, DesignTokens.CanvasBackground, 0.55f),
                DesignTokens.CanvasBackground
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DesignTokens.CanvasBackground)
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
                    // Quick Slider Style Switcher
                    IconButton(onClick = onCycleSliderStyle) {
                        Icon(
                            imageVector = VictusIcons.GraphicEq,
                            contentDescription = "Slider style: ${sliderPreferences.style.label}",
                            tint = DesignTokens.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Lyrics button toggle
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = VictusIcons.Lyrics,
                            contentDescription = "Lyrics",
                            tint = if (showLyrics) accentColor else DesignTokens.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Up Next Queue Button
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            imageVector = VictusIcons.QueueMusic,
                            contentDescription = "Queue",
                            tint = DesignTokens.TextSecondary,
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
                    .padding(vertical = DesignTokens.SpaceMd),
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
                        // Crisp matte rounded Album Artwork (20.dp corner radius + hairline border)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.90f)
                                .aspectRatio(1f)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = DesignTokens.ArtworkShape,
                                    spotColor = Color.Black.copy(alpha = 0.6f),
                                    ambientColor = Color.Black.copy(alpha = 0.4f)
                                )
                                .clip(DesignTokens.ArtworkShape)
                                .background(DesignTokens.InteractiveSurface)
                                .border(
                                    width = DesignTokens.HairlineStrokeWidth,
                                    color = DesignTokens.HairlineBorder,
                                    shape = DesignTokens.ArtworkShape
                                )
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
                            progressStateFlow = progressStateFlow,
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

                    // A-B Section Loop Toggle Button
                    IconButton(
                        onClick = { isLoopModeOpen = !isLoopModeOpen },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = VictusIcons.LoopAb,
                            contentDescription = "A-B Section Loop",
                            tint = when {
                                loopRegion.isActive -> VividCyan
                                isLoopModeOpen || loopRegion.hasBounds -> accentColor
                                else -> TextSecondary
                            },
                            modifier = Modifier.size(24.dp)
                        )
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

            // Custom Canvas Now Playing Slider (Squiggly / Waveform / Minimal / Thick Pill)
            NowPlayingSlider(
                trackId = song.id,
                progressStateFlow = progressStateFlow,
                accentColor = accentColor,
                isPlaying = playbackState.isPlaying,
                sliderPreferences = sliderPreferences,
                onSeek = onSeek,
                onCycleSliderStyle = onCycleSliderStyle,
                loopRegion = loopRegion,
                isLoopModeOpen = isLoopModeOpen,
                onLoopStartDrag = onDragLoopStart,
                onLoopEndDrag = onDragLoopEnd,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )

            // Quick-Mark A-B Loop Control Pill
            AnimatedVisibility(
                visible = isLoopModeOpen,
                enter = expandVertically(tween(200)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(150))
            ) {
                LoopControlPill(
                    loopRegion = loopRegion,
                    accentColor = accentColor,
                    onSetA = { onSetLoopStart(null) },
                    onSetB = { onSetLoopEnd(null) },
                    onNudgeStart = onNudgeLoopStart,
                    onNudgeEnd = onNudgeLoopEnd,
                    onToggleLoop = onToggleLoop,
                    onClear = {
                        onClearLoop()
                        isLoopModeOpen = false
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

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

@Composable
private fun LoopControlPill(
    loopRegion: LoopRegion,
    accentColor: Color,
    onSetA: () -> Unit,
    onSetB: () -> Unit,
    onNudgeStart: (Long) -> Unit,
    onNudgeEnd: (Long) -> Unit,
    onToggleLoop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val startLabel = loopRegion.startMs?.let { formatLoopTimestamp(it) } ?: "--:--"
    val endLabel = loopRegion.endMs?.let { formatLoopTimestamp(it) } ?: "--:--"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .matteSurface(
                cornerRadius = DesignTokens.RadiusCard,
                surfaceColor = DesignTokens.ElevatedSurface,
                tintColor = accentColor,
                tintFraction = 0.08f
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Range Timestamp Readout + Loop ON/PAUSED toggle + Clear button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$startLabel → $endLabel",
                    style = TabularNumericStyle,
                    color = DesignTokens.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (loopRegion.isReady) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (loopRegion.isActive) accentColor.copy(alpha = 0.35f) else DarkSurfaceVariant
                            )
                            .border(
                                1.dp,
                                if (loopRegion.isActive) accentColor else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleLoop()
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (loopRegion.isActive) "LOOP ON" else "PAUSED",
                            color = if (loopRegion.isActive) Color.White else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClear()
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Clear",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Row 2: Set A + Nudge A  |  Set B + Nudge B
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LoopMarkButton(
                    label = "Set A",
                    highlightColor = accentColor,
                    isMarked = loopRegion.startMs != null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSetA()
                    }
                )
                LoopNudgeChip(
                    label = "-0.5s",
                    enabled = loopRegion.startMs != null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNudgeStart(-LoopRegion.NUDGE_STEP_MS)
                    }
                )
                LoopNudgeChip(
                    label = "+0.5s",
                    enabled = loopRegion.startMs != null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNudgeStart(LoopRegion.NUDGE_STEP_MS)
                    }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LoopMarkButton(
                    label = "Set B",
                    highlightColor = VividCyan,
                    isMarked = loopRegion.endMs != null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSetB()
                    }
                )
                LoopNudgeChip(
                    label = "-0.5s",
                    enabled = loopRegion.endMs != null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNudgeEnd(-LoopRegion.NUDGE_STEP_MS)
                    }
                )
                LoopNudgeChip(
                    label = "+0.5s",
                    enabled = loopRegion.endMs != null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNudgeEnd(LoopRegion.NUDGE_STEP_MS)
                    }
                )
            }
        }
    }
}

@Composable
private fun LoopMarkButton(
    label: String,
    highlightColor: Color,
    isMarked: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isMarked) highlightColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)
            )
            .border(
                1.dp,
                if (isMarked) highlightColor else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isMarked) Color.White else TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LoopNudgeChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.07f else 0.02f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) TextSecondary else TextTertiary.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatLoopTimestamp(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val minPrefix = if (minutes < 10L) "0" else ""
    val secPrefix = if (seconds < 10L) "0" else ""
    return "$minPrefix$minutes:$secPrefix$seconds"
}
