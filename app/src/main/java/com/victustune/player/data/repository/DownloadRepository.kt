package com.victustune.player.data.repository

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.victustune.player.data.download.DownloadCacheManager
import com.victustune.player.data.download.DownloadStatus
import com.victustune.player.data.download.MusicDownloadService
import com.victustune.player.data.local.dao.DownloadedTrackDao
import com.victustune.player.data.local.datastore.PreferencesManager
import com.victustune.player.data.local.entity.DownloadedTrackEntity
import com.victustune.player.data.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@OptIn(UnstableApi::class)
class DownloadRepository(
    private val context: Context,
    private val downloadedTrackDao: DownloadedTrackDao,
    private val preferencesManager: PreferencesManager,
    private val okHttpClient: OkHttpClient
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadManager: DownloadManager = DownloadCacheManager.getDownloadManager(context)

    val allDownloadsFlow: Flow<List<DownloadedTrackEntity>> = downloadedTrackDao.getAllDownloadedTracks()

    val completedDownloadsFlow: Flow<List<Song>> = downloadedTrackDao.getCompletedTracks().map { list ->
        list.map { it.toSong() }
    }

    val totalDownloadSizeBytes: Flow<Long> = downloadedTrackDao.getTotalDownloadSize().map { it ?: 0L }

    val downloadStatesMap: Flow<Map<String, DownloadStatus>> = allDownloadsFlow.map { list ->
        list.associate { entity ->
            val status = when (entity.status) {
                "COMPLETED" -> DownloadStatus.COMPLETED
                "DOWNLOADING" -> DownloadStatus.DOWNLOADING
                "QUEUED" -> DownloadStatus.QUEUED
                "FAILED" -> DownloadStatus.FAILED
                else -> DownloadStatus.NOT_DOWNLOADED
            }
            entity.videoId to status
        }
    }

    val downloadProgressMap: Flow<Map<String, Float>> = allDownloadsFlow.map { list ->
        list.associate { it.videoId to it.progress }
    }

    val downloadQualityFlow: Flow<String> = preferencesManager.downloadQualityFlow
    val downloadWifiOnlyFlow: Flow<Boolean> = preferencesManager.downloadWifiOnlyFlow

    private var progressPollingJob: Job? = null

    init {
        // Observe requirements for Wi-Fi only downloading
        repositoryScope.launch {
            preferencesManager.downloadWifiOnlyFlow.collectLatest { wifiOnly ->
                val req = if (wifiOnly) Requirements(Requirements.NETWORK_UNMETERED) else Requirements(0)
                downloadManager.requirements = req
                DownloadService.sendSetRequirements(context, MusicDownloadService::class.java, req, false)
            }
        }

        // Attach Media3 DownloadManager Listener
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                handleDownloadChanged(download)
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                handleDownloadRemoved(download.request.id)
            }
        })

        // Start progress polling loop if active downloads exist
        startProgressPollingIfNeeded()
    }

    private fun handleDownloadChanged(download: Download) {
        val videoId = download.request.id
        val percent = if (download.percentDownloaded >= 0f) download.percentDownloaded else 0f
        val bytes = download.bytesDownloaded

        repositoryScope.launch {
            when (download.state) {
                Download.STATE_QUEUED -> {
                    downloadedTrackDao.updateProgress(videoId, "QUEUED", 0f)
                    startProgressPollingIfNeeded()
                }
                Download.STATE_DOWNLOADING -> {
                    downloadedTrackDao.updateProgress(videoId, "DOWNLOADING", percent)
                    startProgressPollingIfNeeded()
                }
                Download.STATE_COMPLETED -> {
                    downloadedTrackDao.updateCompleted(
                        videoId = videoId,
                        status = "COMPLETED",
                        progress = 100f,
                        sizeBytes = bytes
                    )
                    // Cache artwork locally for offline display
                    cacheArtworkLocally(videoId)
                    stopProgressPollingIfIdle()
                }
                Download.STATE_FAILED -> {
                    downloadedTrackDao.updateProgress(videoId, "FAILED", 0f)
                    stopProgressPollingIfIdle()
                }
                Download.STATE_STOPPED -> {
                    downloadedTrackDao.updateProgress(videoId, "QUEUED", percent)
                }
            }
        }
    }

    private fun handleDownloadRemoved(videoId: String) {
        repositoryScope.launch {
            downloadedTrackDao.deleteTrack(videoId)
            // Delete locally cached artwork file
            val artworkFile = File(DownloadCacheManager.getArtworkDirectory(context), "$videoId.jpg")
            if (artworkFile.exists()) {
                artworkFile.delete()
            }
            stopProgressPollingIfIdle()
        }
    }

    private fun startProgressPollingIfNeeded() {
        if (progressPollingJob?.isActive == true) return
        progressPollingJob = repositoryScope.launch {
            while (isActive) {
                val currentDownloads = downloadManager.currentDownloads
                val hasActive = currentDownloads.any {
                    it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
                }

                if (!hasActive) {
                    break
                }

                for (download in currentDownloads) {
                    if (download.state == Download.STATE_DOWNLOADING) {
                        val videoId = download.request.id
                        val percent = download.percentDownloaded.coerceIn(0f, 100f)
                        downloadedTrackDao.updateProgress(videoId, "DOWNLOADING", percent)
                    }
                }
                delay(600)
            }
        }
    }

    private fun stopProgressPollingIfIdle() {
        val hasActive = downloadManager.currentDownloads.any {
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
        }
        if (!hasActive) {
            progressPollingJob?.cancel()
            progressPollingJob = null
        }
    }

    private suspend fun cacheArtworkLocally(videoId: String) {
        withContext(Dispatchers.IO) {
            try {
                val track = downloadedTrackDao.getTrackSync(videoId) ?: return@withContext
                val thumbUrl = track.thumbnailUrl ?: return@withContext
                val artworkDir = DownloadCacheManager.getArtworkDirectory(context)
                val artworkFile = File(artworkDir, "$videoId.jpg")

                if (!artworkFile.exists() || artworkFile.length() == 0L) {
                    val request = Request.Builder().url(thumbUrl).build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(artworkFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        downloadedTrackDao.updateArtworkPath(videoId, artworkFile.absolutePath)
                    }
                }
            } catch (e: Exception) {
                // Non-fatal if artwork fails to download offline
                e.printStackTrace()
            }
        }
    }

    suspend fun downloadSong(song: Song) {
        withContext(Dispatchers.IO) {
            val videoId = song.remoteVideoId ?: song.id.toString()
            val existing = downloadedTrackDao.getTrackSync(videoId)
            if (existing != null && existing.status == "COMPLETED") {
                return@withContext
            }

            val entity = DownloadedTrackEntity.fromSong(song, status = "QUEUED")
            downloadedTrackDao.insertOrUpdate(entity)

            val request = DownloadCacheManager.buildDownloadRequest(song)
            DownloadService.sendAddDownload(
                context,
                MusicDownloadService::class.java,
                request,
                false
            )
            startProgressPollingIfNeeded()
        }
    }

    suspend fun downloadAll(songs: List<Song>) {
        songs.forEach { song ->
            downloadSong(song)
        }
    }

    suspend fun removeDownload(videoId: String) {
        withContext(Dispatchers.IO) {
            DownloadService.sendRemoveDownload(
                context,
                MusicDownloadService::class.java,
                videoId,
                false
            )
            downloadedTrackDao.deleteTrack(videoId)
            val artworkFile = File(DownloadCacheManager.getArtworkDirectory(context), "$videoId.jpg")
            if (artworkFile.exists()) {
                artworkFile.delete()
            }
        }
    }

    suspend fun removeAllDownloads() {
        withContext(Dispatchers.IO) {
            DownloadService.sendRemoveAllDownloads(
                context,
                MusicDownloadService::class.java,
                false
            )
            downloadedTrackDao.deleteAll()

            // Clean artwork directory
            val artworkDir = DownloadCacheManager.getArtworkDirectory(context)
            artworkDir.listFiles()?.forEach { it.delete() }

            // Clear download cache keys
            try {
                val cache = DownloadCacheManager.getDownloadCache(context)
                cache.keys.forEach { key ->
                    cache.removeResource(key)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pauseDownloads() {
        DownloadService.sendPauseDownloads(
            context,
            MusicDownloadService::class.java,
            false
        )
    }

    fun resumeDownloads() {
        DownloadService.sendResumeDownloads(
            context,
            MusicDownloadService::class.java,
            false
        )
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        preferencesManager.setDownloadWifiOnly(enabled)
    }

    suspend fun setDownloadQuality(quality: String) {
        preferencesManager.setDownloadQuality(quality)
    }

    fun getDownloadStatus(videoId: String): Flow<DownloadStatus> {
        return downloadedTrackDao.getTrack(videoId).map { entity ->
            when (entity?.status) {
                "COMPLETED" -> DownloadStatus.COMPLETED
                "DOWNLOADING" -> DownloadStatus.DOWNLOADING
                "QUEUED" -> DownloadStatus.QUEUED
                "FAILED" -> DownloadStatus.FAILED
                else -> DownloadStatus.NOT_DOWNLOADED
            }
        }
    }
}
