package com.fyiplayer.app.source.newpipe

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response

// OkHttp 4.12's RetryAndFollowUpInterceptor strips only Authorization on a host change, so the
// session Cookie / X-Origin must be removed by us on every hop that leaves youtube.com.
class NewPipeDownloaderTest {

    private fun sessionRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("Cookie", "SID=secret")
        .header("Authorization", "SAPISIDHASH secret")
        .header("X-Origin", "https://www.youtube.com")
        .header("User-Agent", "ua")
        .tag(SessionHeadersInjected::class.java, SessionHeadersInjected)
        .build()

    @Test
    fun `isYoutubeHost accepts youtube com and subdomains only`() {
        assertTrue(isYoutubeHost("youtube.com"))
        assertTrue(isYoutubeHost("www.youtube.com"))
        assertTrue(!isYoutubeHost("notyoutube.com"))
        assertTrue(!isYoutubeHost("youtube.com.evil.example"))
        assertTrue(!isYoutubeHost("googlevideo.com"))
    }

    @Test
    fun `session headers are removed when a hop leaves youtube`() {
        val stripped = withoutSessionHeadersOffYoutube(sessionRequest("https://evil.example/landing"))
        assertNull(stripped.header("Cookie"))
        assertNull(stripped.header("Authorization"))
        assertNull(stripped.header("X-Origin"))
        assertEquals("ua", stripped.header("User-Agent"))
    }

    @Test
    fun `session headers stay on youtube hops`() {
        val kept = withoutSessionHeadersOffYoutube(sessionRequest("https://m.youtube.com/watch"))
        assertEquals("SID=secret", kept.header("Cookie"))
        assertEquals("SAPISIDHASH secret", kept.header("Authorization"))
    }

    @Test
    fun `a request the extractor cookied itself is left alone`() {
        val consent = Request.Builder().url("https://consent.example/").header("Cookie", "CONSENT=1").build()
        assertEquals("CONSENT=1", withoutSessionHeadersOffYoutube(consent).header("Cookie"))
    }

    private class RecordingCallback : Downloader.AsyncCallback {
        val successes = mutableListOf<Response>()
        val errors = mutableListOf<Exception>()
        var throwOnSuccess = false
        override fun onSuccess(response: Response) {
            successes += response
            if (throwOnSuccess) throw IllegalStateException("consumer blew up")
        }
        override fun onError(exception: Exception) { errors += exception }
    }

    private val sampleResponse = Response(200, "OK", emptyMap(), "body", ByteArray(0), "https://x/")

    @Test
    fun `delivery calls onSuccess once on success`() {
        val callback = RecordingCallback()
        deliverOnce(callback) { sampleResponse }
        assertEquals(1, callback.successes.size)
        assertTrue(callback.errors.isEmpty())
    }

    @Test
    fun `delivery calls onError once when producing the response fails`() {
        val callback = RecordingCallback()
        deliverOnce(callback) { throw java.io.IOException("read failed") }
        assertEquals(1, callback.errors.size)
        assertTrue(callback.successes.isEmpty())
    }

    @Test
    fun `a throwing onSuccess does not trigger onError`() {
        val callback = RecordingCallback().apply { throwOnSuccess = true }
        deliverOnce(callback) { sampleResponse }
        assertEquals(1, callback.successes.size)
        assertTrue(callback.errors.isEmpty())
    }
}
