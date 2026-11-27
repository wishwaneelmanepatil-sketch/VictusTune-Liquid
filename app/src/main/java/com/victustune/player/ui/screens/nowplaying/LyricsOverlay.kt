package com.victustune.player.ui.screens.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victustune.player.data.model.LyricLine
import com.victustune.player.data.model.PlaybackProgressState
import com.victustune.player.ui.theme.TextPrimary
import com.victustune.player.ui.theme.TextSecondary
import com.victustune.player.ui.theme.TextTertiary
import kotlinx.coroutines.flow.StateFlow

@Composable
fun LyricsOverlay(
    lyrics: List<LyricLine>,
    progressStateFlow: StateFlow<PlaybackProgressState>,
    accentColor: Color,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val progressState by progressStateFlow.collectAsStateWithLifecycle()

    // Derive active index so the LazyColumn only recomposes when the highlighted lyric line changes
    val activeIndex by remember(lyrics) {
        derivedStateOf {
            val pos = progressState.currentPositionMs
            val idx = lyrics.indexOfLast { it.timeMs <= pos }
            if (idx == -1) 0 else idx
        }
    }

    // Auto-scroll to active lyric line
    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty() && activeIndex in lyrics.indices) {
            listState.animateScrollToItem(
                index = (activeIndex - 2).coerceAtLeast(0),
                scrollOffset = 0
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Synced Lyrics",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close lyrics",
                        tint = TextSecondary
                    )
                }
            }

            if (lyrics.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No synced lyrics available",
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 100.dp, bottom = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    itemsIndexed(
                        items = lyrics,
                        key = { index, line -> "${index}_${line.timeMs}" },
                        contentType = { _, _ -> "lyric_line" }
                    ) { index, line ->
                        val isCurrent = index == activeIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isCurrent) Color.White else TextTertiary,
                            animationSpec = tween(durationMillis = 300),
                            label = "lyricColor"
                        )
                        val textAlpha by animateFloatAsState(
                            targetValue = if (isCurrent) 1.0f else 0.45f,
                            animationSpec = tween(durationMillis = 300),
                            label = "lyricAlpha"
                        )

                        Text(
                            text = line.text,
                            fontSize = if (isCurrent) 22.sp else 17.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = textColor,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(textAlpha)
                                .clickable { onSeek(line.timeMs) }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
