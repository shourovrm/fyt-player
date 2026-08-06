package com.fyiplayer.app.source.newpipe

import java.io.IOException
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
 * separate client, no separate connection pool. Sends no cookies: this tier only ever resolves a
 * public watch page, never anything behind a wall.
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
