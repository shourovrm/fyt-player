package com.fyiplayer.app.source.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pure helpers only -- no network, no NewPipe.init.
class NewPipeYoutubeSourceTest {

    @Test
    fun belowOneThousandIsExact() {
        assertEquals("999", compactCount(999))
        assertEquals("0", compactCount(0))
    }

    @Test
    fun thousandsDropTrailingZeroDecimal() {
        assertEquals("1K", compactCount(1_000))
        assertEquals("1.2K", compactCount(1_200))
        assertEquals("999K", compactCount(999_000))
    }

    @Test
    fun millionsAndBillionsFollowTheSamePattern() {
        assertEquals("1M", compactCount(1_000_000))
        assertEquals("1.2M", compactCount(1_200_000))
        assertEquals("1B", compactCount(1_000_000_000))
    }

    @Test
    fun negativeIsUnknownNeverInvented() {
        assertNull(compactCount(-1))
        assertNull(compactCount(-100))
    }
}
