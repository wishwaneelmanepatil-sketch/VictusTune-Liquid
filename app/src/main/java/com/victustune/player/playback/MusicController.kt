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
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.victustune.player.data.local.datastore.PreferencesManager
import com.victustune.player.data.model.LoopRegion
import com.victustune.player.data.model.PlaybackProgressState
import com.victustune.player.data.model.PlaybackUiState
import com.victustune.player.data.model.QueueEntry
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

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private val _progressState = MutableStateFlow(PlaybackProgressState())
    val progressState: StateFlow<PlaybackProgressState> = _progressState.asStateFlow()

    private var currentSongList = listOf<Song>()
    private var currentQueueEntries = listOf<QueueEntry>()
    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    @Volatile
    private var isAppInForeground: Boolean = true

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
        controllerFuture = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    LoopSessionCommands.fromBundle(extras)?.let { loop ->
                        _playbackState.update { it.copy(loopRegion = loop) }
                    }
                }
            })
            .buildAsync()
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
                _playbackState.update { it.copy(loopRegion = LoopRegion()) }
                updateCurrentSong(controller)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    updateCurrentSong(controller)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying && isAppInForeground) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isLoading = playbackState == Player.STATE_BUFFERING
                _playbackState.update { it.copy(isLoading = isLoading) }
                _progressState.update {
                    it.copy(
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
        val mediaId = controller.currentMediaItem?.mediaId
        val songAtIndex = currentSongList.getOrNull(index)
        val matchesAtIndex = mediaId == null || (songAtIndex != null &&
            (songAtIndex.id.toString() == mediaId || (songAtIndex.isRemote && songAtIndex.remoteVideoId == mediaId)))

        val currentSong = if (songAtIndex != null && matchesAtIndex) {
            songAtIndex
        } else {
            currentSongList.find {
                it.id.toString() == mediaId ||
                (it.isRemote && it.remoteVideoId == mediaId)
            } ?: songAtIndex
        }

        val duration = if (controller.duration > 0) controller.duration else (currentSong?.durationMs ?: 0L)

        _playbackState.update {
            it.copy(
                currentSong = currentSong,
                currentQueueIndex = index
            )
        }
        _progressState.value = PlaybackProgressState(
            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
            totalDurationMs = duration,
            bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L)
        )
    }

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
        if (!inForeground) {
            stopPositionTicker()
            return
        }
        val controller = mediaController ?: return
        val pos = controller.currentPosition.coerceAtLeast(0L)
        val dur = if (controller.duration > 0) controller.duration else _progressState.value.totalDurationMs
        val buf = controller.bufferedPosition.coerceAtLeast(0L)
        _progressState.value = PlaybackProgressState(
            currentPositionMs = pos,
            totalDurationMs = dur,
            bufferedPositionMs = buf
        )
        if (controller.isPlaying) {
            startPositionTicker()
        }
    }

    private fun startPositionTicker() {
        if (!isAppInForeground) return
        stopPositionTicker()
        tickerJob = scope.launch {
            while (isActive && isAppInForeground) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = if (controller.duration > 0) controller.duration else _progressState.value.totalDurationMs
                    val buf = controller.bufferedPosition.coerceAtLeast(0L)
                    val prev = _progressState.value
                    if (prev.currentPositionMs != pos || prev.totalDurationMs != dur || prev.bufferedPositionMs != buf) {
                        _progressState.value = PlaybackProgressState(
                            currentPositionMs = pos,
                            totalDurationMs = dur,
                            bufferedPositionMs = buf
                        )
                    }
                } else {
                    break
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
        val entries = songs.map { QueueEntry(song = it) }
        currentQueueEntries = entries
        currentSongList = entries.map { it.song }

        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        val mediaItems = songs.map { songToMediaItem(it) }
        controller.stop()
        controller.clearMediaItems()
        controller.setMediaItems(mediaItems, safeIndex, 0L)
        controller.prepare()
        controller.play()

        val targetSong = currentSongList.getOrNull(safeIndex)
        _playbackState.update {
            it.copy(
                queue = currentSongList,
                queueEntries = currentQueueEntries,
                currentQueueIndex = safeIndex,
                currentSong = targetSong,
                loopRegion = LoopRegion()
            )
        }
        _progressState.value = PlaybackProgressState(
            currentPositionMs = 0L,
            totalDurationMs = targetSong?.durationMs ?: 0L,
            bufferedPositionMs = 0L
        )
    }

    fun playQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in currentQueueEntries.indices && index < controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            controller.play()
            val targetSong = currentSongList.getOrNull(index)
            _playbackState.update {
                it.copy(
                    currentQueueIndex = index,
                    currentSong = targetSong
                )
            }
            _progressState.value = PlaybackProgressState(
                currentPositionMs = 0L,
                totalDurationMs = targetSong?.durationMs ?: 0L,
                bufferedPositionMs = 0L
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
        val loop = _playbackState.value.loopRegion
        val effectivePos = if (loop.isValidActive && positionMs >= (loop.endMs ?: Long.MAX_VALUE)) {
            loop.startMs ?: 0L
        } else {
            positionMs.coerceAtLeast(0L)
        }
        mediaController?.seekTo(effectivePos)
        _progressState.update { it.copy(currentPositionMs = effectivePos) }
    }

    fun setLoopStart(ms: Long? = null, fromDrag: Boolean = false) {
        val controller = mediaController ?: return
        val targetMs = (ms ?: controller.currentPosition).coerceAtLeast(0L)
        val duration = controller.duration.takeIf { it > 0L } ?: _progressState.value.totalDurationMs
        val updated = _playbackState.value.loopRegion.withStartSet(targetMs, duration, fromDrag)
        _playbackState.update { it.copy(loopRegion = updated) }
        sendLoopCommand(
            LoopSessionCommands.ACTION_SET_LOOP_START,
            Bundle().apply {
                putLong(LoopSessionCommands.EXTRA_POSITION_MS, targetMs)
                putBoolean(LoopSessionCommands.EXTRA_FROM_DRAG, fromDrag)
            }
        )
    }

    fun setLoopEnd(ms: Long? = null, fromDrag: Boolean = false) {
        val controller = mediaController ?: return
        val targetMs = (ms ?: controller.currentPosition).coerceAtLeast(0L)
        val duration = controller.duration.takeIf { it > 0L } ?: _progressState.value.totalDurationMs
        val updated = _playbackState.value.loopRegion.withEndSet(targetMs, duration, fromDrag)
        _playbackState.update { it.copy(loopRegion = updated) }
        if (updated.isValidActive && controller.currentPosition >= (updated.endMs ?: Long.MAX_VALUE)) {
            _progressState.update { it.copy(currentPositionMs = updated.startMs ?: 0L) }
        }
        sendLoopCommand(
            LoopSessionCommands.ACTION_SET_LOOP_END,
            Bundle().apply {
                putLong(LoopSessionCommands.EXTRA_POSITION_MS, targetMs)
                putBoolean(LoopSessionCommands.EXTRA_FROM_DRAG, fromDrag)
            }
        )
    }

    fun nudgeLoopStart(deltaMs: Long) {
        val currentStart = _playbackState.value.loopRegion.startMs ?: return
        setLoopStart((currentStart + deltaMs).coerceAtLeast(0L), fromDrag = true)
    }

    fun nudgeLoopEnd(deltaMs: Long) {
        val currentEnd = _playbackState.value.loopRegion.endMs ?: return
        setLoopEnd((currentEnd + deltaMs).coerceAtLeast(0L), fromDrag = true)
    }

    fun toggleLoop() {
        if (mediaController == null) return
        _playbackState.update { it.copy(loopRegion = it.loopRegion.toggled()) }
        sendLoopCommand(LoopSessionCommands.ACTION_TOGGLE_LOOP, Bundle.EMPTY)
    }

    fun clearLoop() {
        _playbackState.update { it.copy(loopRegion = LoopRegion()) }
        if (mediaController != null) {
            sendLoopCommand(LoopSessionCommands.ACTION_CLEAR_LOOP, Bundle.EMPTY)
        }
    }

    private fun sendLoopCommand(action: String, args: Bundle) {
        val controller = mediaController ?: return
        val future = controller.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
        future.addListener({
            try {
                val result = future.get()
                LoopSessionCommands.fromBundle(result.extras)?.let { synced ->
                    _playbackState.update { it.copy(loopRegion = synced) }
                }
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
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
        currentQueueEntries = currentQueueEntries + QueueEntry(song = song)
        currentSongList = currentQueueEntries.map { it.song }
        controller.addMediaItem(songToMediaItem(song))
        _playbackState.update {
            it.copy(queue = currentSongList, queueEntries = currentQueueEntries)
        }
    }

    fun playSongNext(song: Song) {
        val controller = mediaController ?: return
        val nextIndex = (controller.currentMediaItemIndex + 1).coerceIn(0, currentQueueEntries.size)
        val updatedEntries = currentQueueEntries.toMutableList().apply {
            add(nextIndex, QueueEntry(song = song))
        }
        currentQueueEntries = updatedEntries
        currentSongList = updatedEntries.map { it.song }
        controller.addMediaItem(nextIndex, songToMediaItem(song))
        _playbackState.update {
            it.copy(
                queue = currentSongList,
                queueEntries = currentQueueEntries,
                currentQueueIndex = controller.currentMediaItemIndex
            )
        }
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in currentQueueEntries.indices) {
            val updatedEntries = currentQueueEntries.toMutableList().apply { removeAt(index) }
            currentQueueEntries = updatedEntries
            currentSongList = updatedEntries.map { it.song }
            controller.removeMediaItem(index)
            val updatedCurrentIdx = controller.currentMediaItemIndex
            _playbackState.update {
                it.copy(
                    queue = currentSongList,
                    queueEntries = currentQueueEntries,
                    currentQueueIndex = updatedCurrentIdx,
                    currentSong = currentSongList.getOrNull(updatedCurrentIdx)
                )
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        if (fromIndex in currentQueueEntries.indices &&
            toIndex in currentQueueEntries.indices &&
            fromIndex != toIndex
        ) {
            val updatedEntries = currentQueueEntries.toMutableList()
            val movedEntry = updatedEntries.removeAt(fromIndex)
            updatedEntries.add(toIndex, movedEntry)
            currentQueueEntries = updatedEntries
            currentSongList = updatedEntries.map { it.song }

            // Move inside live MediaController / ExoPlayer without stopping playback
            controller.moveMediaItem(fromIndex, toIndex)
            val syncedIndex = controller.currentMediaItemIndex

            _playbackState.update {
                it.copy(
                    queue = currentSongList,
                    queueEntries = currentQueueEntries,
                    currentQueueIndex = syncedIndex,
                    currentSong = currentSongList.getOrNull(syncedIndex) ?: it.currentSong
                )
            }
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
        val activeSongId = _playbackState.value.currentSong?.id
        currentQueueEntries = currentQueueEntries.map { entry ->
            if (entry.song.id == activeSongId) entry.copy(song = entry.song.copy(isFavorite = isFavorite)) else entry
        }
        currentSongList = currentQueueEntries.map { it.song }
        _playbackState.update { state ->
            val updatedSong = state.currentSong?.copy(isFavorite = isFavorite)
            state.copy(
                currentSong = updatedSong,
                queue = currentSongList,
                queueEntries = currentQueueEntries
            )
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
