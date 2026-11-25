package com.victustune.player.data.transfer

data class PlaylistTransferEntry(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val sourcePathOrUrl: String? = null
)

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    CSV("csv", "text/csv", "CSV (.csv)"),
    M3U8("m3u8", "application/vnd.apple.mpegurl", "M3U8 (.m3u8)")
}
