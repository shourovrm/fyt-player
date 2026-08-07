package com.fyiplayer.app.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleStreamTest {

    private val now = 1_000_000_000L

    @Test fun `never resolved is never stale`() {
        assertFalse(isStale(0L, now))
    }

    @Test fun `fresh resolve is not stale`() {
        assertFalse(isStale(now - 60_000L, now)) // 1 minute ago
    }

    @Test fun `just under the threshold is not stale`() {
        assertFalse(isStale(now - 50 * 60_000L, now)) // exactly 50 minutes ago
    }

    @Test fun `past the threshold is stale`() {
        assertTrue(isStale(now - 51 * 60_000L, now)) // 51 minutes ago
    }
}
