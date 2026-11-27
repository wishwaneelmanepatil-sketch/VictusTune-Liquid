package com.victustune.player.data.model

import androidx.compose.runtime.Immutable
import java.util.UUID

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

@Immutable
data class QueueEntry(
    val uid: String = UUID.randomUUID().toString(),
    val song: Song
)

/** Low-frequency playback metadata & queue state. Excludes position ticks so UI trees do not recompose 5x/sec. */
@Immutable
data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList(),
    val queueEntries: List<QueueEntry> = emptyList(),
    val currentQueueIndex: Int = -1
)

/** High-frequency playback progress state collected only by leaf scrubber / timestamp / progress bar composables. */
@Immutable
data class PlaybackProgressState(
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L
) {
    val progress: Float
        get() = if (totalDurationMs > 0) (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

typealias PlaybackState = PlaybackUiState

@Immutable
data class LyricLine(
    val timeMs: Long,
    val text: String
)
