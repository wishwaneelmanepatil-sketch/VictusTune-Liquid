package com.victustune.player.data.remote.innertube

object ThumbnailUtils {
    private val GOOGLE_USERCONTENT_REGEX = Regex("=(w\\d+(-[a-z0-9]+)*-h\\d+|s\\d+).*")
    private val YTIMG_VI_REGEX = Regex(".*/vi/([a-zA-Z0-9_-]+)/.*")

    /**
     * Upgrades low-resolution YouTube Music / Google CDN / YouTube thumbnails to crisp HD quality.
     *
     * @param url Original thumbnail URL
     * @param targetSize Desired square dimension for Google UserContent images (default 1024)
     * @return High resolution image URL
     */
    fun upgrade(url: String?, targetSize: Int = 1024): String? {
        if (url.isNullOrBlank()) return null
        var clean = if (url.startsWith("//")) "https:$url" else url

        // 1. Google UserContent (YouTube Music albums, tracks, artists, playlist banners)
        // Default size in InnerTube responsive list items is =w60-h60 or =w120-h120.
        // Upgrading to =w1024-h1024-l90-rj yields crystal-clear HD artwork directly from Google's CDN.
        if (clean.contains("googleusercontent.com")) {
            return if (GOOGLE_USERCONTENT_REGEX.containsMatchIn(clean)) {
                clean.replace(GOOGLE_USERCONTENT_REGEX, "=w$targetSize-h$targetSize-l90-rj")
            } else if (clean.contains("=")) {
                val base = clean.substringBeforeLast("=")
                "$base=w$targetSize-h$targetSize-l90-rj"
            } else {
                "$clean=w$targetSize-h$targetSize-l90-rj"
            }
        }

        // 2. YouTube Video Thumbnails (i.ytimg.com / img.youtube.com)
        // Standard video thumbnails: upgrade to hqdefault (480x360 uncompressed) without downscaling params
        if (clean.contains("i.ytimg.com") || clean.contains("img.youtube.com")) {
            val videoMatch = YTIMG_VI_REGEX.find(clean)
            if (videoMatch != null) {
                val videoId = videoMatch.groupValues[1]
                return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }
        }

        return clean
    }

    /**
     * Returns candidate URLs for video artwork, ordered by preferred resolution.
     */
    fun getVideoThumbnailCandidates(videoId: String): List<String> {
        return listOf(
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        )
    }
}
