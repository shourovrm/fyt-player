package com.fyiplayer.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.VideoRef
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TIMEOUT_MS = 15_000L
private val MEDIA_PATTERNS = listOf(".m3u8", ".mp4", "videoplayback")

/**
 * Tier 2 of the resolver chain: a headless [WebView] loads the page and observes the player's
 * own first media request instead of parsing markup. STRICTLY READ-ONLY: [shouldInterceptRequest]
 * always returns null (observe, never substitute), and the only JS ever run is `video.play()` --
 * no form fill, no wall dismissal. Must run on the main thread (WebView requirement), so every
 * touch is dispatched through [Handler].
 */
class WebViewResolver(private val context: Context) : StreamResolver {

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun resolve(ref: VideoRef): Resolved = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        val finished = AtomicBoolean(false)

        fun finish(deliver: () -> Unit) {
            if (!finished.compareAndSet(false, true)) return
            handler.post { webView?.stopLoading(); webView?.destroy(); webView = null }
            deliver()
        }

        val timeoutRunnable = Runnable {
            finish { cont.resumeWithException(ExtractionError.Unsupported("no media request observed")) }
        }

        handler.post {
            try {
                val wv = WebView(context)
                webView = wv
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.mediaPlaybackRequiresUserGesture = false
                wv.webViewClient = object : WebViewClient() {
                    // Called on a WebView-internal thread, not necessarily the UI thread.
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url.toString()
                        if (MEDIA_PATTERNS.any { url.contains(it, ignoreCase = true) }) {
                            val protocol = if (url.contains(".m3u8", ignoreCase = true)) {
                                Protocol.HLS
                            } else {
                                Protocol.PROGRESSIVE
                            }
                            finish { cont.resume(buildResolved(ref, url, protocol, request.requestHeaders)) }
                        }
                        return null // observe only, request always passes through unmodified
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        // Nudge autoplay so the player fires its media request. Nothing else runs.
                        view.evaluateJavascript("document.querySelector('video')?.play(); void 0;", null)
                    }
                }
                wv.loadUrl(ref.pageUrl)
            } catch (e: Throwable) {
                finish { cont.resumeWithException(ExtractionError.Unsupported("WebView unavailable", e)) }
            }
        }

        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        cont.invokeOnCancellation {
            handler.removeCallbacks(timeoutRunnable)
            finish {}
        }
    }
}

private fun buildResolved(
    ref: VideoRef,
    url: String,
    protocol: Protocol,
    headers: Map<String, String>,
): Resolved {
    val format = MediaFormat(
        formatId = "webview",
        url = url,
        container = if (protocol == Protocol.HLS) "m3u8" else "mp4",
        protocol = protocol,
        headers = headers,
    )
    return Resolved(ref = ref, formats = listOf(format), resolvedAtMillis = System.currentTimeMillis())
}
