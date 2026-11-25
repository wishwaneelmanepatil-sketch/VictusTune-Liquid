package com.victustune.player.playback

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.victustune.player.data.remote.extractor.StreamResolver
import com.victustune.player.playback.cache.MediaCacheManager
import kotlinx.coroutines.runBlocking

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

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

        mediaSession = MediaSession.Builder(this, player).build()
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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        equalizerController.release()
        super.onDestroy()
    }
}
