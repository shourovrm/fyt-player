package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SegmentStartTest {
    private val segmentStartsUs = longArrayOf(0, 5_000_000, 10_000_000, 15_000_000)

    @Test
    fun `position inside a segment maps to that segment's start`() {
        assertEquals(5_000_000L, segmentStartAtOrBefore(segmentStartsUs, 7_300_000))
    }

    @Test
    fun `position exactly on a boundary maps to that boundary`() {
        assertEquals(10_000_000L, segmentStartAtOrBefore(segmentStartsUs, 10_000_000))
    }

    @Test
    fun `position past the last start maps to the last segment`() {
        assertEquals(15_000_000L, segmentStartAtOrBefore(segmentStartsUs, 99_000_000))
    }

    @Test
    fun `position inside the first segment maps to zero`() {
        assertEquals(0L, segmentStartAtOrBefore(segmentStartsUs, 1_200_000))
    }

    @Test
    fun `position before the first segment has no segment`() {
        assertNull(segmentStartAtOrBefore(longArrayOf(2_000_000, 7_000_000), 500_000))
    }

    @Test
    fun `empty index has no segment`() {
        assertNull(segmentStartAtOrBefore(longArrayOf(), 4_000_000))
    }
}
