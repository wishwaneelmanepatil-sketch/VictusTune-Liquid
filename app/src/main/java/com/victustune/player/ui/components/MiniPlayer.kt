package com.victustune.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.victustune.player.data.model.PlaybackProgressState
import com.victustune.player.data.model.PlaybackUiState
import com.victustune.player.ui.theme.*
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MiniPlayer(
    playbackState: PlaybackUiState,
    progressStateFlow: StateFlow<PlaybackProgressState>,
    accentColor: Color,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return

    var totalDragOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .glassmorphic(cornerRadius = 18.dp, tintColor = accentColor, tintAlpha = 0.22f)
            .draggable(
                state = rememberDraggableState { delta ->
                    totalDragOffset += delta
                },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    if (totalDragOffset < -80f || velocity < -500f) {
                        onSkipNext()
                    } else if (totalDragOffset > 80f || velocity > 500f) {
                        onSkipPrevious()
                    }
                    totalDragOffset = 0f
                }
            )
            .clickable(onClick = onExpand)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album artwork thumbnail (downsampled for list/miniplayer display)
                val context = LocalContext.current
                val artworkRequest = remember(song.albumArtUri) {
                    ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .size(144, 144)
                        .precision(Precision.INEXACT)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = artworkRequest,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Song Title & Artist with Marquee
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    MarqueeText(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause Button with animated icon
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(40.dp)
                        .background(accentColor.copy(alpha = 0.25f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) VictusIcons.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Skip Next Button
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = VictusIcons.SkipNext,
                        contentDescription = "Next",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Isolated leaf micro-progress bar (only this leaf recomposes/draws on progress ticks)
            MiniPlayerProgressBar(
                progressStateFlow = progressStateFlow,
                accentColor = accentColor
            )
        }
    }
}

@Composable
private fun MiniPlayerProgressBar(
    progressStateFlow: StateFlow<PlaybackProgressState>,
    accentColor: Color
) {
    val progressState by progressStateFlow.collectAsStateWithLifecycle()
    val animatedProgress by animateFloatAsState(
        targetValue = progressState.progress,
        animationSpec = tween(durationMillis = 200),
        label = "miniPlayerProgress"
    )
    val trackColor = Color.White.copy(alpha = 0.1f)
    val progressBrush = remember(accentColor) {
        Brush.horizontalGradient(
            colors = listOf(accentColor.copy(alpha = 0.7f), accentColor, VividCyan)
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
    ) {
        drawRect(color = trackColor)
        val fillWidth = (size.width * animatedProgress.coerceIn(0f, 1f))
        if (fillWidth > 0f) {
            drawRect(
                brush = progressBrush,
                size = Size(width = fillWidth, height = size.height)
            )
        }
    }
}
