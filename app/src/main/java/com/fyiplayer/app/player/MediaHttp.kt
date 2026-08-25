package com.fyiplayer.app.player

import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient

// For non-media hosts (thumbnails, storyboards) a browser UA stays -- some CDNs pace unknown UAs.
private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

// One counter per process, monotonic across every media request -- mirrors the web player's
// behaviour, which numbers all its media fetches in one sequence.
private val requestNumber = AtomicLong(0)

private const val YOUTUBE_ORIGIN = "https://www.youtube.com"

/**
 * The OkHttp client for MEDIA requests (playback datasource + stream downloads), shaped like
 * PipePipe's `YoutubeHttpDataSource` for `/videoplayback` URLs:
 *  - incrementing `rn` query parameter and `TE: trailers` on every request;
 *  - the User-Agent must MATCH the innertube client that signed the URL (its `c=` param), never
 *    a desktop browser: a Chrome/Firefox UA on a visionos/TVHTML5-signed URL is a mismatch
 *    googlevideo intermittently 403s (seen live). PipePipe sends the platform default UA for
 *    everything that isn't ANDROID/IOS-signed; `http.agent` is that default.
 *  - `Origin`/`Referer`/`Sec-Fetch-*` only for WEB/TVHTML5-family URLs, exactly like PipePipe.
 * Non-googlevideo URLs pass through with just the browser UA.
 */
internal fun mediaHttpClient(): OkHttpClient = OkHttpClient.Builder()
    // 8s/8s = media3's DefaultHttpDataSource defaults (what PipePipe ships). OkHttp's 10s/10s
    // plus 3 backoff retries cost 29s of spinner on a WiFi->LTE handover (seen live, shorts pager).
    .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
    .addInterceptor { chain ->
        val request = chain.request()
        val builder = request.newBuilder()
        if (request.url.encodedPath.startsWith("/videoplayback")) {
            val client = request.url.queryParameter("c").orEmpty()
            val platformUa = System.getProperty("http.agent")
            if (request.header("User-Agent") == null && platformUa != null) {
                builder.header("User-Agent", platformUa)
            }
            if (client.startsWith("WEB") || client.startsWith("TVHTML5")) {
                builder.header("Origin", YOUTUBE_ORIGIN)
                builder.header("Referer", YOUTUBE_ORIGIN)
                builder.header("Sec-Fetch-Dest", "empty")
                builder.header("Sec-Fetch-Mode", "cors")
                builder.header("Sec-Fetch-Site", "cross-site")
            }
            builder.header("TE", "trailers")
            if (request.url.queryParameter("rn") == null) {
                builder.url(
                    request.url.newBuilder()
                        .addQueryParameter("rn", requestNumber.incrementAndGet().toString())
                        .build(),
                )
            }
        } else if (request.header("User-Agent") == null) {
            builder.header("User-Agent", BROWSER_USER_AGENT)
        }
        val shaped = builder.build()
        val response = chain.proceed(shaped)
        // Non-YouTube CDN refusal: host + header NAMES + code only. Never values (cookies,
        // signed paths) -- enough to tell "Cookie never attached" from "Cookie rejected".
        if (!shaped.url.encodedPath.startsWith("/videoplayback") && response.code in 400..499) {
            android.util.Log.d("MediaHttp", "cdn ${shaped.url.host} code=${response.code} headers=${shaped.headers.names()}")
        }
        if (shaped.url.encodedPath.startsWith("/videoplayback")) {
            // client name + booleans only -- the URL itself is signed and must never be logged
            android.util.Log.d(
                "MediaHttp",
                "videoplayback c=${shaped.url.queryParameter("c")} " +
                    "ua=${shaped.header("User-Agent")?.substringBefore('/')} " +
                    "range=${shaped.url.queryParameter("range") != null} " +
                    "rangeHeader=${shaped.header("Range") != null} " +
                    "rn=${shaped.url.queryParameter("rn") != null} code=${response.code}",
            )
        }
        response
    }
    .build()
