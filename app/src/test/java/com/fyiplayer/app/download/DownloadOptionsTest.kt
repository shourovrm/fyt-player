package com.fyiplayer.app.download

import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure formats -> DownloadOption derivation. No Android, no network, no filesystem -- mirrors
// FormatSelectorTest's fixture style since deriveDownloadOptions is a thin wrapper around
// FormatSelector.select per candidate height.
private fun fmt(
    id: String,
    height: Int? = null,
    video: String? = null,
    audio: String? = null,
    bytes: Long? = null,
    protocol: Protocol = Protocol.PROGRESSIVE,
) = MediaFormat(
    formatId = id, url = "https://example.invalid/$id", container = "mp4",
    protocol = protocol, height = height, videoCodec = video, audioCodec = audio, filesizeBytes = bytes,
)

class DownloadOptionsTest {

    @Test fun `distinct heights come back sorted highest first with an audio-only option last`() {
        val formats = listOf(
            fmt("v1080", height = 1080, video = "avc", bytes = 900_000_000),
            fmt("v720", height = 720, video = "avc", bytes = 400_000_000),
            fmt("v480", height = 480, video = "avc", bytes = 150_000_000),
            fmt("a", audio = "opus", bytes = 5_000_000),
        )
        val options = deriveDownloadOptions(formats)

        assertEquals(listOf("1080p", "720p", "480p", "Audio only"), options.map { it.label })
    }

    @Test fun `paired video-only plus audio-only sums the two file sizes`() {
        val formats = listOf(
            fmt("v", height = 720, video = "avc", bytes = 400_000_000),
            fmt("a", audio = "opus", bytes = 5_000_000),
        )
        val option = deriveDownloadOptions(formats).first { it.label == "720p" }

        assertEquals("v+a", option.formatId)
        assertEquals(405_000_000L, option.approxBytes)
    }

    @Test fun `no options are duplicated when two candidate heights collapse to the same pick`() {
        // "storyboard1080" has a height but is neither video nor audio (yt-dlp really does this
        // for seek-thumbnail sprite tracks) -- it pollutes the candidate height list without
        // giving FormatSelector anything real to choose at 1080p, so both the 1080 and 720
        // ceilings fall through to the exact same video-only+audio pair.
        val formats = listOf(
            fmt("storyboard1080", height = 1080),
            fmt("v720", height = 720, video = "avc", bytes = 400_000_000),
            fmt("a", audio = "opus", bytes = 5_000_000),
        )
        val options = deriveDownloadOptions(formats)

        assertEquals(listOf("720p", "Audio only"), options.map { it.label })
        assertEquals(1, options.count { it.formatId == "v720+a" })
    }

    @Test fun `audio-only is offered even with no video track at all`() {
        val formats = listOf(fmt("a", audio = "opus", bytes = 5_000_000))
        val options = deriveDownloadOptions(formats)

        assertEquals(1, options.size)
        assertEquals("Audio only", options.single().label)
        assertEquals(5_000_000L, options.single().approxBytes)
    }

    @Test fun `empty format list yields no options rather than crashing`() {
        assertTrue(deriveDownloadOptions(emptyList()).isEmpty())
    }

    @Test fun `formats with no usable video, audio or muxed track yield no options`() {
        // Height-bearing but neither video nor audio (e.g. a storyboard track) and nothing else.
        val formats = listOf(fmt("storyboard", height = 360))
        assertTrue(deriveDownloadOptions(formats).isEmpty())
    }

    @Test fun `the synthetic tier-2 selector is never offered as a download option`() {
        val formats = listOf(fmt("webview", height = 1080, video = "avc", audio = "aac"))
        assertTrue(deriveDownloadOptions(formats).isEmpty())
    }

    @Test fun `a single muxed format carries its own file size directly`() {
        val formats = listOf(fmt("muxed480", height = 480, video = "avc", audio = "aac", bytes = 120_000_000))
        val option = deriveDownloadOptions(formats).single()

        assertEquals("muxed480", option.formatId)
        assertEquals("480p", option.label)
        assertEquals(120_000_000L, option.approxBytes)
    }

    @Test fun `manifests are excluded from options when includeManifests is false`() {
        // visionos serves an HLS master alongside progressive streams; StreamDownloader saving
        // that manifest produced a 70 KB .m3u8 as the "finished video" on device.
        val formats = listOf(
            fmt("hls1080", height = 1080, video = "avc", protocol = Protocol.HLS),
            fmt("v720", height = 720, video = "avc", bytes = 400_000_000),
            fmt("a", audio = "opus", bytes = 5_000_000),
        )
        val options = deriveDownloadOptions(formats, includeManifests = false)

        assertEquals(listOf("v720+a", "a"), options.map { it.formatId })
    }

    @Test fun `manifests stay available for the engine path`() {
        val formats = listOf(
            fmt("hls1080", height = 1080, video = "avc", protocol = Protocol.HLS),
            fmt("a", audio = "opus", bytes = 5_000_000),
        )
        val options = deriveDownloadOptions(formats)

        assertEquals(listOf("hls1080", "a"), options.map { it.formatId })
    }
}
