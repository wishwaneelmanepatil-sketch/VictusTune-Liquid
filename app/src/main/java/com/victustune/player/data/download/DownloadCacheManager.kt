package com.victustune.player.data.download

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadIndex
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.victustune.player.data.model.Song
import com.victustune.player.playback.PlaybackService
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
object DownloadCacheManager {
    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "victustune_download_channel"
    const val FOREGROUND_NOTIFICATION_ID = 2001

    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null

    @Volatile
    private var downloadCache: SimpleCache? = null

    @Volatile
    private var downloadManager: DownloadManager? = null

    @Volatile
    private var notificationHelper: DownloadNotificationHelper? = null

    private val lock = Any()

    fun getDatabaseProvider(context: Context): StandaloneDatabaseProvider {
        return databaseProvider ?: synchronized(lock) {
            databaseProvider ?: StandaloneDatabaseProvider(context.applicationContext).also {
                databaseProvider = it
            }
        }
    }

    fun getDownloadCache(context: Context): SimpleCache {
        return downloadCache ?: synchronized(lock) {
            downloadCache ?: run {
                val downloadDir = File(context.applicationContext.filesDir, "downloads")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                SimpleCache(
                    downloadDir,
                    NoOpCacheEvictor(),
                    getDatabaseProvider(context)
                ).also { downloadCache = it }
            }
        }
    }

    fun getResolvingDataSourceFactory(context: Context): ResolvingDataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)

        return ResolvingDataSource.Factory(
            httpDataSourceFactory,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    if (uri.scheme == "stream") {
                        val videoId = uri.host ?: uri.schemeSpecificPart.removePrefix("//")
                        val resolvedUrl = runBlocking {
                            try {
                                PlaybackService.streamResolver.resolveStreamUrl(videoId)
                            } catch (e: Exception) {
                                PlaybackService.streamResolver.invalidate(videoId)
                                PlaybackService.streamResolver.resolveStreamUrl(videoId)
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
    }

    fun getDownloadManager(context: Context): DownloadManager {
        return downloadManager ?: synchronized(lock) {
            downloadManager ?: run {
                val manager = DownloadManager(
                    context.applicationContext,
                    getDatabaseProvider(context),
                    getDownloadCache(context),
                    getResolvingDataSourceFactory(context),
                    Executors.newFixedThreadPool(4)
                )
                manager.maxParallelDownloads = 3
                manager.resumeDownloads()
                downloadManager = manager
                manager
            }
        }
    }

    fun getNotificationHelper(context: Context): DownloadNotificationHelper {
        return notificationHelper ?: synchronized(lock) {
            notificationHelper ?: DownloadNotificationHelper(
                context.applicationContext,
                DOWNLOAD_NOTIFICATION_CHANNEL_ID
            ).also { notificationHelper = it }
        }
    }

    fun buildDownloadRequest(song: Song): DownloadRequest {
        val videoId = song.remoteVideoId ?: song.id.toString()
        val uri = Uri.parse("stream://$videoId")
        return DownloadRequest.Builder(videoId, uri)
            .setCustomCacheKey("stream://$videoId")
            .setData(song.title.toByteArray(Charsets.UTF_8))
            .build()
    }

    fun getDownloadDirectory(context: Context): File {
        return File(context.applicationContext.filesDir, "downloads")
    }

    fun getArtworkDirectory(context: Context): File {
        val dir = File(context.applicationContext.filesDir, "download_artwork")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getDownloadSizeBytes(context: Context): Long {
        return getDownloadCache(context).cacheSpace
    }

    fun isDownloaded(context: Context, videoId: String): Boolean {
        val cache = getDownloadCache(context)
        val key = "stream://$videoId"
        return cache.isCached(key, 0, 1)
    }
}
