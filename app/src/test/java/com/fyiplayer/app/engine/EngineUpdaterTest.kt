package com.fyiplayer.app.engine

import com.yausername.youtubedl_android.YoutubeDL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUpdaterTest {
    @Test
    fun networkExceptionMapsToNetworkReason() {
        val reason = engineUpdateFailureReason(Exception("unable to resolve host"))
        assertTrue(reason.contains("Network", ignoreCase = true))
    }

    @Test
    fun timeoutMapsToNetworkReason() {
        val reason = engineUpdateFailureReason(Exception("connect timed out"))
        assertTrue(reason.contains("Network", ignoreCase = true))
    }

    @Test
    fun unknownExceptionMapsToGenericReason() {
        val reason = engineUpdateFailureReason(Exception("some obscure yt-dlp stack trace"))
        assertEquals("Update failed. Try again later.", reason)
    }

    @Test
    fun nullMessageMapsToGenericReason() {
        val reason = engineUpdateFailureReason(RuntimeException())
        assertEquals("Update failed. Try again later.", reason)
    }

    @Test
    fun channelMapsToConfirmedSdkConstants() {
        assertEquals(YoutubeDL.UpdateChannel._STABLE, EngineChannel.STABLE.toSdkChannel())
        assertEquals(YoutubeDL.UpdateChannel._NIGHTLY, EngineChannel.NIGHTLY.toSdkChannel())
        assertEquals(YoutubeDL.UpdateChannel._MASTER, EngineChannel.MASTER.toSdkChannel())
    }
}
