package com.fyiplayer.app.source.newpipe

import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

// Desktop Firefox UA -- YouTube serves a degraded/bot-flagged response to unfamiliar UAs.
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

/**
 * [Downloader] for tier-0 (NewPipeExtractor). Reuses the app's single [OkHttpClient] -- no
 * separate client, no separate connection pool. Sends the user's own YouTube session cookie
 * (see [YoutubeAuth]) only to youtube.com hosts -- never to googlevideo.com, googleapis.com or
 * any other service (cookie isolation is per-service, a project rule).
 */
class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
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

        client.newCall(builder.build()).execute().use { resp ->
            // 429 is the extractor's own signal to surface a recaptcha/rate-limit wall upstream.
            if (resp.code == 429) throw ReCaptchaException("reCaptcha challenge", request.url())
            val responseBody = resp.body?.string()
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                responseBody,
                resp.request.url.toString(),
            )
        }
    }
}
