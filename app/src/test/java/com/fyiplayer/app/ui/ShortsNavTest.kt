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
}
