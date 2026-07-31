package com.fyiplayer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortsNavTest {

    @Test fun `session not started yet does nothing`() {
        assertEquals(ShortsNavAction.NONE, shortsNavAction(page = 0, index = -1))
    }

    @Test fun `settling on the current item does nothing`() {
        assertEquals(ShortsNavAction.NONE, shortsNavAction(page = 2, index = 2))
    }

    @Test fun `settling one page forward reuses the prefetch`() {
        assertEquals(ShortsNavAction.NEXT, shortsNavAction(page = 3, index = 2))
    }

    @Test fun `settling one page back always re-resolves`() {
        assertEquals(ShortsNavAction.PREVIOUS, shortsNavAction(page = 1, index = 2))
    }

    @Test fun `settling more than one page away is a jump`() {
        assertEquals(ShortsNavAction.JUMP, shortsNavAction(page = 5, index = 2))
        assertEquals(ShortsNavAction.JUMP, shortsNavAction(page = 0, index = 3))
    }

    @Test fun `grid index in range maps straight through`() {
        assertEquals(3, clampGridIndex(3, itemCount = 10))
    }

    @Test fun `grid index clamps to the last item when the feed shrank`() {
        assertEquals(4, clampGridIndex(9, itemCount = 5))
    }

    @Test fun `negative grid index clamps to zero`() {
        assertEquals(0, clampGridIndex(-1, itemCount = 5))
    }

    @Test fun `empty feed clamps to zero, never divides or crashes`() {
        assertEquals(0, clampGridIndex(0, itemCount = 0))
    }

    @Test fun `progress fraction is the plain ratio`() {
        assertEquals(0.5f, shortsProgressFraction(positionMs = 5_000, durationMs = 10_000))
    }

    @Test fun `progress fraction with zero duration never divides, renders empty`() {
        assertEquals(0f, shortsProgressFraction(positionMs = 5_000, durationMs = 0))
    }

    @Test fun `progress fraction with unknown negative duration renders empty`() {
        assertEquals(0f, shortsProgressFraction(positionMs = 5_000, durationMs = -1))
    }

    @Test fun `progress fraction clamps past the end to 1`() {
        assertEquals(1f, shortsProgressFraction(positionMs = 12_000, durationMs = 10_000))
    }

    @Test fun `progress fraction clamps a negative position to 0`() {
        assertEquals(0f, shortsProgressFraction(positionMs = -500, durationMs = 10_000))
    }
}
