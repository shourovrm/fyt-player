package com.fyiplayer.app.player

import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import com.fyiplayer.app.core.SegmentIndexInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashManifestBuilderTest {

    private val videoIndex = SegmentIndexInfo(
        initStart = 0, initEnd = 739, indexStart = 740, indexEnd = 2063,
        codecs = "avc1.640028", bitrate = 4_100_000, durationMs = 213_500,
        width = 1920, frameRate = 30,
    )
    private val audioIndex = SegmentIndexInfo(
        initStart = 0, initEnd = 631, indexStart = 632, indexEnd = 1255,
        codecs = "mp4a.40.2", bitrate = 130_000, durationMs = 213_480,
        audioSampleRate = 44100, audioChannels = 2,
    )

    private fun video(url: String = "https://r1.googlevideo.com/videoplayback?id=a&itag=137", index: SegmentIndexInfo? = videoIndex) =
        MediaFormat(
            formatId = "137", url = url, container = "mp4", protocol = Protocol.PROGRESSIVE,
            height = 1080, videoCodec = "avc1.640028", segmentIndex = index,
        )

    private fun audio(url: String = "https://r1.googlevideo.com/videoplayback?id=a&itag=140", index: SegmentIndexInfo? = audioIndex) =
        MediaFormat(
            formatId = "audio-uuid", url = url, container = "m4a", protocol = Protocol.PROGRESSIVE,
            audioCodec = "mp4a.40.2", segmentIndex = index,
        )

    @Test fun `url ampersands and angle brackets and quotes are escaped`() {
        val manifest = buildDashManifest(
            video("https://h/videoplayback?a=1&b=<2>&c=\"3\""),
            audio(),
        )!!
        assertTrue(manifest.contains("<BaseURL>https://h/videoplayback?a=1&amp;b=&lt;2&gt;&amp;c=&quot;3&quot;</BaseURL>"))
        assertFalse(manifest.contains("a=1&b="))
    }

    @Test fun `both adaptation sets are present with mime types from the container`() {
        val manifest = buildDashManifest(video(), audio())!!
        assertEquals(2, Regex("<AdaptationSet ").findAll(manifest).count())
        assertTrue(manifest.contains("mimeType=\"video/mp4\""))
        assertTrue(manifest.contains("mimeType=\"audio/mp4\""))
    }

    @Test fun `webm containers map to webm mime types`() {
        val webmVideo = video().copy(container = "webm")
        val webmAudio = audio().copy(container = "webm")
        val manifest = buildDashManifest(webmVideo, webmAudio)!!
        assertTrue(manifest.contains("mimeType=\"video/webm\""))
        assertTrue(manifest.contains("mimeType=\"audio/webm\""))
    }

    @Test fun `ranges are rendered for both streams`() {
        val manifest = buildDashManifest(video(), audio())!!
        assertTrue(manifest.contains("indexRange=\"740-2063\""))
        assertTrue(manifest.contains("<Initialization range=\"0-739\"/>"))
        assertTrue(manifest.contains("indexRange=\"632-1255\""))
        assertTrue(manifest.contains("<Initialization range=\"0-631\"/>"))
    }

    @Test fun `representation attributes come from the format and its segment index`() {
        val manifest = buildDashManifest(video(), audio())!!
        assertTrue(manifest.contains("id=\"137\""))
        assertTrue(manifest.contains("codecs=\"avc1.640028\""))
        assertTrue(manifest.contains("bandwidth=\"4100000\""))
        assertTrue(manifest.contains("width=\"1920\""))
        assertTrue(manifest.contains("height=\"1080\""))
        assertTrue(manifest.contains("frameRate=\"30\""))
        assertTrue(manifest.contains("audioSamplingRate=\"44100\""))
        assertTrue(manifest.contains("value=\"2\""))
    }

    @Test fun `presentation duration is the longer stream in seconds with milliseconds`() {
        val manifest = buildDashManifest(video(), audio())!!
        assertTrue(manifest.contains("mediaPresentationDuration=\"PT213.500S\""))
        assertTrue(manifest.contains("type=\"static\""))
    }

    @Test fun `format without segment index is rejected`() {
        assertNull(buildDashManifest(video(index = null), audio()))
        assertNull(buildDashManifest(video(), audio(index = null)))
    }

    @Test fun `unknown container is rejected`() {
        assertNull(buildDashManifest(video().copy(container = "flv"), audio()))
    }

    @Test fun `unusable segment index values are rejected`() {
        assertNull(buildDashManifest(video(index = videoIndex.copy(bitrate = 0)), audio()))
        assertNull(buildDashManifest(video(index = videoIndex.copy(durationMs = 0)), audio()))
        assertNull(buildDashManifest(video(index = videoIndex.copy(codecs = " ")), audio()))
        assertNull(buildDashManifest(video(index = videoIndex.copy(indexEnd = videoIndex.indexStart)), audio()))
    }

    @Test fun `audio channels default to stereo when unknown`() {
        val manifest = buildDashManifest(video(), audio(index = audioIndex.copy(audioChannels = null)))
        assertNotNull(manifest)
        assertTrue(manifest!!.contains("value=\"2\""))
    }
}
