package com.fyiplayer.app.engine

import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.VideoRef
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val jsonCodec = Json { ignoreUnknownKeys = true }

/** Tier 1 of the resolver chain: the engine's own info JSON, no markup parsing anywhere.
 *  [cookieFor] returns the user's own session cookie for a page URL (or null) — the engine can
 *  pass an age wall with the account the user actually signed in with. Never logged. */
class EngineResolver(private val cookieFor: (String) -> String? = { null }) : StreamResolver {
    override suspend fun resolve(ref: VideoRef): Resolved {
        val cookie = cookieFor(ref.pageUrl)
        val paired = if (cookie == null) emptyList() else listOf("--add-header" to "Cookie: $cookie")
        val out = runEngine(ref.pageUrl, "-J", "--no-playlist", "--no-warnings", paired = paired)
        return parseInfoJson(out, ref, System.currentTimeMillis())
    }
}

@Serializable
private data class EngineFormatJson(
    @SerialName("format_id") val formatId: String? = null,
    val ext: String? = null,
    val height: Int? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val protocol: String? = null,
    val url: String? = null,
    @SerialName("http_headers") val httpHeaders: Map<String, String>? = null,
    /** Set-Cookie-shaped ("k=v; Domain=..; Path=..; k2=v2; ..."): the session cookies the CDN
     *  demands for this URL (TikTok's tt_chain_token etc.). Not a request header as-is. */
    val cookies: String? = null,
    val filesize: Long? = null,
    @SerialName("filesize_approx") val filesizeApprox: Long? = null,
    val tbr: Double? = null,
)

@Serializable
private data class EngineInfoJson(
    val title: String? = null,
    val duration: Double? = null,
    val thumbnail: String? = null,
    val formats: List<EngineFormatJson>? = null,
)

/**
 * Pure JSON -> model mapping, the only unit-testable slice of this resolver (no Android, no
 * network). `vcodec`/`acodec` of literal "none" means absent by the engine's own convention and
 * must become null, or [MediaFormat.isVideoOnly]/[MediaFormat.isAudioOnly] silently invert.
 */
internal fun parseInfoJson(json: String, ref: VideoRef, nowMillis: Long): Resolved {
    val info = try {
        jsonCodec.decodeFromString(EngineInfoJson.serializer(), json)
    } catch (e: SerializationException) {
        throw ExtractionError.Unsupported("engine JSON did not match expected shape", e)
    }
    val rawFormats = info.formats
    if (rawFormats.isNullOrEmpty()) throw ExtractionError.Unsupported("engine returned no formats")

    val formats = rawFormats.map { f ->
        MediaFormat(
            formatId = f.formatId ?: "",
            url = f.url ?: "",
            container = f.ext ?: "",
            protocol = mapProtocol(f.protocol),
            height = f.height,
            // Both codecs ABSENT (not "none") = the engine never probed them (Facebook's sd/hd
            // progressive mp4 arrive like this). A muxed file is the only thing that shape can be;
            // null/null would read as neither video nor audio and get rejected as unplayable.
            videoCodec = f.vcodec.orNoneToNull() ?: if (f.vcodec == null && f.acodec == null) UNKNOWN_CODEC else null,
            audioCodec = f.acodec.orNoneToNull() ?: if (f.vcodec == null && f.acodec == null) UNKNOWN_CODEC else null,
            bitrate = f.tbr?.let { (it * 1000).roundToLong() }, // tbr is kbps; MediaFormat.bitrate is bps
            filesizeBytes = f.filesize ?: f.filesizeApprox,
            headers = withCookieHeader(f.httpHeaders ?: emptyMap(), f.cookies),
        )
    }
    // The page's own info JSON is fresher than whatever listing this ref came from -- carry it
    // forward onto the ref so a resolve-time title/thumbnail/duration change reaches the player.
    val resolvedRef = ref.copy(
        title = info.title ?: ref.title,
        thumbnailUrl = info.thumbnail ?: ref.thumbnailUrl,
        durationSeconds = info.duration?.roundToInt() ?: ref.durationSeconds,
    )
    return Resolved(ref = resolvedRef, formats = formats, resolvedAtMillis = nowMillis)
}

private fun mapProtocol(protocol: String?): Protocol = when {
    protocol == null -> Protocol.PROGRESSIVE
    protocol.startsWith("m3u8") -> Protocol.HLS
    protocol == "dash" || protocol.startsWith("http_dash") -> Protocol.DASH
    else -> Protocol.PROGRESSIVE
}

private fun String?.orNoneToNull(): String? = if (this == null || this == "none") null else this

internal const val UNKNOWN_CODEC = "unknown"

private val COOKIE_ATTRIBUTES = setOf("domain", "path", "expires", "max-age", "secure", "httponly", "samesite", "priority")

/** yt-dlp's per-format `cookies` is Set-Cookie text; a CDN wants the plain `Cookie: k=v; k2=v2`
 *  request header (verified live: TikTok answers 206 with it, 403 without -- and 403 with the
 *  raw attribute-laden string). An explicit Cookie in http_headers wins. Memory only, never logged. */
internal fun cookieHeaderFrom(setCookieStyle: String?): String? {
    if (setCookieStyle.isNullOrBlank()) return null
    val pairs = setCookieStyle.split(';').mapNotNull { part ->
        val p = part.trim()
        val eq = p.indexOf('=')
        if (eq <= 0) return@mapNotNull null
        val name = p.substring(0, eq).trim()
        if (name.lowercase() in COOKIE_ATTRIBUTES) null else "$name=${p.substring(eq + 1).trim()}"
    }
    return pairs.takeIf { it.isNotEmpty() }?.joinToString("; ")
}

internal fun withCookieHeader(headers: Map<String, String>, cookies: String?): Map<String, String> {
    if (headers.keys.any { it.equals("Cookie", ignoreCase = true) }) return headers
    val cookie = cookieHeaderFrom(cookies) ?: return headers
    return headers + ("Cookie" to cookie)
}
