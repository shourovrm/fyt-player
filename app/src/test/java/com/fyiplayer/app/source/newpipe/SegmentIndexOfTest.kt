package com.fyiplayer.app.source.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.ItagItem

class SegmentIndexOfTest {

    private fun videoItag(): ItagItem = ItagItem.getItag(137).apply {
        bitrate = 4_100_000
        width = 1920
        height = 1080
        fps = 30
        initStart = 0
        initEnd = 739
        indexStart = 740
        indexEnd = 2063
        approxDurationMs = 213_500
    }

    private fun audioItag(): ItagItem = ItagItem.getItag(140).apply {
        bitrate = 130_000
        sampleRate = 44100
        audioChannels = 2
        initStart = 0
        initEnd = 631
        indexStart = 632
        indexEnd = 1255
        approxDurationMs = 213_480
    }

    @Test fun `video itag maps ranges and stream properties`() {
        val info = segmentIndexOf(videoItag(), "avc1.640028", isAudio = false)!!
        assertEquals(0L, info.initStart)
        assertEquals(739L, info.initEnd)
        assertEquals(740L, info.indexStart)
        assertEquals(2063L, info.indexEnd)
        assertEquals("avc1.640028", info.codecs)
        assertEquals(4_100_000L, info.bitrate)
        assertEquals(213_500L, info.durationMs)
        assertEquals(1920, info.width)
        assertEquals(30, info.frameRate)
        assertNull(info.audioSampleRate)
    }

    @Test fun `audio itag maps sample rate and channels`() {
        val info = segmentIndexOf(audioItag(), "mp4a.40.2", isAudio = true)!!
        assertEquals(44100, info.audioSampleRate)
        assertEquals(2, info.audioChannels)
        assertNull(info.width)
        assertNull(info.frameRate)
    }

    @Test fun `missing ranges give null`() {
        // The extractor stores -1 when the player response had no initRange/indexRange.
        assertNull(segmentIndexOf(videoItag().apply { initEnd = -1 }, "avc1.640028", isAudio = false))
        assertNull(segmentIndexOf(videoItag().apply { indexEnd = -1 }, "avc1.640028", isAudio = false))
    }

    @Test fun `ranges that are not sane give null`() {
        assertNull(segmentIndexOf(videoItag().apply { indexEnd = indexStart }, "avc1.640028", isAudio = false))
        assertNull(segmentIndexOf(videoItag().apply { initEnd = 0 }, "avc1.640028", isAudio = false))
        assertNull(segmentIndexOf(videoItag().apply { indexStart = 100 }, "avc1.640028", isAudio = false))
    }

    @Test fun `missing itag codec or duration give null`() {
        assertNull(segmentIndexOf(null, "avc1.640028", isAudio = false))
        assertNull(segmentIndexOf(videoItag(), "", isAudio = false))
        assertNull(segmentIndexOf(videoItag(), null, isAudio = false))
        assertNull(segmentIndexOf(videoItag().apply { approxDurationMs = -1 }, "avc1.640028", isAudio = false))
    }

    @Test fun `missing peak bitrate gives null`() {
        assertNull(segmentIndexOf(videoItag().apply { bitrate = 0 }, "avc1.640028", isAudio = false))
    }
}
