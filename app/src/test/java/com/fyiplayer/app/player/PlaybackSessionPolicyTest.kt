package com.fyiplayer.app.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionPolicyTest {

    @Test fun `a rebuffer is not a stop`() {
        assertFalse(shouldPersistOnStop(playWhenReady = true, playbackState = Player.STATE_BUFFERING))
    }

    @Test fun `a user pause persists`() {
        assertTrue(shouldPersistOnStop(playWhenReady = false, playbackState = Player.STATE_READY))
        assertTrue(shouldPersistOnStop(playWhenReady = false, playbackState = Player.STATE_BUFFERING))
    }

    @Test fun `ended and idle persist`() {
        assertTrue(shouldPersistOnStop(playWhenReady = true, playbackState = Player.STATE_ENDED))
        assertTrue(shouldPersistOnStop(playWhenReady = true, playbackState = Player.STATE_IDLE))
    }

    @Test fun `sponsor guard holds while at or past the skipped segment`() {
        assertEquals(10_000L, sponsorSkipGuardAfter(10_000L, positionMs = 10_000L)) // still inside after the seek lands late
        assertEquals(10_000L, sponsorSkipGuardAfter(10_000L, positionMs = 45_000L))
    }

    @Test fun `sponsor guard resets once playback is back before the segment`() {
        // Loop restart or a seek back: the segment must be skippable again.
        assertNull(sponsorSkipGuardAfter(10_000L, positionMs = 0L))
        assertNull(sponsorSkipGuardAfter(10_000L, positionMs = 9_999L))
    }

    @Test fun `sponsor guard stays null when nothing was skipped`() {
        assertNull(sponsorSkipGuardAfter(null, positionMs = 5_000L))
    }
}
