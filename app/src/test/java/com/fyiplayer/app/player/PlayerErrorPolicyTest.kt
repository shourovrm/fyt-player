package com.fyiplayer.app.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun facts(
    code: Int,
    httpStatus: Int? = null,
    transport: Boolean = false,
    surface: Boolean = false,
) = PlayerErrorFacts(code, httpStatus, transport, surface)

private fun budget(
    reResolveSpent: Boolean = false,
    seeksSpent: Int = 0,
    lowerSpent: Boolean = false,
    lowerAvailable: Boolean = true,
    msSinceSurface: Long? = null,
) = PlayerErrorBudget(reResolveSpent, seeksSpent, lowerSpent, lowerAvailable, msSinceSurface)

class PlayerErrorPolicyTest {

    @Test fun `behind live window seeks to default position up to the cap`() {
        val code = PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
        assertEquals(PlayerErrorAction.SEEK_TO_DEFAULT_POSITION, classifyPlayerError(facts(code), budget()))
        assertEquals(
            PlayerErrorAction.SEEK_TO_DEFAULT_POSITION,
            classifyPlayerError(facts(code), budget(seeksSpent = MAX_DEFAULT_POSITION_SEEKS - 1)),
        )
        assertEquals(
            PlayerErrorAction.SHOW_ERROR,
            classifyPlayerError(facts(code), budget(seeksSpent = MAX_DEFAULT_POSITION_SEEKS)),
        )
    }

    @Test fun `expired url re-resolves once then shows the error`() {
        val expired = facts(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpStatus = 410)
        assertEquals(PlayerErrorAction.RERESOLVE_EXPIRED_URL, classifyPlayerError(expired, budget()))
        assertEquals(PlayerErrorAction.SHOW_ERROR, classifyPlayerError(expired, budget(reResolveSpent = true)))
    }

    @Test fun `403 on a fresh url after the re-resolve is never retried`() {
        val refused = facts(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpStatus = 403)
        val afterReResolve = budget(reResolveSpent = true, lowerAvailable = true)
        assertEquals(PlayerErrorAction.SHOW_ERROR, classifyPlayerError(refused, afterReResolve))
    }

    @Test fun `transport failure re-resolves once without invalidating`() {
        val dropped = facts(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, transport = true)
        assertEquals(PlayerErrorAction.RERESOLVE_AFTER_TRANSPORT_FAILURE, classifyPlayerError(dropped, budget()))
        assertEquals(PlayerErrorAction.SHOW_ERROR, classifyPlayerError(dropped, budget(reResolveSpent = true)))
    }

    @Test fun `surface loss re-prepares, then respects the cooldown`() {
        val surfaceLost = facts(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, surface = true)
        assertEquals(PlayerErrorAction.REPREPARE_AFTER_SURFACE_LOSS, classifyPlayerError(surfaceLost, budget()))
        assertEquals(
            PlayerErrorAction.REPREPARE_AFTER_SURFACE_LOSS,
            classifyPlayerError(surfaceLost, budget(msSinceSurface = SURFACE_RECOVERY_COOLDOWN_MS)),
        )
        assertEquals(
            PlayerErrorAction.SHOW_ERROR,
            classifyPlayerError(surfaceLost, budget(msSinceSurface = SURFACE_RECOVERY_COOLDOWN_MS - 1)),
        )
    }

    @Test fun `surface loss never downgrades the rendition`() {
        val surfaceLost = facts(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, surface = true)
        assertEquals(
            PlayerErrorAction.SHOW_ERROR,
            classifyPlayerError(surfaceLost, budget(msSinceSurface = 1, lowerAvailable = true)),
        )
    }

    @Test fun `decoder failure falls back to a lower rendition once`() {
        val decoderFailed = facts(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
        assertEquals(PlayerErrorAction.FALL_BACK_TO_LOWER_RENDITION, classifyPlayerError(decoderFailed, budget()))
        assertEquals(PlayerErrorAction.SHOW_ERROR, classifyPlayerError(decoderFailed, budget(lowerSpent = true)))
        assertEquals(PlayerErrorAction.SHOW_ERROR, classifyPlayerError(decoderFailed, budget(lowerAvailable = false)))
    }

    @Test fun `format exceeding capabilities falls back`() {
        val tooBig = facts(PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES)
        assertEquals(PlayerErrorAction.FALL_BACK_TO_LOWER_RENDITION, classifyPlayerError(tooBig, budget()))
    }

    @Test fun `unrelated codes show the error`() {
        val drm = facts(PlaybackException.ERROR_CODE_DRM_UNSPECIFIED)
        assertEquals(PlayerErrorAction.SHOW_ERROR, classifyPlayerError(drm, budget()))
    }

    @Test fun `lower rendition ceiling is the next height below the selected one`() {
        assertEquals(720, lowerRenditionCeiling(listOf(1080, 720, 480), selectedHeight = 1080))
        assertEquals(480, lowerRenditionCeiling(listOf(1080, 720, 480), selectedHeight = 720))
        assertNull(lowerRenditionCeiling(listOf(1080, 720, 480), selectedHeight = 480))
        assertNull(lowerRenditionCeiling(listOf(720), selectedHeight = null))
    }

    @Test fun `surface released is recognised through the cause chain`() {
        val platform = IllegalArgumentException("The surface has been released")
        val wrapped = RuntimeException("decoder init", platform)
        assertTrue(isSurfaceReleasedFailure(wrapped))
    }

    @Test fun `other decoder failures are not mistaken for surface loss`() {
        assertFalse(isSurfaceReleasedFailure(RuntimeException("no decoder", IllegalStateException("surface"))))
        assertFalse(isSurfaceReleasedFailure(IllegalArgumentException("bad bitrate")))
        assertFalse(isSurfaceReleasedFailure(null))
    }
}
