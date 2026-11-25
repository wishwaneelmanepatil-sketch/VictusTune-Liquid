package com.victustune.player.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTransferCodecTest {

    @Test
    fun testM3uExtendedAndPlainParsing() {
        val m3u = """
            #EXTM3U
            #EXTINF:215,Linkin Park - Numb
            https://music.youtube.com/watch?v=kXYiU_JCYtU
            #EXTINF:180,Solo Title Only
            content://media/external/audio/media/42
            /storage/emulated/0/Music/Daft Punk - Get Lucky.mp3
            https://music.youtube.com/watch?v=5NV6Rdv1a3I
        """.trimIndent()

        val entries = M3uCodec.parse(m3u)
        assertEquals(4, entries.size)

        assertEquals("Numb", entries[0].title)
        assertEquals("Linkin Park", entries[0].artist)
        assertEquals(215_000L, entries[0].durationMs)
        assertEquals("https://music.youtube.com/watch?v=kXYiU_JCYtU", entries[0].sourcePathOrUrl)

        assertEquals("Solo Title Only", entries[1].title)
        assertEquals("", entries[1].artist)
        assertEquals(180_000L, entries[1].durationMs)
        assertEquals("content://media/external/audio/media/42", entries[1].sourcePathOrUrl)

        assertEquals("Get Lucky", entries[2].title)
        assertEquals("Daft Punk", entries[2].artist)
        assertEquals("/storage/emulated/0/Music/Daft Punk - Get Lucky.mp3", entries[2].sourcePathOrUrl)

        assertEquals("", entries[3].title)
        assertEquals("https://music.youtube.com/watch?v=5NV6Rdv1a3I", entries[3].sourcePathOrUrl)
    }

    @Test
    fun testM3uRoundTrip() {
        val original = listOf(
            PlaylistTransferEntry(
                title = "Bohemian Rhapsody",
                artist = "Queen",
                durationMs = 354_000L,
                sourcePathOrUrl = "https://music.youtube.com/watch?v=fJ9rUzIMcZQ"
            ),
            PlaylistTransferEntry(
                title = "夜に駆ける",
                artist = "YOASOBI",
                durationMs = 260_000L,
                sourcePathOrUrl = "content://media/external/audio/media/99"
            )
        )

        val encoded = M3uCodec.encodeEntries(original)
        assertTrue(encoded.startsWith("#EXTM3U"))

        val decoded = M3uCodec.parse(encoded)
        assertEquals(2, decoded.size)
        assertEquals("Bohemian Rhapsody", decoded[0].title)
        assertEquals("Queen", decoded[0].artist)
        assertEquals(354_000L, decoded[0].durationMs)
        assertEquals("https://music.youtube.com/watch?v=fJ9rUzIMcZQ", decoded[0].sourcePathOrUrl)

        assertEquals("夜に駆ける", decoded[1].title)
        assertEquals("YOASOBI", decoded[1].artist)
        assertEquals(260_000L, decoded[1].durationMs)
        assertEquals("content://media/external/audio/media/99", decoded[1].sourcePathOrUrl)
    }

    @Test
    fun testCsvQuotedCommasAndEscapedQuotes() {
        val csv = listOf(
            "Track Name,Artist Name(s),Album Name,Duration (ms)",
            "\"Hello, World\",\"Tyler, The Creator\",\"Igor, Vol. 1\",195000",
            "\"Track \"\"Quoted\"\" Name\",Artist B,\"Album \"\"Special\"\"\",03:15"
        ).joinToString("\n")

        val entries = CsvCodec.parse(csv)
        assertEquals(2, entries.size)

        assertEquals("Hello, World", entries[0].title)
        assertEquals("Tyler, The Creator", entries[0].artist)
        assertEquals("Igor, Vol. 1", entries[0].album)
        assertEquals(195_000L, entries[0].durationMs)

        assertEquals("Track \"Quoted\" Name", entries[1].title)
        assertEquals("Artist B", entries[1].artist)
        assertEquals("Album \"Special\"", entries[1].album)
        assertEquals(195_000L, entries[1].durationMs)
    }

    @Test
    fun testExtractYouTubeVideoIdAndNormalize() {
        assertEquals(
            "kXYiU_JCYtU",
            PlaylistImporter.extractYouTubeVideoId("https://music.youtube.com/watch?v=kXYiU_JCYtU&si=xyz")
        )
        assertEquals(
            "5NV6Rdv1a3I",
            PlaylistImporter.extractYouTubeVideoId("https://youtu.be/5NV6Rdv1a3I")
        )
        assertEquals(
            "starlight",
            PlaylistImporter.normalizeForMatch("Starlight (Official Music Video) feat. Someone")
        )
    }

    @Test
    fun testCsvColumnAutoDetectionCaseInsensitive() {
        val csv = """
            song,artists,album,duration,videoid
            Starboy,The Weeknd,Starboy,230,34Na4j8AVgA
        """.trimIndent()

        val entries = CsvCodec.parse(csv)
        assertEquals(1, entries.size)
        assertEquals("Starboy", entries[0].title)
        assertEquals("The Weeknd", entries[0].artist)
        assertEquals("Starboy", entries[0].album)
        assertEquals(230_000L, entries[0].durationMs)
        assertEquals("https://music.youtube.com/watch?v=34Na4j8AVgA", entries[0].sourcePathOrUrl)
    }

    @Test
    fun testCsvRoundTripWithUtf8AndSpecialCharacters() {
        val original = listOf(
            PlaylistTransferEntry(
                title = "Viva La Vida, Live \"2008\"",
                artist = "Coldplay",
                album = "Viva La Vida or Death, and All His Friends",
                durationMs = 242_000L,
                sourcePathOrUrl = "https://music.youtube.com/watch?v=dvgZkm1xWPE"
            ),
            PlaylistTransferEntry(
                title = "アイドル",
                artist = "YOASOBI",
                album = "THE BOOK 3",
                durationMs = 213_000L,
                sourcePathOrUrl = "content://media/external/audio/media/501"
            )
        )

        val encoded = CsvCodec.encodeEntries(original, includeBom = true)
        val decoded = CsvCodec.parse(encoded)

        assertEquals(2, decoded.size)
        assertEquals(original[0].title, decoded[0].title)
        assertEquals(original[0].artist, decoded[0].artist)
        assertEquals(original[0].album, decoded[0].album)
        assertEquals(original[0].durationMs, decoded[0].durationMs)
        assertEquals(original[0].sourcePathOrUrl, decoded[0].sourcePathOrUrl)

        assertEquals(original[1].title, decoded[1].title)
        assertEquals(original[1].artist, decoded[1].artist)
        assertEquals(original[1].album, decoded[1].album)
        assertEquals(original[1].durationMs, decoded[1].durationMs)
        assertEquals(original[1].sourcePathOrUrl, decoded[1].sourcePathOrUrl)
    }
}
