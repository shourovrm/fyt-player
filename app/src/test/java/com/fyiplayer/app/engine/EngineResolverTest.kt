package com.fyiplayer.app.engine

import com.fyiplayer.app.core.Protocol
import com.fyiplayer.app.core.VideoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure JSON -> Resolved mapping, no Android, no network.
class EngineResolverTest {
    private val ref = VideoRef(
        sourceId = "youtube",
        pageUrl = "https://www.youtube.com/watch?v=abc12345678",
        remoteId = "abc12345678",
        title = "Sample",
    )

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().readText()

    @Test
    fun mapsFormatsFromInfoJson() {
        val json = fixture("/youtube/info_formats.json")
        val resolved = parseInfoJson(json, ref, nowMillis = 1_000L)

        assertEquals(4, resolved.formats.size)
        assertEquals(1_000L, resolved.resolvedAtMillis)

        // The page's own info JSON refreshes title/thumbnail/duration onto the resolved ref.
        assertEquals("Resolved Title", resolved.ref.title)
        assertEquals("https://example.invalid/resolved_thumb.jpg", resolved.ref.thumbnailUrl)
        assertEquals(755, resolved.ref.durationSeconds)

        val videoOnly = resolved.formats.first { it.formatId == "137" }
        assertNull(videoOnly.audioCodec) // "none" -> null
        assertTrue(videoOnly.isVideoOnly)
        assertEquals(4_500_500L, videoOnly.bitrate) // 4500.5 kbps -> bps
        assertEquals(123456789L, videoOnly.filesizeBytes)
        assertEquals("test-agent", videoOnly.headers["User-Agent"])
        assertEquals(Protocol.PROGRESSIVE, videoOnly.protocol)

        val audioOnly = resolved.formats.first { it.formatId == "140" }
        assertNull(audioOnly.videoCodec)
        assertTrue(audioOnly.isAudioOnly)
        assertEquals(3456789L, audioOnly.filesizeBytes) // filesize_approx fallback

        val hls = resolved.formats.first { it.formatId == "96" }
        assertEquals(Protocol.HLS, hls.protocol)

        val dash = resolved.formats.first { it.formatId == "232" }
        assertEquals(Protocol.DASH, dash.protocol)
    }
}
