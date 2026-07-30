package com.fyiplayer.app.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * `--progress-template` for the engine's "download" hook: one compact JSON object per stdout
 * line. `#j` is the engine's json-safe compact encoding -- numbers stay numbers, an unknown value
 * becomes `null`. Deliberately carries no URL or filename, only byte counts (nothing here may ever
 * echo a signed media URL into a log).
 */
internal const val PROGRESS_TEMPLATE =
    "download:{\"downloaded\":%(progress.downloaded_bytes)#j," +
        "\"total\":%(progress.total_bytes,progress.total_bytes_estimate)#j," +
        "\"eta\":%(progress.eta)#j,\"speed\":%(progress.speed)#j}"

private val progressJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class ProgressLineJson(
    val downloaded: Long? = null,
    val total: Long? = null,
    val eta: Long? = null,
    val speed: Double? = null,
)

data class DownloadProgress(
    val percent: Float?,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val etaSeconds: Long?,
    val speedBytesPerSecond: Double?,
)

/**
 * Parses one raw stdout line from the engine callback. Most lines are ordinary chatter, not the
 * template above -- returns null for anything that isn't a JSON object on its own line, and never
 * throws on garbage input. This is the only place progress text is interpreted.
 */
internal fun parseProgressLine(line: String): DownloadProgress? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
    val parsed = try {
        progressJson.decodeFromString(ProgressLineJson.serializer(), trimmed)
    } catch (e: SerializationException) {
        return null
    } catch (e: IllegalArgumentException) {
        return null
    }
    val downloaded = parsed.downloaded ?: return null
    val total = parsed.total?.takeIf { it > 0 }
    val percent = total?.let { downloaded * 100f / it }
    return DownloadProgress(percent, downloaded, total, parsed.eta, parsed.speed)
}
