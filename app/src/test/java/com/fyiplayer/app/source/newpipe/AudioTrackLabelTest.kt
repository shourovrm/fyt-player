package com.fyiplayer.app.source.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTrackLabelTest {

    @Test fun `an untagged track counts as original, as it always has`() {
        assertTrue(isOriginalAudioTrack(null))
    }

    @Test fun `a platform name saying original or default marks the original track`() {
        assertTrue(isOriginalAudioTrack("English (United States) original"))
        assertTrue(isOriginalAudioTrack("English default"))
    }

    @Test fun `a dubbed track name is not original`() {
        assertFalse(isOriginalAudioTrack("Spanish (dubbed)"))
    }

    @Test fun `with no language code the platform track name is the label`() {
        assertEquals("Spanish dub", audioTrackLabelOf(languageCode = null, trackName = "Spanish dub", isOriginal = false))
    }

    @Test fun `with neither language code nor name there is no label`() {
        assertNull(audioTrackLabelOf(languageCode = null, trackName = null, isOriginal = false))
    }

    @Test fun `the original track label is marked original`() {
        // The language name depends on the device locale, so only the marker is asserted.
        val label = audioTrackLabelOf(languageCode = "en", trackName = null, isOriginal = true)
        assertTrue(label != null && label.endsWith(" (original)"))
    }

    @Test fun `a dub label is not marked original`() {
        val label = audioTrackLabelOf(languageCode = "es", trackName = null, isOriginal = false)
        assertTrue(label != null && !label.endsWith("(original)"))
    }
}
