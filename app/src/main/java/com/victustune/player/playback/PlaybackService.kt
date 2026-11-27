package com.victustune.player.playback

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.victustune.player.data.model.LoopRegion
import com.victustune.player.data.remote.extractor.StreamResolver
import com.victustune.player.playback.LoopSessionCommands.toSessionBundle
import com.victustune.player.playback.cache.MediaCacheManager
import kotlinx.coroutines.runBlocking

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

    private var currentLoopRegion: LoopRegion = LoopRegion()
    private var loopMessage: PlayerMessage? = null
    private var loopMediaItemIndex: Int = C.INDEX_UNSET

    companion object {
        var equalizerController: EqualizerController = EqualizerController()
        var streamResolver: StreamResolver = StreamResolver()
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            httpDataSourceFactory,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    if (uri.scheme == "stream") {
                        val videoId = uri.host ?: uri.schemeSpecificPart.removePrefix("//")
                        val resolvedUrl = runBlocking {
                            try {
                                streamResolver.resolveStreamUrl(videoId)
                            } catch (e: Exception) {
                                // If first attempt failed (e.g. expired cache), invalidate and retry once
                                streamResolver.invalidate(videoId)
                                streamResolver.resolveStreamUrl(videoId)
                            }
                        }
                        return dataSpec.buildUpon()
                            .setUri(Uri.parse(resolvedUrl))
                            .build()
                    }
                    return dataSpec
                }
            }
        )

        val simpleCache = MediaCacheManager.getInstance(applicationContext)
        val streamingCacheFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val downloadCache = com.victustune.player.data.download.DownloadCacheManager.getDownloadCache(applicationContext)
        val downloadCacheFactory = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(streamingCacheFactory)
            .setCacheWriteDataSinkFactory(null) // Read-only! Downloaded songs are written by DownloadManager
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val defaultDataSourceFactory = DefaultDataSource.Factory(this, downloadCacheFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(defaultDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true) // Handles audio focus automatically
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    equalizerController.attachAudioSession(audioSessionId)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                clearLoopStateAndSync()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                    currentLoopRegion.isValidActive &&
                    newPosition.mediaItemIndex == loopMediaItemIndex
                ) {
                    val startMs = currentLoopRegion.startMs ?: return
                    val endMs = currentLoopRegion.endMs ?: return
                    if (newPosition.positionMs >= endMs) {
                        player.seekTo(loopMediaItemIndex, startMs)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause
                if (cause is HttpDataSource.InvalidResponseCodeException &&
                    (cause.responseCode == 403 || cause.responseCode == 410)
                ) {
                    val currentMediaItem = player.currentMediaItem
                    val currentUri = currentMediaItem?.localConfiguration?.uri
                    val videoId = if (currentUri?.scheme == "stream") {
                        currentUri.host ?: currentUri.schemeSpecificPart.removePrefix("//")
                    } else {
                        currentMediaItem?.mediaId
                    }

                    if (!videoId.isNullOrEmpty()) {
                        streamResolver.invalidate(videoId)
                        player.prepare()
                        player.play()
                        return
                    }
                }

                // If playback error occurs on current track, gracefully advance to next track if available
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })

        // Initial audio session attachment if already set
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            equalizerController.attachAudioSession(player.audioSessionId)
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(LoopSessionCommands.ACTION_SET_LOOP_START, Bundle.EMPTY))
                        .add(SessionCommand(LoopSessionCommands.ACTION_SET_LOOP_END, Bundle.EMPTY))
                        .add(SessionCommand(LoopSessionCommands.ACTION_TOGGLE_LOOP, Bundle.EMPTY))
                        .add(SessionCommand(LoopSessionCommands.ACTION_CLEAR_LOOP, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    when (customCommand.customAction) {
                        LoopSessionCommands.ACTION_SET_LOOP_START -> {
                            val ms = args.getLong(
                                LoopSessionCommands.EXTRA_POSITION_MS,
                                player.currentPosition.coerceAtLeast(0L)
                            )
                            val fromDrag = args.getBoolean(LoopSessionCommands.EXTRA_FROM_DRAG, false)
                            applyLoopState(currentLoopRegion.withStartSet(ms, duration, fromDrag))
                        }
                        LoopSessionCommands.ACTION_SET_LOOP_END -> {
                            val ms = args.getLong(
                                LoopSessionCommands.EXTRA_POSITION_MS,
                                player.currentPosition.coerceAtLeast(0L)
                            )
                            val fromDrag = args.getBoolean(LoopSessionCommands.EXTRA_FROM_DRAG, false)
                            applyLoopState(currentLoopRegion.withEndSet(ms, duration, fromDrag))
                        }
                        LoopSessionCommands.ACTION_TOGGLE_LOOP -> {
                            applyLoopState(currentLoopRegion.toggled())
                        }
                        LoopSessionCommands.ACTION_CLEAR_LOOP -> {
                            clearLoopStateAndSync()
                        }
                        else -> {
                            return Futures.immediateFuture(
                                SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                            )
                        }
                    }
                    return Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS, currentLoopRegion.toSessionBundle())
                    )
                }
            })
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun applyLoopState(updated: LoopRegion) {
        currentLoopRegion = updated
        cancelLoopMessage()

        if (updated.isValidActive) {
            val startMs = updated.startMs ?: return
            val endMs = updated.endMs ?: return
            val activeIndex = player.currentMediaItemIndex
            if (activeIndex != C.INDEX_UNSET && activeIndex < player.mediaItemCount) {
                loopMediaItemIndex = activeIndex
                loopMessage = player.createMessage { _, _ ->
                    if (currentLoopRegion.isValidActive &&
                        player.currentMediaItemIndex == activeIndex
                    ) {
                        player.seekTo(activeIndex, startMs)
                    }
                }
                    .setLooper(player.applicationLooper)
                    .setPosition(activeIndex, endMs)
                    .setDeleteAfterDelivery(false)
                    .send()

                // If playback or seek is already past endMs when loop activates, wrap immediately to startMs
                if (player.currentPosition >= endMs) {
                    player.seekTo(activeIndex, startMs)
                }
            }
        }

        mediaSession?.setSessionExtras(currentLoopRegion.toSessionBundle())
    }

    @OptIn(UnstableApi::class)
    private fun clearLoopStateAndSync() {
        cancelLoopMessage()
        if (currentLoopRegion.hasBounds || currentLoopRegion.isActive) {
            currentLoopRegion = LoopRegion()
            mediaSession?.setSessionExtras(currentLoopRegion.toSessionBundle())
        }
    }

    @OptIn(UnstableApi::class)
    private fun cancelLoopMessage() {
        loopMessage?.cancel()
        loopMessage = null
        loopMediaItemIndex = C.INDEX_UNSET
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        cancelLoopMessage()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        equalizerController.release()
        super.onDestroy()
    }
}
