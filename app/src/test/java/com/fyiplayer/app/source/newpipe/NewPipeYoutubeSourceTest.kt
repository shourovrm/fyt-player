package com.fyiplayer.app.source.newpipe

import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun futureDateIsUpcoming() {
        val now = OffsetDateTime.parse("2026-08-08T00:00:00Z")
        val premiereStart = DateWrapper(now.plusHours(2))
        assertTrue(isUpcoming(premiereStart, now))
    }

    @Test
    fun pastOrNullDateIsNotUpcoming() {
        val now = OffsetDateTime.parse("2026-08-08T00:00:00Z")
        assertFalse(isUpcoming(DateWrapper(now.minusDays(1)), now))
        assertFalse(isUpcoming(null, now))
    }
}
