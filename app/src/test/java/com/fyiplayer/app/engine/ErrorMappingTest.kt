package com.fyiplayer.app.engine

import com.fyiplayer.app.core.ExtractionError
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMappingTest {
    @Test
    fun wallMessageMapsToAccessChallenge() {
        val e = mapEngineError(Exception("Sign in to confirm you're not a bot"))
        assertTrue(e is ExtractionError.AccessChallenge)
    }

    @Test
    fun urlSlugContainingWallWordIsNotAWall() {
        // "premium" only appears inside a URL slug -- scrubbing must remove it before matching,
        // or a purely technical failure would wrongly hard-stop instead of falling to tier2.
        val e = mapEngineError(Exception("Unsupported URL: https://example.invalid/watch/premium-mix-123"))
        assertTrue(e !is ExtractionError.AccessChallenge)
        assertTrue(e is ExtractionError.Unsupported)
    }

    @Test
    fun expiredMapsToExpired() {
        val e = mapEngineError(Exception("HTTP Error 410: Gone, the link has expired"))
        assertTrue(e is ExtractionError.Expired)
    }

    @Test
    fun networkFailureMapsToNetwork() {
        val e = mapEngineError(Exception("Unable to resolve host, connection timed out"))
        assertTrue(e is ExtractionError.Network)
    }

    @Test
    fun contentGoneMapsToContentUnavailable() {
        val e = mapEngineError(Exception("ERROR: [youtube] abc123: Video unavailable"))
        assertTrue(e is ExtractionError.ContentUnavailable)
    }
}
