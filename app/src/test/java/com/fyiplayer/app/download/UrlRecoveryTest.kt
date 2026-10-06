package com.fyiplayer.app.download

import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private fun format(
    id: String,
    url: String = "https://example.invalid/$id",
    size: Long? = null,
    protocol: Protocol = Protocol.PROGRESSIVE,
) = MediaFormat(
    formatId = id, url = url, container = "mp4", protocol = protocol,
    height = 720, videoCodec = "avc1", filesizeBytes = size,
)

class UrlRecoveryTest {

    @Test fun `recovers on 403 or 410 after the urls served bytes`() {
        assertTrue(shouldRecoverExpiredUrl(403, urlsServedBytes = true, alreadyRecovered = false))
        assertTrue(shouldRecoverExpiredUrl(410, urlsServedBytes = true, alreadyRecovered = false))
    }

    @Test fun `a 403 on a fresh url set that never served bytes is not retried`() {
        assertFalse(shouldRecoverExpiredUrl(403, urlsServedBytes = false, alreadyRecovered = false))
    }

    @Test fun `only one recovery per run`() {
        assertFalse(shouldRecoverExpiredUrl(403, urlsServedBytes = true, alreadyRecovered = true))
    }

    @Test fun `other statuses never recover`() {
        assertFalse(shouldRecoverExpiredUrl(401, urlsServedBytes = true, alreadyRecovered = false))
        assertFalse(shouldRecoverExpiredUrl(429, urlsServedBytes = true, alreadyRecovered = false))
        assertFalse(shouldRecoverExpiredUrl(500, urlsServedBytes = true, alreadyRecovered = false))
    }

    @Test fun `same format is matched by id`() {
        val fresh = listOf(format("137", url = "https://new.invalid/137"), format("140"))
        assertEquals("https://new.invalid/137", sameFormatIn(fresh, format("137"))?.url)
    }

    @Test fun `a format that disappeared gives null`() {
        assertNull(sameFormatIn(listOf(format("140")), format("137")))
    }

    @Test fun `a manifest with the same id is not the same stream`() {
        assertNull(sameFormatIn(listOf(format("137", protocol = Protocol.HLS)), format("137")))
    }

    @Test fun `a different published size means a different file`() {
        assertNull(sameFormatIn(listOf(format("137", size = 2_000)), format("137", size = 1_000)))
        assertEquals("137", sameFormatIn(listOf(format("137", size = 1_000)), format("137", size = 1_000))?.formatId)
        assertEquals("137", sameFormatIn(listOf(format("137")), format("137", size = 1_000))?.formatId)
    }

    @Test fun `recover spends the single recovery and later reads use the fresh urls`() = runBlocking {
        var resolveCalls = 0
        val recovery = UrlRecovery {
            resolveCalls++
            listOf(format("137", url = "https://new.invalid/137"), format("140", url = "https://new.invalid/140"))
        }
        val stale = format("137")
        assertSame(stale, recovery.current(stale))

        val fresh = recovery.recover(stale)
        assertEquals("https://new.invalid/137", fresh?.url)
        assertTrue(recovery.used)
        assertEquals("https://new.invalid/140", recovery.current(format("140")).url)
        assertEquals(1, resolveCalls)
    }

    @Test fun `a failed re-resolve recovers nothing`() = runBlocking {
        val recovery = UrlRecovery { null }
        assertNull(recovery.recover(format("137")))
        assertTrue(recovery.used)
    }
}
