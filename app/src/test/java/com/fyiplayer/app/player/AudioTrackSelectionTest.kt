package com.fyiplayer.app.player

import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun videoFormat(id: String, height: Int) = MediaFormat(
    formatId = id, url = "https://example.invalid/$id", container = "mp4",
    protocol = Protocol.PROGRESSIVE, height = height, videoCodec = "avc",
)

private fun audioFormat(
    id: String,
    trackId: String?,
    bitrate: Long,
    isOriginal: Boolean,
    label: String? = null,
) = MediaFormat(
    formatId = id, url = "https://example.invalid/$id", container = "webm",
    protocol = Protocol.PROGRESSIVE, audioCodec = "opus", bitrate = bitrate,
    audioTrackId = trackId, audioLabel = label, isOriginalAudio = isOriginal,
)

class AudioTrackSelectionTest {

    private val englishHigh = audioFormat("en-high", "en.0", 128_000, isOriginal = true, label = "English")
    private val englishLow = audioFormat("en-low", "en.0", 48_000, isOriginal = true, label = "English")
    private val spanishHigh = audioFormat("es-high", "es.4", 160_000, isOriginal = false, label = "Spanish")
    private val spanishLow = audioFormat("es-low", "es.4", 64_000, isOriginal = false, label = "Spanish")
    private val videoOnly720 = videoFormat("v720", 720)
    private val dubbedFormats = listOf(videoOnly720, englishHigh, englishLow, spanishHigh, spanishLow)

    @Test fun `default pick is the original track even when a dub has the higher bitrate`() {
        val paired = FormatSelector.select(dubbedFormats, maxHeight = 1080).selection as FormatSelection.Paired
        assertEquals("en-high", paired.audio.formatId)
    }

    @Test fun `an explicit track is picked over the original`() {
        val paired = FormatSelector.select(dubbedFormats, maxHeight = 1080, audioTrackId = "es.4")
            .selection as FormatSelection.Paired
        assertEquals("es.4", paired.audio.audioTrackId)
    }

    @Test fun `inside the explicit track the highest bitrate wins`() {
        val paired = FormatSelector.select(dubbedFormats, maxHeight = 1080, audioTrackId = "es.4")
            .selection as FormatSelection.Paired
        assertEquals("es-high", paired.audio.formatId)
    }

    @Test fun `an unknown track id falls back to the original track`() {
        val paired = FormatSelector.select(dubbedFormats, maxHeight = 1080, audioTrackId = "de.2")
            .selection as FormatSelection.Paired
        assertEquals("en-high", paired.audio.formatId)
    }

    @Test fun `audio-only selection follows the same default and explicit pick`() {
        val defaultPick = FormatSelector.select(dubbedFormats, Int.MAX_VALUE, audioOnly = true)
            .selection as FormatSelection.Single
        val explicitPick = FormatSelector.select(dubbedFormats, Int.MAX_VALUE, audioOnly = true, audioTrackId = "es.4")
            .selection as FormatSelection.Single
        assertEquals("en-high", defaultPick.format.formatId)
        assertEquals("es-high", explicitPick.format.formatId)
    }

    @Test fun `formats without track ids keep the bitrate-only pick`() {
        val formats = listOf(
            videoOnly720,
            audioFormat("a-low", trackId = null, bitrate = 48_000, isOriginal = true),
            audioFormat("a-high", trackId = null, bitrate = 128_000, isOriginal = true),
        )
        val paired = FormatSelector.select(formats, maxHeight = 1080).selection as FormatSelection.Paired
        assertEquals("a-high", paired.audio.formatId)
    }

    @Test fun `a muxed stream is kept for the default pick but skipped for an explicit dub`() {
        val muxed720 = MediaFormat(
            formatId = "muxed720", url = "https://example.invalid/muxed720", container = "mp4",
            protocol = Protocol.PROGRESSIVE, height = 720, videoCodec = "avc", audioCodec = "aac",
        )
        // The best video-only here is 480p, so the 720p muxed stream is the taller option by default.
        val audioOnly = listOf(englishHigh, englishLow, spanishHigh, spanishLow)
        val formats = listOf(videoFormat("v480", 480)) + audioOnly + muxed720

        val defaultPick = FormatSelector.select(formats, maxHeight = 1080).selection
        assertTrue(defaultPick is FormatSelection.Single)
        assertEquals("muxed720", (defaultPick as FormatSelection.Single).format.formatId)

        val dubPick = FormatSelector.select(formats, maxHeight = 1080, audioTrackId = "es.4").selection
        val paired = dubPick as FormatSelection.Paired
        assertEquals("es.4", paired.audio.audioTrackId)
    }

    @Test fun `options list each track once, in platform order, labelled by language`() {
        assertEquals(
            listOf(AudioTrackOption("en.0", "English"), AudioTrackOption("es.4", "Spanish")),
            audioTrackOptionsOf(dubbedFormats),
        )
    }

    @Test fun `options fall back to the track id when a track has no label`() {
        val formats = listOf(audioFormat("de", "de.0", 96_000, isOriginal = false, label = null))
        assertEquals(listOf(AudioTrackOption("de.0", "de.0")), audioTrackOptionsOf(formats))
    }

    @Test fun `no options when no audio format carries a track id`() {
        val formats = listOf(videoOnly720, audioFormat("a", trackId = null, bitrate = 96_000, isOriginal = true))
        assertTrue(audioTrackOptionsOf(formats).isEmpty())
    }

    @Test fun `the selected track id is the audio of a pair and null for a muxed stream`() {
        val paired = FormatSelection.Paired(videoOnly720, spanishHigh)
        assertEquals("es.4", audioTrackIdOf(paired))
        val muxed = FormatSelection.Single(videoFormat("m", 360))
        assertNull(audioTrackIdOf(muxed))
    }

    @Test fun `tracks that share a language name get numbered labels`() {
        val formats = listOf(
            audioFormat("zh-a", "zh.1", 96_000, isOriginal = false, label = "Chinese"),
            audioFormat("zh-b", "zh.2", 96_000, isOriginal = false, label = "Chinese"),
            audioFormat("en", "en.0", 96_000, isOriginal = true, label = "English"),
        )
        assertEquals(listOf("Chinese 1", "Chinese 2", "English"), audioTrackOptionsOf(formats).map { it.label })
    }
}
