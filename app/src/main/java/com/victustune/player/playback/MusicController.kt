package com.victustune.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.victustune.player.data.local.datastore.PreferencesManager
import com.victustune.player.data.model.PlaybackState
import com.victustune.player.data.model.RepeatMode
import com.victustune.player.data.model.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MusicController(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentSongList = listOf<Song>()
    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Int?> = _sleepTimerRemainingSeconds.asStateFlow()

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupController()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupController() {
        val controller = mediaController ?: return

        controller.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSong(controller)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isLoading = playbackState == Player.STATE_BUFFERING
                _playbackState.update {
                    it.copy(
                        isLoading = isLoading,
                        totalDurationMs = controller.duration.coerceAtLeast(0L),
                        bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L)
                    )
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _playbackState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val mode = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
                _playbackState.update { it.copy(repeatMode = mode) }
            }
        })

        // Restore initial state from controller
        updateCurrentSong(controller)
        _playbackState.update {
            it.copy(
                isPlaying = controller.isPlaying,
                isShuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
            )
        }
    }

    private fun updateCurrentSong(controller: MediaController) {
        val index = controller.currentMediaItemIndex
        val currentMediaItem = controller.currentMediaItem
        val currentSong = currentSongList.find {
            it.id.toString() == currentMediaItem?.mediaId ||
            (it.isRemote && it.remoteVideoId == currentMediaItem?.mediaId)
        } ?: if (index in currentSongList.indices) currentSongList[index] else null

        val duration = if (controller.duration > 0) controller.duration else (currentSong?.durationMs ?: 0L)

        _playbackState.update {
            it.copy(
                currentSong = currentSong,
                currentQueueIndex = index,
                totalDurationMs = duration,
                currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L)
            )
        }
    }

    private fun startPositionTicker() {
        stopPositionTicker()
        tickerJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        val pos = controller.currentPosition.coerceAtLeast(0L)
                        val dur = if (controller.duration > 0) controller.duration else _playbackState.value.totalDurationMs
                        val buf = controller.bufferedPosition.coerceAtLeast(0L)
                        _playbackState.update {
                            it.copy(
                                currentPositionMs = pos,
                                totalDurationMs = dur,
                                bufferedPositionMs = buf
                            )
                        }
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopPositionTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val controller = mediaController ?: return
        currentSongList = songs.toList()

        val mediaItems = songs.map { songToMediaItem(it) }
        controller.stop()
        controller.clearMediaItems()
        controller.setMediaItems(mediaItems, startIndex.coerceIn(0, songs.size - 1), 0L)
        controller.prepare()
        controller.play()

        _playbackState.update {
            it.copy(
                queue = songs,
                currentQueueIndex = startIndex,
                currentSong = songs.getOrNull(startIndex)
            )
        }
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        val index = queue.indexOfFirst { it.id == song.id }.let { if (it == -1) 0 else it }
        val targetQueue = if (queue.none { it.id == song.id }) listOf(song) + queue else queue
        playQueue(targetQueue, if (index == -1) 0 else index)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun playNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    fun playPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newShuffle = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newShuffle
        _playbackState.update { it.copy(isShuffleEnabled = newShuffle) }
        scope.launch { preferencesManager.setShuffle(newShuffle) }
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }

        val media3Repeat = when (nextMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }

        controller.repeatMode = media3Repeat
        _playbackState.update { it.copy(repeatMode = nextMode) }
        scope.launch { preferencesManager.setRepeatMode(nextMode) }
    }

    fun addToQueue(song: Song) {
        val controller = mediaController ?: return
        currentSongList = currentSongList + song
        controller.addMediaItem(songToMediaItem(song))
        _playbackState.update { it.copy(queue = currentSongList) }
    }

    fun playSongNext(song: Song) {
        val controller = mediaController ?: return
        val nextIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(currentSongList.size)
        val updated = currentSongList.toMutableList().apply {
            add(nextIndex, song)
        }
        currentSongList = updated
        controller.addMediaItem(nextIndex, songToMediaItem(song))
        _playbackState.update { it.copy(queue = currentSongList) }
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in currentSongList.indices) {
            val updated = currentSongList.toMutableList().apply { removeAt(index) }
            currentSongList = updated
            controller.removeMediaItem(index)
            _playbackState.update { it.copy(queue = currentSongList) }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        if (fromIndex in currentSongList.indices && toIndex in currentSongList.indices) {
            val updated = currentSongList.toMutableList()
            val item = updated.removeAt(fromIndex)
            updated.add(toIndex, item)
            currentSongList = updated
            controller.moveMediaItem(fromIndex, toIndex)
            _playbackState.update { it.copy(queue = currentSongList) }
        }
    }

    fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return

        val totalSeconds = minutes * 60
        _sleepTimerRemainingSeconds.value = totalSeconds

        sleepTimerJob = scope.launch {
            var rem = totalSeconds
            while (rem > 0 && isActive) {
                delay(1000)
                rem--
                _sleepTimerRemainingSeconds.value = rem
            }
            if (rem == 0) {
                mediaController?.pause()
                _sleepTimerRemainingSeconds.value = null
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingSeconds.value = null
    }

    fun updateCurrentSongFavorite(isFavorite: Boolean) {
        _playbackState.update { state ->
            val updatedSong = state.currentSong?.copy(isFavorite = isFavorite)
            val updatedQueue = state.queue.map { song ->
                if (song.id == state.currentSong?.id) song.copy(isFavorite = isFavorite) else song
            }
            state.copy(
                currentSong = updatedSong,
                queue = updatedQueue
            )
        }
        currentSongList = currentSongList.map { song ->
            if (song.id == _playbackState.value.currentSong?.id) song.copy(isFavorite = isFavorite) else song
        }
    }

    private fun songToMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
            .build()

        val mediaId = if (song.isRemote) (song.remoteVideoId ?: song.id.toString()) else song.id.toString()
        val uri = if (song.isRemote) {
            val vid = song.remoteVideoId ?: song.id.toString()
            android.net.Uri.parse("stream://$vid")
        } else {
            song.contentUri
        }

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun release() {
        stopPositionTicker()
        cancelSleepTimer()
        scope.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
