package com.victustune.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victustune.player.data.model.LoopRegion
import com.victustune.player.data.model.PlaybackProgressState
import com.victustune.player.data.model.SliderColorMode
import com.victustune.player.data.model.SliderPreferences
import com.victustune.player.data.model.SliderStyle
import com.victustune.player.data.model.Song
import com.victustune.player.ui.theme.DesignTokens
import com.victustune.player.ui.theme.TabularNumericStyle
import com.victustune.player.ui.theme.VividCyan
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom Canvas-drawn Now Playing scrubber collecting [PlaybackProgressState] strictly inside
 * this leaf composable so high-frequency ticks never recompose the parent screen.
 */
@Composable
fun NowPlayingSlider(
    trackId: Long,
    progressStateFlow: StateFlow<PlaybackProgressState>,
    accentColor: Color,
    isPlaying: Boolean,
    sliderPreferences: SliderPreferences,
    onSeek: (Long) -> Unit,
    onCycleSliderStyle: (() -> Unit)? = null,
    loopRegion: LoopRegion = LoopRegion(),
    isLoopModeOpen: Boolean = false,
    onLoopStartDrag: (Long) -> Unit = {},
    onLoopEndDrag: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val progressState by progressStateFlow.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val totalDurationMs = progressState.totalDurationMs
    val currentPositionMs = progressState.currentPositionMs

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var hitBoundaryHaptic by remember { mutableStateOf(false) }

    val displayedProgress = if (isDragging) dragProgress else progressState.progress
    val displayedPositionMs = if (isDragging) (dragProgress * totalDurationMs).toLong() else currentPositionMs

    val resolvedColor = when (sliderPreferences.colorMode) {
        SliderColorMode.DYNAMIC -> accentColor
        SliderColorMode.MONOCHROME -> Color.White
    }

    // Squiggly wave amplitude gently flattens to 0 when paused
    val squigglyAmplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying && sliderPreferences.style == SliderStyle.SQUIGGLY && !isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "squigglyAmp"
    )

    val wavePhase = if (isPlaying && sliderPreferences.style == SliderStyle.SQUIGGLY) {
        val infiniteTransition = rememberInfiniteTransition(label = "squigglyPhaseAnim")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.28318f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        )
        phase
    } else {
        0f
    }

    // Deterministic per-track waveform bars for WAVEFORM style
    val waveformBarFactors = remember(trackId) {
        val rng = Random(trackId)
        FloatArray(48) { idx ->
            val envelope = 0.35f + 0.45f * kotlin.math.abs(sin(idx * 0.28f))
            (envelope * (0.65f + 0.5f * rng.nextFloat())).coerceIn(0.18f, 0.95f)
        }
    }

    // Minimal style smoothly thickens track while dragging
    val minimalTrackHeightDp by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 3.dp,
        animationSpec = tween(160),
        label = "minimalHeight"
    )

    NowPlayingSliderContent(
        progressProvider = { displayedProgress },
        wavePhaseProvider = { wavePhase },
        squigglyAmplitudeProvider = { squigglyAmplitudeFactor },
        waveformBarFactors = waveformBarFactors,
        minimalTrackHeightPx = with(LocalDensity.current) { minimalTrackHeightDp.toPx() },
        style = sliderPreferences.style,
        showThumb = sliderPreferences.showThumb,
        sliderColor = resolvedColor,
        isDragging = isDragging,
        displayedPositionMs = displayedPositionMs,
        totalDurationMs = totalDurationMs,
        loopRegion = loopRegion,
        isLoopModeOpen = isLoopModeOpen,
        onLoopStartDrag = onLoopStartDrag,
        onLoopEndDrag = onLoopEndDrag,
        onDragStart = { p ->
            isDragging = true
            hitBoundaryHaptic = false
            dragProgress = p
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        onDragUpdate = { p ->
            val clamped = p.coerceIn(0f, 1f)
            val atBound = clamped <= 0.001f || clamped >= 0.999f
            if (atBound && !hitBoundaryHaptic) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                hitBoundaryHaptic = true
            } else if (!atBound) {
                hitBoundaryHaptic = false
            }
            dragProgress = clamped
        },
        onDragEnd = {
            isDragging = false
            onSeek((dragProgress * totalDurationMs).toLong())
        },
        onDragCancel = { isDragging = false },
        onTapSeek = { p ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSeek((p * totalDurationMs).toLong())
        },
        onLongPress = {
            if (onCycleSliderStyle != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCycleSliderStyle()
            }
        },
        modifier = modifier
    )
}

