package com.fyiplayer.app.player

import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient

// Same desktop-Firefox UA the extractor path sends -- googlevideo paces or rejects requests with
// an unfamiliar UA (okhttp's default identifies itself as okhttp).
private const val MEDIA_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

// One counter per process, monotonic across every media request -- mirrors the web player's
// behaviour, which numbers all its media fetches in one sequence.
private val requestNumber = AtomicLong(0)

/**
 * The OkHttp client for MEDIA requests (playback datasource + stream downloads). PipePipe's
 * player (NewPipe's `YoutubeHttpDataSource`) shapes every googlevideo request three ways, and
 * without them googlevideo throttles the transfer to roughly realtime: an incrementing `rn`
 * (request number) query parameter on `/videoplayback` URLs, a real browser User-Agent, and
 * `TE: trailers`. Non-googlevideo URLs pass through untouched apart from the UA.
 */
internal fun mediaHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = chain.request()
        val builder = request.newBuilder()
        if (request.header("User-Agent") == null) builder.header("User-Agent", MEDIA_USER_AGENT)
        if (request.url.encodedPath.startsWith("/videoplayback")) {
            builder.header("TE", "trailers")
            if (request.url.queryParameter("rn") == null) {
                builder.url(
                    request.url.newBuilder()
                        .addQueryParameter("rn", requestNumber.incrementAndGet().toString())
                        .build(),
                )
            }
        }
        chain.proceed(builder.build())
    }
    .build()
