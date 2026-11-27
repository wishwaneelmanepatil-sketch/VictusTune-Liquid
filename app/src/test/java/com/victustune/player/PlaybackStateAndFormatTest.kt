package com.victustune.player

import com.victustune.player.data.model.PlaybackProgressState
import com.victustune.player.data.model.PlaybackUiState
import com.victustune.player.data.model.RepeatMode
import com.victustune.player.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStateAndFormatTest {

    @Test
    fun formatDurationMsFormatsZeroAndSingleDigitSeconds() {
        assertEquals("0:00", Song.formatDurationMs(0L))
        assertEquals("0:00", Song.formatDurationMs(-5000L))
        assertEquals("0:09", Song.formatDurationMs(9_000L))
        assertEquals("3:05", Song.formatDurationMs(185_000L))
        assertEquals("12:34", Song.formatDurationMs(754_000L))
    }

    @Test
    fun playbackProgressStateComputesClampedProgressFraction() {
        assertEquals(0f, PlaybackProgressState(0L, 0L).progress, 0.0001f)
        assertEquals(0.5f, PlaybackProgressState(50_000L, 100_000L).progress, 0.0001f)
        assertEquals(1f, PlaybackProgressState(150_000L, 100_000L).progress, 0.0001f)
        assertEquals(0f, PlaybackProgressState(-100L, 100_000L).progress, 0.0001f)
    }

    @Test
    fun playbackUiStateStartsEmptyAndIdle() {
        val state = PlaybackUiState()
        assertNull(state.currentSong)
        assertFalse(state.isPlaying)
        assertFalse(state.isLoading)
        assertEquals(RepeatMode.OFF, state.repeatMode)
        assertEquals(-1, state.currentQueueIndex)
    }
}
