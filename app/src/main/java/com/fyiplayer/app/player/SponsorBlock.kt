package com.fyiplayer.app.player

import com.fyiplayer.app.core.SponsorCategory
import com.fyiplayer.app.core.SponsorSegment
import java.net.URLDecoder
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private val jsonCodec = Json { ignoreUnknownKeys = true }
private val httpClient = OkHttpClient()

@Serializable
private data class SegmentEntryJson(
    val category: String? = null,
    val segment: List<Double> = emptyList(),
    // "skip" for a normal segment; "mute"/"full"/"poi" cannot be honoured by seeking past them.
    val actionType: String? = null,
)

@Serializable
private data class VideoEntryJson(val videoID: String? = null, val segments: List<SegmentEntryJson> = emptyList())

/**
 * SponsorBlock's k-anonymity endpoint: only a 4-hex-char prefix of sha256(videoId) is sent, never
 * the full id, so the server can't correlate a client with the exact video watched. Every failure
 * (network, 404, malformed JSON) collapses to an empty list -- gated by Prefs.sponsorBlock, a miss
 * here must just mean "play normally", never a visible error.
 */
object SponsorBlock {
    /** Every category is requested whatever its current mode, so a mode changed mid-video takes
     *  effect at once. The request carries no more about the viewer than before: the hash prefix
     *  is the only thing that identifies the video. */
    private val CATEGORIES_QUERY = SponsorCategory.entries.joinToString(",", "[", "]") { "\"${it.apiName}\"" }

    suspend fun fetchSponsorSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        try {
            val url = "https://sponsor.ajay.app/api/skipSegments/${sha256Prefix(videoId)}".toHttpUrl().newBuilder()
                .addQueryParameter("categories", CATEGORIES_QUERY)
                .build()
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (resp.code == 404 || !resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                parseSponsorSegments(body, videoId)
            }
        } catch (e: CancellationException) {
            throw e // structured concurrency: never swallow a cancel as "no segments"
        } catch (e: Exception) {
            emptyList() // network/JSON failure -- never surfaced to the caller, see class doc
        }
    }
}

/** The usable segments of [videoId] in a k-anonymity response (which lists every video sharing the
 *  hash prefix). Unknown categories, non-skip actions and empty or inverted windows are dropped. */
internal fun parseSponsorSegments(responseBody: String, videoId: String): List<SponsorSegment> =
    jsonCodec.decodeFromString(ListSerializer(VideoEntryJson.serializer()), responseBody)
        .firstOrNull { it.videoID == videoId }
        ?.segments.orEmpty()
        .filter { it.segment.size == 2 && (it.actionType == null || it.actionType == "skip") }
        .mapNotNull { entry ->
            val category = SponsorCategory.fromApiName(entry.category) ?: return@mapNotNull null
            val startMs = (entry.segment[0] * 1000).toLong()
            val endMs = (entry.segment[1] * 1000).toLong()
            if (endMs <= startMs) null else SponsorSegment(startMs, endMs, category)
        }
        .sortedBy { it.startMs }

private fun sha256Prefix(videoId: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(4)
}

/** `youtube.com/watch?v=`, `youtu.be/`, or `/shorts/` -- the bare video id. Duplicated from
 *  ui/DescriptionTab.kt's youtubeVideoId rather than shared: player/ must not import ui/. */
internal fun youtubeVideoId(url: String): String? {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    val path = uri.path ?: ""
    return when {
        host == "youtu.be" -> path.trim('/').substringBefore('/').takeIf { it.isNotEmpty() }
        host.endsWith("youtube.com") && path == "/watch" -> queryParam(url, "v")
        host.endsWith("youtube.com") && path.startsWith("/shorts/") ->
            path.removePrefix("/shorts/").substringBefore('/').takeIf { it.isNotEmpty() }
        else -> null
    }
}

private fun queryParam(url: String, name: String): String? {
    val query = runCatching { java.net.URI(url) }.getOrNull()?.query ?: return null
    return query.split('&').firstNotNullOfOrNull { pair ->
        val eq = pair.indexOf('=')
        if (eq < 0 || pair.substring(0, eq) != name) return@firstNotNullOfOrNull null
        val raw = pair.substring(eq + 1)
        runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }
}
