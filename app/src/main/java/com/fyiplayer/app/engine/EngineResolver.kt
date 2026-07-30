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

/** Tier 1 of the resolver chain: the engine's own info JSON, no markup parsing anywhere. */
class EngineResolver : StreamResolver {
    override suspend fun resolve(ref: VideoRef): Resolved {
        val out = runEngine(ref.pageUrl, "-J", "--no-playlist", "--no-warnings")
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
            videoCodec = f.vcodec.orNoneToNull(),
            audioCodec = f.acodec.orNoneToNull(),
            bitrate = f.tbr?.let { (it * 1000).roundToLong() }, // tbr is kbps; MediaFormat.bitrate is bps
            filesizeBytes = f.filesize ?: f.filesizeApprox,
            headers = f.httpHeaders ?: emptyMap(),
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
