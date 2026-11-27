package com.victustune.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.victustune.player.data.auth.AuthManager
import com.victustune.player.data.coil.AudioArtworkFetcher
import com.victustune.player.data.coil.HighResThumbnailInterceptor
import com.victustune.player.data.local.VictusTuneDatabase
import com.victustune.player.data.local.datastore.PreferencesManager
import com.victustune.player.data.remote.extractor.OkHttpDownloader
import com.victustune.player.data.remote.extractor.StreamResolver
import com.victustune.player.data.remote.innertube.InnerTubeClient
import com.victustune.player.data.repository.AudioRepository
import com.victustune.player.data.repository.LyricsRepository
import com.victustune.player.data.repository.PlaylistRepository
import com.victustune.player.data.repository.StreamingRepository
import com.victustune.player.playback.EqualizerController
import com.victustune.player.playback.MusicController
import com.victustune.player.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.TimeUnit

class AppContainer(application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: VictusTuneDatabase by lazy {
        VictusTuneDatabase.getInstance(application)
    }

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(application)
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val okHttpDownloader: OkHttpDownloader by lazy {
        OkHttpDownloader(okHttpClient)
    }

    val streamResolver: StreamResolver by lazy {
        StreamResolver()
    }

    val authManager: AuthManager by lazy {
        AuthManager(application)
    }

    val innerTubeClient: InnerTubeClient by lazy {
        InnerTubeClient(okHttpClient, authManager)
    }

    val streamingRepository: StreamingRepository by lazy {
        StreamingRepository(
            innerTubeClient = innerTubeClient,
            streamResolver = streamResolver,
            searchHistoryDao = database.searchHistoryDao(),
            authManager = authManager,
            syncedPlaylistDao = database.syncedPlaylistDao()
        )
    }

    val audioRepository: AudioRepository by lazy {
        AudioRepository(application, database.favoriteDao())
    }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(
            database.playlistDao(),
            database.favoriteDao(),
            database.historyDao(),
            audioRepository,
            database.downloadedTrackDao(),
            database.savedTrackDao()
        )
    }

    val downloadRepository: com.victustune.player.data.repository.DownloadRepository by lazy {
        com.victustune.player.data.repository.DownloadRepository(
            context = application,
            downloadedTrackDao = database.downloadedTrackDao(),
            preferencesManager = preferencesManager,
            okHttpClient = okHttpClient
        )
    }

    val playlistImporter: com.victustune.player.data.transfer.PlaylistImporter by lazy {
        com.victustune.player.data.transfer.PlaylistImporter(
            context = application,
            audioRepository = audioRepository,
            streamingRepository = streamingRepository,
            playlistRepository = playlistRepository
        )
    }

    val playlistExporter: com.victustune.player.data.transfer.PlaylistExporter by lazy {
        com.victustune.player.data.transfer.PlaylistExporter(
            context = application,
            playlistRepository = playlistRepository,
            streamingRepository = streamingRepository,
            downloadRepository = downloadRepository
        )
    }

    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepository()
    }

    val musicController: MusicController by lazy {
        MusicController(application, preferencesManager)
    }

    val equalizerController: EqualizerController
        get() = PlaybackService.equalizerController

    init {
        // Initialize NewPipe with custom OkHttpDownloader
        NewPipe.init(okHttpDownloader)
        PlaybackService.streamResolver = streamResolver

        // Keep StreamResolver quality in sync with user preference
        appScope.launch {
            preferencesManager.audioQualityFlow.collectLatest { quality ->
                streamResolver.setQuality(quality)
            }
        }
    }
}

class VictusTuneApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioArtworkFetcher.Factory(this@VictusTuneApp))
                add(HighResThumbnailInterceptor())
            }
            .allowHardware(true)
            .crossfade(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .build()
    }
}
