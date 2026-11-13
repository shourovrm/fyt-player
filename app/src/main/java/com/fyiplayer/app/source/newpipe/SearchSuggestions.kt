package com.fyiplayer.app.source.newpipe

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Autocomplete strings for a partial query, from Google's YouTube suggestion service (the only
 * suggestion source wired up). Failures collapse to an empty list -- a dead suggestion endpoint
 * must never surface as a search error.
 */
object SearchSuggestions {
    // Own client and no cookies: a suggestion is typed text, it needs no session, and it must
    // not wait behind the extractor's feed requests.
    private val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()

    suspend fun fetch(query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { parseSuggestions(download(query)) }.getOrDefault(emptyList())
    }

    private fun download(query: String): String {
        // www.google.com, not the extractor's suggestqueries.google.com: both serve the same
        // list, but some DNS resolvers (measured: AdGuard private DNS) send the suggestqueries
        // name to a server ~350 ms away, where www.google.com answers from ~40 ms.
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.google.com")
            .addPathSegments("complete/search")
            .addQueryParameter("client", "firefox") // the client that answers in plain JSON
            .addQueryParameter("ds", "yt")
            .addQueryParameter("oe", "utf-8")
            .addQueryParameter("hl", NewPipeInit.language)
            .addQueryParameter("gl", NewPipeInit.country)
            .addQueryParameter("q", query)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("suggestions HTTP ${response.code}")
            val body = response.body ?: throw IOException("suggestions answer has no body")
            return body.string()
        }
    }
}

/** The service answers `["typed text", ["suggestion", "suggestion", ...]]`. */
internal fun parseSuggestions(body: String): List<String> =
    Json.parseToJsonElement(body).jsonArray[1].jsonArray.map { it.jsonPrimitive.content }
