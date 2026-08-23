package com.fyiplayer.app.player

import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fmt(
    id: String,
    height: Int? = null,
    video: String? = null,
    audio: String? = null,
    bitrate: Long? = null,
    protocol: Protocol = Protocol.PROGRESSIVE,
) = MediaFormat(
    formatId = id, url = "https://example.invalid/$id", container = "mp4",
    protocol = protocol, height = height, videoCodec = video, audioCodec = audio, bitrate = bitrate,
)

class FormatSelectorTest {

    @Test fun `respects the height ceiling`() {
        val formats = listOf(
            fmt("v4k", height = 2160, video = "avc"),
            fmt("v720", height = 720, video = "avc"),
            fmt("a", audio = "opus", bitrate = 128_000),
        )
        val result = FormatSelector.select(formats, maxHeight = 1080)
        val paired = result.selection as FormatSelection.Paired
        assertEquals(720, paired.video.height)
    }

    @Test fun `video-only plus audio-only beats a lower muxed format`() {
        val formats = listOf(
            fmt("v1080", height = 1080, video = "avc"),
            fmt("a", audio = "opus", bitrate = 128_000),
            fmt("muxed480", height = 480, video = "avc", audio = "aac"),
        )
        val result = FormatSelector.select(formats, maxHeight = 1080)
        assertTrue(result.selection is FormatSelection.Paired)
        val paired = result.selection as FormatSelection.Paired
        assertEquals(1080, paired.video.height)
    }

    @Test fun `a higher muxed format beats a lower video-only pairing`() {
        val formats = listOf(
            fmt("v480", height = 480, video = "avc"),
            fmt("a", audio = "opus", bitrate = 128_000),
            fmt("muxed720", height = 720, video = "avc", audio = "aac"),
        )
        val result = FormatSelector.select(formats, maxHeight = 1080)
        val single = result.selection as FormatSelection.Single
        assertEquals("muxed720", single.format.formatId)
    }

    @Test fun `a progressive pair is preferred over a manifest, manifest is the fallback`() {
        val formats = listOf(
            fmt("hls", height = 1080, protocol = Protocol.HLS),
            fmt("v1080", height = 1080, video = "avc"),
            fmt("a", audio = "opus", bitrate = 128_000),
            fmt("muxed720", height = 720, video = "avc", audio = "aac"),
        )
        val paired = FormatSelector.select(formats, maxHeight = 1080).selection as FormatSelection.Paired
        assertEquals("v1080", paired.video.formatId)
        assertEquals("a", paired.audio.formatId)

        val hlsOnly = FormatSelector.select(listOf(formats[0], formats[2]), maxHeight = 1080).selection as FormatSelection.Single
        assertEquals("hls", hlsOnly.format.formatId)
    }

    @Test fun `a manifest above the ceiling is skipped, not force-fit`() {
        val formats = listOf(
            fmt("hls4k", height = 2160, protocol = Protocol.HLS),
            fmt("muxed720", height = 720, video = "avc", audio = "aac"),
        )
        val result = FormatSelector.select(formats, maxHeight = 1080)
        val single = result.selection as FormatSelection.Single
        assertEquals("muxed720", single.format.formatId)
    }

    @Test fun `audio-only mode picks the best audio-only track regardless of video`() {
        val formats = listOf(
            fmt("v1080", height = 1080, video = "avc"),
            fmt("aLow", audio = "opus", bitrate = 64_000),
            fmt("aHigh", audio = "opus", bitrate = 192_000),
            fmt("muxed720", height = 720, video = "avc", audio = "aac"),
        )
        val result = FormatSelector.select(formats, maxHeight = 1080, audioOnly = true)
        val single = result.selection as FormatSelection.Single
        assertEquals("aHigh", single.format.formatId)
    }

    @Test fun `empty input selects nothing and says why`() {
        val result = FormatSelector.select(emptyList(), maxHeight = 1080)
        assertNull(result.selection)
        assertNotNull(result.reason)
    }

    @Test fun `audio-only mode with no audio track selects nothing`() {
        val result = FormatSelector.select(listOf(fmt("v1080", height = 1080, video = "avc")), maxHeight = 1080, audioOnly = true)
        assertNull(result.selection)
        assertNotNull(result.reason)
    }

    @Test fun `video-only with no audio track and no muxed option selects nothing`() {
        val result = FormatSelector.select(listOf(fmt("v1080", height = 1080, video = "avc")), maxHeight = 1080)
        assertNull(result.selection)
        assertNotNull(result.reason)
    }
}

class MuxedWithoutHeightTest {
    // Facebook's sd/hd arrive with no height and engine-unprobed codecs; still the only stream.
    @Test fun `a muxed stream with unknown height is selectable`() {
        val muxed = fmt("hd", height = null, video = "unknown", audio = "unknown")
        val result = FormatSelector.select(listOf(muxed), maxHeight = 1080)
        assertEquals(FormatSelection.Single(muxed), result.selection)
    }

    @Test fun `a paired stream still beats an unknown-height muxed one`() {
        val v = fmt("v", height = 720, video = "avc1")
        val a = fmt("a", audio = "mp4a", bitrate = 128_000)
        val muxed = fmt("hd", height = null, video = "unknown", audio = "unknown")
        val result = FormatSelector.select(listOf(v, a, muxed), maxHeight = 1080)
        assertEquals(FormatSelection.Paired(v, a), result.selection)
    }
}
