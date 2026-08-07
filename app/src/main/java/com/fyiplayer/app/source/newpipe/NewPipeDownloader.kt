package com.fyiplayer.app.source.newpipe

import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

// Desktop Firefox UA -- YouTube serves a degraded/bot-flagged response to unfamiliar UAs.
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

/**
 * [Downloader] for tier-0 (PipePipeExtractor). Reuses the app's single [OkHttpClient] -- no
 * separate client, no separate connection pool. Sends the user's own YouTube session cookie
 * (see [YoutubeAuth]) only to youtube.com hosts -- never to googlevideo.com, googleapis.com or
 * any other service (cookie isolation is per-service, a project rule).
 */
class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        client.newCall(buildOkRequest(request)).execute().use { resp ->
            // 429 is the extractor's own signal to surface a recaptcha/rate-limit wall upstream.
            if (resp.code == 429) throw ReCaptchaException("reCaptcha challenge", request.url())
            return resp.toNewPipeResponse()
        }
    }

    /** The extractor fans player/page/next requests out concurrently through this; okhttp's own
     *  enqueue is the natural mapping. [CancellableCall.setFinished] must run on EVERY exit path
     *  -- the extractor awaits it on a latch and a missed call hangs the whole resolve. */
    override fun executeAsync(request: Request, callback: Downloader.AsyncCallback): CancellableCall {
        val call = client.newCall(buildOkRequest(request))
        val cancellable = CancellableCall(call)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                try {
                    callback.onError(e)
                } finally {
                    cancellable.setFinished()
                }
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                try {
                    response.use {
                        if (it.code == 429) {
                            callback.onError(ReCaptchaException("reCaptcha challenge", request.url()))
                        } else {
                            callback.onSuccess(it.toNewPipeResponse())
                        }
                    }
                } catch (e: Exception) {
                    callback.onError(e)
                } finally {
                    cancellable.setFinished()
                }
            }
        })
        return cancellable
    }

    private fun buildOkRequest(request: Request): OkRequest {
        val body = request.dataToSend()?.toRequestBody()
        val builder = OkRequest.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .header("User-Agent", USER_AGENT)

        // Apply verbatim: clear any default for a header name before adding the caller's values.
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        // First-party session, YouTube host only -- cookie isolation is per-service (project rule).
        // Skipped when the extractor already set its own Cookie header (e.g. consent cookie).
        val host = request.url().toHttpUrlOrNull()?.host
        val isYoutubeHost = host != null && (host == "youtube.com" || host.endsWith(".youtube.com"))
        val hasCookieHeader = request.headers().keys.any { it.equals("Cookie", ignoreCase = true) }
        if (isYoutubeHost && !hasCookieHeader) {
            val cookie = YoutubeAuth.cookieHeader()
            val authorization = YoutubeAuth.authorizationHeader()
            if (cookie != null && authorization != null) {
                builder.header("Cookie", cookie)
                builder.header("X-Origin", "https://www.youtube.com")
                builder.header("Authorization", authorization)
            }
        }
        return builder.build()
    }
}

/** Raw bytes ride along with the decoded string: the fork's SABR path reads protobuf bodies. */
private fun okhttp3.Response.toNewPipeResponse(): Response {
    val raw = body?.bytes()
    return Response(
        code,
        message,
        headers.toMultimap(),
        raw?.let { String(it, Charsets.UTF_8) },
        raw ?: ByteArray(0),
        request.url.toString(),
    )
}