@Composable
private fun NowPlayingSliderContent(
    progressProvider: () -> Float,
    wavePhaseProvider: () -> Float,
    squigglyAmplitudeProvider: () -> Float,
    waveformBarFactors: FloatArray,
    minimalTrackHeightPx: Float,
    style: SliderStyle,
    showThumb: Boolean,
    sliderColor: Color,
    isDragging: Boolean,
    displayedPositionMs: Long,
    totalDurationMs: Long,
    loopRegion: LoopRegion,
    isLoopModeOpen: Boolean,
    onLoopStartDrag: (Long) -> Unit,
    onLoopEndDrag: (Long) -> Unit,
    onDragStart: (Float) -> Unit,
    onDragUpdate: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onTapSeek: (Float) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    var activeDragStartMs by remember { mutableStateOf<Long?>(null) }
    var activeDragEndMs by remember { mutableStateOf<Long?>(null) }

    val latestLoopRegion by rememberUpdatedState(loopRegion)
    val latestTotalDurationMs by rememberUpdatedState(totalDurationMs)
    val latestOnLoopStartDrag by rememberUpdatedState(onLoopStartDrag)
    val latestOnLoopEndDrag by rememberUpdatedState(onLoopEndDrag)

    val effectiveStartMs = activeDragStartMs ?: loopRegion.startMs
    val effectiveEndMs = activeDragEndMs ?: loopRegion.endMs

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Draggable A / B Range Handle Strip when A-B Loop editor is open
        if (isLoopModeOpen && totalDurationMs > 0L && (effectiveStartMs != null || effectiveEndMs != null)) {
            val handleWidthDp = 24.dp
            val handleWidthPx = with(density) { handleWidthDp.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
            ) {
                if (effectiveStartMs != null) {
                    val startFraction = (effectiveStartMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    val xPx = ((startFraction * trackWidthPx) - (handleWidthPx / 2f))
                        .coerceIn(0f, (trackWidthPx - handleWidthPx).coerceAtLeast(0f))
                        .roundToInt()

                    var lastHapticBucket by remember { mutableLongStateOf(-1L) }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(xPx, 0) }
                            .size(width = handleWidthDp, height = 20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(sliderColor)
                            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                            .pointerInput(totalDurationMs) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val initStart = latestLoopRegion.startMs ?: 0L
                                        activeDragStartMs = initStart
                                        lastHapticBucket = initStart / 250L
                                    },
                                    onDragEnd = {
                                        activeDragStartMs?.let { latestOnLoopStartDrag(it) }
                                        activeDragStartMs = null
                                    },
                                    onDragCancel = {
                                        activeDragStartMs = null
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        val duration = latestTotalDurationMs
                                        if (duration <= 0L || trackWidthPx <= 0f) return@detectHorizontalDragGestures
                                        val deltaMs = ((dragAmount / trackWidthPx) * duration).roundToLong()
                                        val current = activeDragStartMs ?: latestLoopRegion.startMs ?: 0L
                                        val upperBound = (latestLoopRegion.endMs?.let {
                                            (it - LoopRegion.MIN_LOOP_DURATION_MS).coerceAtLeast(0L)
                                        } ?: duration)
                                        val clamped = (current + deltaMs).coerceIn(0L, upperBound)
                                        activeDragStartMs = clamped
                                        val bucket = clamped / 250L
                                        if (bucket != lastHapticBucket) {
                                            lastHapticBucket = bucket
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        latestOnLoopStartDrag(clamped)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            color = if (sliderColor == Color.White) DesignTokens.CanvasBackground else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (effectiveEndMs != null) {
                    val endFraction = (effectiveEndMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    val xPx = ((endFraction * trackWidthPx) - (handleWidthPx / 2f))
                        .coerceIn(0f, (trackWidthPx - handleWidthPx).coerceAtLeast(0f))
                        .roundToInt()

                    var lastHapticBucket by remember { mutableLongStateOf(-1L) }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(xPx, 0) }
                            .size(width = handleWidthDp, height = 20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(VividCyan)
                            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                            .pointerInput(totalDurationMs) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val initEnd = latestLoopRegion.endMs ?: latestTotalDurationMs
                                        activeDragEndMs = initEnd
                                        lastHapticBucket = initEnd / 250L
                                    },
                                    onDragEnd = {
                                        activeDragEndMs?.let { latestOnLoopEndDrag(it) }
                                        activeDragEndMs = null
                                    },
                                    onDragCancel = {
                                        activeDragEndMs = null
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        val duration = latestTotalDurationMs
                                        if (duration <= 0L || trackWidthPx <= 0f) return@detectHorizontalDragGestures
                                        val deltaMs = ((dragAmount / trackWidthPx) * duration).roundToLong()
                                        val current = activeDragEndMs ?: latestLoopRegion.endMs ?: duration
                                        val lowerBound = ((latestLoopRegion.startMs ?: 0L) + LoopRegion.MIN_LOOP_DURATION_MS)
                                            .coerceAtMost(duration)
                                        val clamped = (current + deltaMs).coerceIn(lowerBound, duration)
                                        activeDragEndMs = clamped
                                        val bucket = clamped / 250L
                                        if (bucket != lastHapticBucket) {
                                            lastHapticBucket = bucket
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        latestOnLoopEndDrag(clamped)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "B",
                            color = DesignTokens.CanvasBackground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Main Interactive Canvas Scrubber
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            onTapSeek(newProgress)
                        },
                        onLongPress = { onLongPress() }
                    )
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
                val loopStartFrac = if (totalDurationMs > 0L && effectiveStartMs != null) {
                    (effectiveStartMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                } else null
                val loopEndFrac = if (totalDurationMs > 0L && effectiveEndMs != null) {
                    (effectiveEndMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                } else null

                if ((isLoopModeOpen || loopRegion.isActive) && loopStartFrac != null && loopEndFrac != null) {
                    val leftX = loopStartFrac * size.width
                    val rightX = loopEndFrac * size.width
                    val bandWidth = (rightX - leftX).coerceAtLeast(2.dp.toPx())
                    val bandAlpha = if (loopRegion.isActive) 0.22f else 0.12f
                    drawRoundRect(
                        color = sliderColor.copy(alpha = bandAlpha),
                        topLeft = Offset(leftX, 2.dp.toPx()),
                        size = Size(bandWidth, size.height - 4.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }

                val progress = progressProvider().coerceIn(0f, 1f)
                val inactiveColor = Color.White.copy(alpha = 0.16f)

                when (style) {
                    SliderStyle.SQUIGGLY -> drawSquigglySlider(
                        progress = progress,
                        activeColor = sliderColor,
                        inactiveColor = inactiveColor,
                        wavePhase = wavePhaseProvider(),
                        amplitudeFactor = squigglyAmplitudeProvider(),
                        showThumb = showThumb,
                        isDragging = isDragging
                    )
                    SliderStyle.WAVEFORM -> drawWaveformSlider(
                        progress = progress,
                        activeColor = sliderColor,
                        inactiveColor = inactiveColor,
                        barFactors = waveformBarFactors,
                        loopStartFrac = if (isLoopModeOpen || loopRegion.isActive) loopStartFrac else null,
                        loopEndFrac = if (isLoopModeOpen || loopRegion.isActive) loopEndFrac else null,
                        isDragging = isDragging
                    )
                    SliderStyle.MINIMAL -> drawMinimalSlider(
                        progress = progress,
                        activeColor = sliderColor,
                        inactiveColor = inactiveColor,
                        trackHeightPx = minimalTrackHeightPx,
                        showThumb = showThumb,
                        isDragging = isDragging
                    )
                    SliderStyle.THICK_PILL -> drawThickPillSlider(
                        progress = progress,
                        activeColor = sliderColor,
                        inactiveColor = inactiveColor
                    )
                }

                // Vertical A & B markers on top of any slider style when bounds exist
                if (isLoopModeOpen || loopRegion.hasBounds) {
                    loopStartFrac?.let { sf ->
                        val sx = (sf * size.width).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
                        drawLine(
                            color = sliderColor,
                            start = Offset(sx, 2.dp.toPx()),
                            end = Offset(sx, size.height - 2.dp.toPx()),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    loopEndFrac?.let { ef ->
                        val ex = (ef * size.width).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
                        drawLine(
                            color = VividCyan,
                            start = Offset(ex, 2.dp.toPx()),
                            end = Offset(ex, size.height - 2.dp.toPx()),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
        }

        // Leaf Timestamps with Tabular Numerals (tnum)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Song.formatDurationMs(displayedPositionMs),
                style = TabularNumericStyle,
                fontSize = 12.sp,
                color = DesignTokens.TextSecondary
            )
            val remainingMs = (totalDurationMs - displayedPositionMs).coerceAtLeast(0L)
            Text(
                text = "-${Song.formatDurationMs(remainingMs)}",
                style = TabularNumericStyle,
                fontSize = 12.sp,
                color = DesignTokens.TextSecondary
            )
        }
    }
}

/** Compact live Canvas preview used in Settings -> Appearance for each [SliderStyle]. */
@Composable
fun SliderStylePreview(
    style: SliderStyle,
    accentColor: Color,
    showThumb: Boolean,
    modifier: Modifier = Modifier
) {
    val previewBars = remember {
        floatArrayOf(0.4f, 0.7f, 0.55f, 0.9f, 0.6f, 0.85f, 0.45f, 0.75f, 0.95f, 0.5f, 0.65f, 0.4f, 0.8f, 0.55f, 0.7f, 0.35f)
    }
    Canvas(modifier = modifier.height(20.dp).fillMaxWidth()) {
        val activeColor = accentColor
        val inactiveColor = Color.White.copy(alpha = 0.2f)
        val previewProgress = 0.58f
        when (style) {
            SliderStyle.SQUIGGLY -> drawSquigglySlider(
                progress = previewProgress,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                wavePhase = 0.6f,
                amplitudeFactor = 1f,
                showThumb = showThumb,
                isDragging = false
            )
            SliderStyle.WAVEFORM -> drawWaveformSlider(
                progress = previewProgress,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                barFactors = previewBars,
                loopStartFrac = null,
                loopEndFrac = null,
                isDragging = false
            )
            SliderStyle.MINIMAL -> drawMinimalSlider(
                progress = previewProgress,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                trackHeightPx = 3.dp.toPx(),
                showThumb = showThumb,
                isDragging = false
            )
            SliderStyle.THICK_PILL -> drawThickPillSlider(
                progress = previewProgress,
                activeColor = activeColor,
                inactiveColor = inactiveColor
            )
        }
    }
}

private fun DrawScope.drawSquigglySlider(
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    wavePhase: Float,
    amplitudeFactor: Float,
    showThumb: Boolean,
    isDragging: Boolean
) {
    val centerY = size.height / 2f
    val playheadX = (progress * size.width).coerceIn(0f, size.width)
    val strokeWidth = 3.dp.toPx()

    // Inactive straight line segment ahead of playhead
    if (playheadX < size.width) {
        drawLine(
            color = inactiveColor,
            start = Offset(playheadX, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    // Active expressive sine wave up to playhead (gently flattens when paused)
    if (playheadX > 1f) {
        val maxAmplitude = 4.dp.toPx() * amplitudeFactor
        if (maxAmplitude <= 0.15f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, centerY),
                end = Offset(playheadX, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        } else {
            val path = Path()
            val stepPx = 3.dp.toPx().coerceAtLeast(2f)
            val wavelengthPx = 24.dp.toPx()
            val twoPi = 6.2831853f
            var x = 0f
            path.moveTo(0f, centerY + maxAmplitude * sin(wavePhase))
            while (x < playheadX) {
                x = (x + stepPx).coerceAtMost(playheadX)
                val angle = (x / wavelengthPx) * twoPi + wavePhase
                // Taper slightly near endpoints so wave connects cleanly at playhead
                val edgeTaper = ((playheadX - x) / 8.dp.toPx()).coerceIn(0f, 1f)
                val y = centerY + (maxAmplitude * edgeTaper) * sin(angle)
                path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = activeColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }

    if (showThumb || isDragging) {
        val pillWidth = if (isDragging) 4.dp.toPx() else 3.dp.toPx()
        val pillHeight = if (isDragging) 18.dp.toPx() else 14.dp.toPx()
        drawRoundRect(
            color = Color.White,
            topLeft = Offset((playheadX - pillWidth / 2f).coerceIn(0f, size.width - pillWidth), centerY - pillHeight / 2f),
            size = Size(pillWidth, pillHeight),
            cornerRadius = CornerRadius(pillWidth / 2f, pillWidth / 2f)
        )
    }
}

private fun DrawScope.drawWaveformSlider(
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    barFactors: FloatArray,
    loopStartFrac: Float?,
    loopEndFrac: Float?,
    isDragging: Boolean
) {
    val barCount = barFactors.size.coerceAtLeast(1)
    val barWidth = (size.width / (barCount * 1.85f)).coerceIn(2.5.dp.toPx(), 4.dp.toPx())
    val spacing = (size.width - barCount * barWidth) / (barCount - 1).coerceAtLeast(1)

    for (i in 0 until barCount) {
        val barFraction = i.toFloat() / (barCount - 1).coerceAtLeast(1).toFloat()
        val x = i * (barWidth + spacing)
        val barH = (size.height * 0.85f) * barFactors[i]
        val y = (size.height - barH) / 2f

        val isPast = barFraction <= progress
        val inLoop = loopStartFrac != null && loopEndFrac != null && barFraction in loopStartFrac..loopEndFrac
        val color = when {
            isPast -> activeColor
            inLoop -> VividCyan.copy(alpha = 0.45f)
            else -> inactiveColor
        }

        drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(barWidth, barH),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )
    }

    if (isDragging) {
        val thumbX = (progress * size.width).coerceIn(0f, size.width)
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = Offset(thumbX, size.height / 2f)
        )
    }
}

private fun DrawScope.drawMinimalSlider(
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    trackHeightPx: Float,
    showThumb: Boolean,
    isDragging: Boolean
) {
    val centerY = size.height / 2f
    val top = centerY - trackHeightPx / 2f
    val corner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

    // Full background track
    drawRoundRect(
        color = inactiveColor,
        topLeft = Offset(0f, top),
        size = Size(size.width, trackHeightPx),
        cornerRadius = corner
    )

    // Active progress fill
    val fillWidth = (progress * size.width).coerceIn(0f, size.width)
    if (fillWidth > 0f) {
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, top),
            size = Size(fillWidth, trackHeightPx),
            cornerRadius = corner
        )
    }

    if (isDragging) {
        drawCircle(
            color = activeColor.copy(alpha = 0.28f),
            radius = 12.dp.toPx(),
            center = Offset(fillWidth, centerY)
        )
        drawCircle(
            color = Color.White,
            radius = 5.5.dp.toPx(),
            center = Offset(fillWidth, centerY)
        )
    } else if (showThumb) {
        drawCircle(
            color = Color.White,
            radius = 4.dp.toPx(),
            center = Offset(fillWidth, centerY)
        )
    }
}

private fun DrawScope.drawThickPillSlider(
    progress: Float,
    activeColor: Color,
    inactiveColor: Color
) {
    val pillHeight = 8.dp.toPx()
    val top = (size.height - pillHeight) / 2f
    val corner = CornerRadius(pillHeight / 2f, pillHeight / 2f)

    drawRoundRect(
        color = inactiveColor,
        topLeft = Offset(0f, top),
        size = Size(size.width, pillHeight),
        cornerRadius = corner
    )

    val fillWidth = (progress * size.width).coerceIn(0f, size.width)
    if (fillWidth > 0f) {
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, top),
            size = Size(fillWidth.coerceAtLeast(pillHeight.coerceAtMost(size.width)), pillHeight),
            cornerRadius = corner
        )
    }
}
