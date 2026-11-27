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

@Immutable
data class LoopRegion(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val isActive: Boolean = false
) {
    val hasBounds: Boolean
        get() = startMs != null || endMs != null

    val isReady: Boolean
        get() = startMs != null && endMs != null && (endMs - startMs) >= MIN_LOOP_DURATION_MS

    val isValidActive: Boolean
        get() = isActive && isReady

    fun withStartSet(ms: Long, maxDurationMs: Long = Long.MAX_VALUE, fromDrag: Boolean = false): LoopRegion {
        val safeMax = if (maxDurationMs > 0L) maxDurationMs else Long.MAX_VALUE
        val clampedMs = ms.coerceIn(0L, safeMax)
        val currentEnd = endMs

        if (currentEnd == null) {
            return copy(startMs = clampedMs, isActive = false)
        }

        if (fromDrag) {
            val maxStart = (currentEnd - MIN_LOOP_DURATION_MS).coerceAtLeast(0L)
            return copy(startMs = clampedMs.coerceAtMost(maxStart))
        }

        // Quick-mark Set A: if new A > B (or cannot satisfy min 1s loop before B), clear B so user marks a fresh end point
        if (clampedMs > currentEnd) {
            return LoopRegion(startMs = clampedMs, endMs = null, isActive = false)
        }
        val gapAdjustedStart = if (currentEnd - clampedMs < MIN_LOOP_DURATION_MS) {
            if (currentEnd >= MIN_LOOP_DURATION_MS) {
                (currentEnd - MIN_LOOP_DURATION_MS).coerceAtLeast(0L)
            } else {
                return LoopRegion(startMs = clampedMs, endMs = null, isActive = false)
            }
        } else {
            clampedMs
        }
        return copy(startMs = gapAdjustedStart)
    }

    fun withEndSet(ms: Long, maxDurationMs: Long = Long.MAX_VALUE, fromDrag: Boolean = false): LoopRegion {
        val safeMax = if (maxDurationMs > 0L) maxDurationMs else Long.MAX_VALUE
        val currentStart = startMs ?: 0L
        val minEnd = currentStart + MIN_LOOP_DURATION_MS

        if (safeMax in 1L until MIN_LOOP_DURATION_MS) {
            return LoopRegion(startMs = 0L, endMs = null, isActive = false)
        }

        val effectiveStart: Long
        val clampedEnd: Long
        if (fromDrag) {
            effectiveStart = currentStart.coerceIn(0L, (safeMax - MIN_LOOP_DURATION_MS).coerceAtLeast(0L))
            clampedEnd = ms.coerceIn(effectiveStart + MIN_LOOP_DURATION_MS, safeMax)
            return copy(startMs = effectiveStart, endMs = clampedEnd)
        } else {
            // Setting B before A defaults A to 0ms; enforce at least 1000ms loop span and immediately activate
            val boundedEnd = ms.coerceIn(0L, safeMax)
            if (boundedEnd >= minEnd) {
                effectiveStart = currentStart
                clampedEnd = boundedEnd
            } else if (safeMax >= minEnd) {
                effectiveStart = currentStart
                clampedEnd = minEnd.coerceAtMost(safeMax)
            } else {
                clampedEnd = boundedEnd.coerceAtLeast(MIN_LOOP_DURATION_MS.coerceAtMost(safeMax))
                effectiveStart = (clampedEnd - MIN_LOOP_DURATION_MS).coerceAtLeast(0L)
            }
            val valid = (clampedEnd - effectiveStart) >= MIN_LOOP_DURATION_MS
            return LoopRegion(
                startMs = effectiveStart,
                endMs = clampedEnd,
                isActive = valid
            )
        }
    }

    fun toggled(): LoopRegion {
        return if (isReady) {
            copy(isActive = !isActive)
        } else {
            copy(isActive = false)
        }
    }

    companion object {
        const val MIN_LOOP_DURATION_MS = 1000L
        const val NUDGE_STEP_MS = 500L
    }
}

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
    val currentQueueIndex: Int = -1,
    val loopRegion: LoopRegion = LoopRegion()
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
