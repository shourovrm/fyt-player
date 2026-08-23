package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaCacheKeyFactoryTest {

    @Test fun `googlevideo videoplayback keys on id, itag, lmt -- not the signed URL`() {
        assertEquals(
            "yt|abc123|136|1600000000000000",
            cacheKeyFor(
                encodedPath = "/videoplayback",
                fullUrl = "https://rr1---sn-xyz.googlevideo.com/videoplayback?id=abc123&itag=136&lmt=1600000000000000&sig=SECRET",
                id = "abc123",
                itag = "136",
                lmt = "1600000000000000",
            ),
        )
    }

    @Test fun `non-googlevideo URL (tiktok CDN) hashes to sha256`() {
        val url = "https://v16-webapp.tiktok.com/abc/video.mp4?x-expires=123&x-signature=xyz"
        val key = cacheKeyFor(encodedPath = "/abc/video.mp4", fullUrl = url, id = null, itag = null, lmt = null)
        // SHA-256 hex: 64 lowercase hex chars, never the URL itself.
        assertEquals(64, key.length)
        assertNotEquals(url, key)
        assertEquals(true, key.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test fun `videoplayback missing one of id-itag-lmt falls back to hash, not a partial key`() {
        val url = "https://x.googlevideo.com/videoplayback?id=abc&itag=136"
        val key = cacheKeyFor(encodedPath = "/videoplayback", fullUrl = url, id = "abc", itag = "136", lmt = null)
        assertEquals(64, key.length) // sha256 hex, not "yt|abc|136|null"
    }

    @Test fun `range query param does not affect the key -- cache sits outside the chunker`() {
        val withRange = cacheKeyFor("/videoplayback", "ignored", "abc123", "136", "1600000000000000")
        val withoutRange = cacheKeyFor("/videoplayback", "ignored-different-full-url", "abc123", "136", "1600000000000000")
        // Same id/itag/lmt -> same key regardless of what the full URL (and any range= it may
        // carry) looks like -- proves chunk windows of the same file share one cache key.
        assertEquals(withRange, withoutRange)
    }

    @Test fun `HLS path-encoded videoplayback URL (no id-itag-lmt query) hashes instead`() {
        val url = "https://x.googlevideo.com/videoplayback/id/abc/itag/136/lmt/1600000000000000"
        val key = cacheKeyFor(encodedPath = "/videoplayback/id/abc/itag/136/lmt/1600000000000000", fullUrl = url, id = null, itag = null, lmt = null)
        assertEquals(64, key.length)
    }
}
