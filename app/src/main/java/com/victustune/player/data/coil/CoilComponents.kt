package com.victustune.player.data.coil

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.Options
import coil.request.SuccessResult
import com.victustune.player.data.remote.innertube.ThumbnailUtils

/**
 * Interceptor that ensures any network image loaded from Google CDN or YouTube
 * is automatically upgraded to full HD resolution, attempting maxresdefault for videos
 * with fallback to hqdefault.
 */
class HighResThumbnailInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data

        val urlStr = when (data) {
            is String -> data
            is Uri -> data.toString()
            else -> null
        }

        if (urlStr != null) {
            // YouTube video thumbnail handling
            if (urlStr.contains("i.ytimg.com/vi/") || urlStr.contains("img.youtube.com/vi/")) {
                val match = Regex(".*/vi/([a-zA-Z0-9_-]+)/.*").find(urlStr)
                if (match != null) {
                    val videoId = match.groupValues[1]
                    // Try maxresdefault first (1280x720)
                    val maxResUrl = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
                    val maxResRequest = request.newBuilder()
                        .data(if (data is Uri) Uri.parse(maxResUrl) else maxResUrl)
                        .build()
                    val result = chain.proceed(maxResRequest)
                    if (result is SuccessResult) {
                        return result
                    }
                    // Fall back to hqdefault (480x360, guaranteed available)
                    val hqUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    val hqRequest = request.newBuilder()
                        .data(if (data is Uri) Uri.parse(hqUrl) else hqUrl)
                        .build()
                    return chain.proceed(hqRequest)
                }
            }

            // Google UserContent and other CDN thumbnail upgrades
            val upgraded = ThumbnailUtils.upgrade(urlStr)
            if (upgraded != null && upgraded != urlStr) {
                val upgradedRequest = request.newBuilder()
                    .data(if (data is Uri) Uri.parse(upgraded) else upgraded)
                    .build()
                return chain.proceed(upgradedRequest)
            }
        }

        return chain.proceed(request)
    }
}

/**
 * Custom Fetcher to extract full-resolution embedded album art from local audio files.
 */
class AudioArtworkFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val isMediaArt = uri.scheme == ContentResolver.SCHEME_CONTENT &&
                (uri.authority == "media" || uri.authority == "com.android.providers.media")

        if (!isMediaArt) return null

        // 1. Try modern ContentResolver.loadThumbnail on Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val bitmap = context.contentResolver.loadThumbnail(uri, Size(1024, 1024), null)
                return DrawableResult(
                    drawable = BitmapDrawable(context.resources, bitmap),
                    isSampled = false,
                    dataSource = DataSource.DISK
                )
            } catch (_: Throwable) {}
        }

        // 2. Try MediaMetadataRetriever to extract embedded picture bytes
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture
            if (picture != null) {
                val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size)
                if (bitmap != null) {
                    return DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (_: Throwable) {
        } finally {
            try {
                retriever?.release()
            } catch (_: Throwable) {}
        }

        // Fall back to Coil's default handling
        return null
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme == ContentResolver.SCHEME_CONTENT &&
                (data.authority == "media" || data.authority == "com.android.providers.media")
            ) {
                return AudioArtworkFetcher(context, data)
            }
            return null
        }
    }
}
