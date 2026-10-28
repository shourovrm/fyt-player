package com.fyiplayer.app.download

import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure decision test for shouldFallBackToEngine. No Android, no network: the fixtures are plain
// MediaFormat values, the same shape DownloadOptionsTest uses.
private fun mediaFormat(
    id: String,
    protocol: Protocol = Protocol.PROGRESSIVE,
    height: Int? = null,
    video: String? = null,
    audio: String? = null,
) = MediaFormat(
    formatId = id, url = "https://example.invalid/$id", container = "mp4",
    protocol = protocol, height = height, videoCodec = video, audioCodec = audio,
)

class YoutubeEngineFallbackTest {

    @Test fun `a normal youtube list with a progressive track does not fall back`() {
        val formats = listOf(
            mediaFormat("18", height = 360, video = "avc", audio = "aac"),
        )

        assertFalse(shouldFallBackToEngine(formats, "youtube"))
    }

    @Test fun `a youtube list with only SABR formats falls back`() {
        val formats = listOf(
            mediaFormat("sabr-360", protocol = Protocol.SABR, height = 360, video = "avc", audio = "mp4a"),
            mediaFormat("sabr-1080", protocol = Protocol.SABR, height = 1080, video = "avc", audio = "mp4a"),
        )

        assertTrue(shouldFallBackToEngine(formats, "youtube"))
    }

    @Test fun `an empty youtube list falls back`() {
        assertTrue(shouldFallBackToEngine(emptyList(), "youtube"))
    }

    @Test fun `a non-youtube source never falls back even with only SABR formats`() {
        val formats = listOf(
            mediaFormat("sabr-720", protocol = Protocol.SABR, height = 720, video = "avc", audio = "mp4a"),
        )

        assertFalse(shouldFallBackToEngine(formats, "vimeo"))
    }
}
