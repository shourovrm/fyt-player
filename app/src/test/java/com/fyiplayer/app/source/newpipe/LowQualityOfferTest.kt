package com.fyiplayer.app.source.newpipe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowQualityOfferTest {
    @Test fun `only the muxed 360p stream is low quality`() {
        // What VisionOS leaves for made-for-kids videos: YouTube refuses the request and the
        // extractor keeps only the Android client's single muxed itag 18.
        assertTrue(LowQualityOffer.isLowQualityOnly(hasMasterPlaylist = false, videoHeights = listOf(360)))
    }

    @Test fun `no video stream at all is not low quality`() {
        assertFalse(LowQualityOffer.isLowQualityOnly(hasMasterPlaylist = false, videoHeights = emptyList()))
    }

    @Test fun `a master playlist is not low quality`() {
        assertFalse(LowQualityOffer.isLowQualityOnly(hasMasterPlaylist = true, videoHeights = listOf(360)))
    }

    @Test fun `any stream above 360p is not low quality`() {
        assertFalse(LowQualityOffer.isLowQualityOnly(hasMasterPlaylist = false, videoHeights = listOf(360, 720)))
    }
}
