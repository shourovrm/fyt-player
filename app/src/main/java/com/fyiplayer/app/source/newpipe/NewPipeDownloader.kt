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
class NewPipeDownloader(baseClient: OkHttpClient) : Downloader() {

    // OkHttp 4.12 strips only Authorization when a redirect changes host (RetryAndFollowUpInterceptor
    // .buildRedirectRequest); the hand-set Cookie and X-Origin would follow it. A network interceptor
    // runs on every hop, so the session headers are dropped the moment a hop leaves youtube.com.
    // newBuilder shares the caller's connection pool and dispatcher.
    private val client: OkHttpClient = baseClient.newBuilder()
        .addNetworkInterceptor { chain -> chain.proceed(withoutSessionHeadersOffYoutube(chain.request())) }
        .build()

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
                    deliverOnce(callback) {
                        response.use {
                            if (it.code == 429) throw ReCaptchaException("reCaptcha challenge", request.url())
                            it.toNewPipeResponse()
                        }
                    }
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
        val hasCookieHeader = request.headers().keys.any { it.equals("Cookie", ignoreCase = true) }
        if (host != null && isYoutubeHost(host) && !hasCookieHeader) {
            val cookie = YoutubeAuth.cookieHeader()
            val authorization = YoutubeAuth.authorizationHeader()
            if (cookie != null && authorization != null) {
                builder.header("Cookie", cookie)
                builder.header("X-Origin", "https://www.youtube.com")
                builder.header("Authorization", authorization)
                // Marks the request so the network interceptor only strips what this code added;
                // redirect follow-ups are built from the request and keep its tags.
                builder.tag(SessionHeadersInjected::class.java, SessionHeadersInjected)
            }
        }
        return builder.build()
    }
}

/** Tag on requests that carry the session headers added by [NewPipeDownloader.buildOkRequest]. */
internal object SessionHeadersInjected

internal fun isYoutubeHost(host: String): Boolean = host == "youtube.com" || host.endsWith(".youtube.com")

/** Drops the session headers from a tagged request whose target is not a youtube.com host. */
internal fun withoutSessionHeadersOffYoutube(request: OkRequest): OkRequest {
    if (request.tag(SessionHeadersInjected::class.java) == null) return request
    if (isYoutubeHost(request.url.host)) return request
    return request.newBuilder()
        .removeHeader("Cookie")
        .removeHeader("Authorization")
        .removeHeader("X-Origin")
        .build()
}

/**
 * Delivers exactly one terminal callback. A throw from [Downloader.AsyncCallback.onSuccess] is
 * swallowed: calling onError afterwards would be a second terminal callback, and rethrowing would
 * crash OkHttp's dispatcher thread.
 */
internal fun deliverOnce(callback: Downloader.AsyncCallback, produceResponse: () -> Response) {
    val response = try {
        produceResponse()
    } catch (e: Exception) {
        callback.onError(e)
        return
    }
    try {
        callback.onSuccess(response)
    } catch (ignored: Exception) {
        // Nothing left to report to: the consumer already received its one callback.
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
