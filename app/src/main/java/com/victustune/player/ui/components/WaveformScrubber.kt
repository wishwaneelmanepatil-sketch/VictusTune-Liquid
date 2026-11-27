package com.victustune.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victustune.player.data.model.PlaybackProgressState
import com.victustune.player.data.model.Song
import com.victustune.player.ui.theme.TextSecondary
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sin

@Composable
fun WaveformScrubber(
    progressStateFlow: StateFlow<PlaybackProgressState>,
    accentColor: Color,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progressState by progressStateFlow.collectAsStateWithLifecycle()
    val currentPositionMs = progressState.currentPositionMs
    val totalDurationMs = progressState.totalDurationMs

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val actualProgress = progressState.progress
    val displayedProgress = if (isDragging) dragProgress else actualProgress
    val displayedPositionMs = if (isDragging) (dragProgress * totalDurationMs).toLong() else currentPositionMs

    // Subtle wave animation only while actively playing (0 allocations when paused)
    val wavePhase = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "waveformAnim")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.28318f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        )
        animated
    } else {
        0f
    }

    WaveformScrubberContent(
        progressProvider = { displayedProgress },
        wavePhaseProvider = { wavePhase },
        isPlaying = isPlaying,
        isDragging = isDragging,
        displayedPositionMs = displayedPositionMs,
        totalDurationMs = totalDurationMs,
        accentColor = accentColor,
        onDragStart = { p ->
            isDragging = true
            dragProgress = p
        },
        onDragUpdate = { p -> dragProgress = p },
        onDragEnd = {
            isDragging = false
            onSeek((dragProgress * totalDurationMs).toLong())
        },
        onDragCancel = { isDragging = false },
        onTapSeek = { p -> onSeek((p * totalDurationMs).toLong()) },
        modifier = modifier
    )
}

@Composable
private fun WaveformScrubberContent(
    progressProvider: () -> Float,
    wavePhaseProvider: () -> Float,
    isPlaying: Boolean,
    isDragging: Boolean,
    displayedPositionMs: Long,
    totalDurationMs: Long,
    accentColor: Color,
    onDragStart: (Float) -> Unit,
    onDragUpdate: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onTapSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Waveform Bar Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onTapSeek(newProgress)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onDragStart((offset.x / size.width).coerceIn(0f, 1f))
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            onDragUpdate((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val barCount = 48
                val barWidth = 4.dp.toPx()
                val spacing = (canvasWidth - (barCount * barWidth)) / (barCount - 1).coerceAtLeast(1)

                val displayedProgress = progressProvider()
                val wavePhase = wavePhaseProvider()
                val activeColor = accentColor
                val inactiveColor = Color.White.copy(alpha = 0.2f)
                val thumbColor = Color.White

                for (i in 0 until barCount) {
                    val barFraction = i.toFloat() / barCount.toFloat()
                    val x = i * (barWidth + spacing)

                    // Wave pattern equation
                    val baseHeightFactor = 0.25f + 0.55f * sin((i * 0.35f)).let { kotlin.math.abs(it) }
                    val dynamicWave = if (isPlaying && !isDragging) {
                        0.15f * sin(i * 0.4f + wavePhase)
                    } else 0f

                    val heightFraction = (baseHeightFactor + dynamicWave).coerceIn(0.15f, 0.95f)
                    val barH = canvasHeight * heightFraction
                    val y = (canvasHeight - barH) / 2f

                    val isPast = barFraction <= displayedProgress
                    val color = if (isPast) activeColor else inactiveColor

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }

                // Scrubber Playhead Thumb indicator
                val thumbX = (displayedProgress * canvasWidth).coerceIn(0f, canvasWidth)
                drawCircle(
                    color = thumbColor,
                    radius = if (isDragging) 8.dp.toPx() else 5.dp.toPx(),
                    center = Offset(thumbX, canvasHeight / 2f)
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.4f),
                    radius = if (isDragging) 14.dp.toPx() else 8.dp.toPx(),
                    center = Offset(thumbX, canvasHeight / 2f)
                )
            }
        }

        // Timestamps
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Song.formatDurationMs(displayedPositionMs),
                fontSize = 12.sp,
                color = TextSecondary
            )
            val remainingMs = (totalDurationMs - displayedPositionMs).coerceAtLeast(0L)
            Text(
                text = "-${Song.formatDurationMs(remainingMs)}",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}
