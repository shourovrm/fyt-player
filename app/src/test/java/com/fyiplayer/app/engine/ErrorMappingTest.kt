package com.fyiplayer.app.engine

import com.fyiplayer.app.core.AccessChallengeReason
import com.fyiplayer.app.core.ExtractionError
import org.junit.Assert.assertEquals
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

    @Test
    fun http429MapsToRateLimitWall() {
        val e = mapEngineError(Exception("ERROR: unable to download video data: HTTP Error 429: Too Many Requests"))
        assertTrue(e is ExtractionError.AccessChallenge)
        assertEquals(AccessChallengeReason.RATE_LIMIT, (e as ExtractionError.AccessChallenge).reason)
    }

    @Test
    fun rateLimitWordingMapsToRateLimitWall() {
        val e = mapEngineError(Exception("ERROR: This content isn't available, try again later. The current session has been rate-limited"))
        assertEquals(AccessChallengeReason.RATE_LIMIT, (e as ExtractionError.AccessChallenge).reason)
    }

    @Test
    fun http403MapsToWallNotFallthrough() {
        val e = mapEngineError(Exception("ERROR: unable to download video data: HTTP Error 403: Forbidden"))
        assertTrue(e is ExtractionError.AccessChallenge)
    }

    @Test
    fun statusDigitsInsideAnIdAreNotAWall() {
        // A video id like "x4290403y" contains 429 and 403 but is not an HTTP status.
        val e = mapEngineError(Exception("ERROR: [tiktok] x4290403y: Unable to extract webpage video data"))
        assertTrue(e !is ExtractionError.AccessChallenge)
    }

    @Test
    fun botCheckWinsOverItsSignInWording() {
        val e = mapEngineError(Exception("Sign in to confirm you're not a bot"))
        assertEquals(AccessChallengeReason.BOT_CHECK, (e as ExtractionError.AccessChallenge).reason)
    }

    @Test
    fun confirmYourAgeIsAnAgeWallNotABotCheck() {
        val e = mapEngineError(Exception("Sign in to confirm your age"))
        assertEquals(AccessChallengeReason.AGE_RESTRICTION, (e as ExtractionError.AccessChallenge).reason)
    }
}
