package com.fyiplayer.app.source.youtube

import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.SeekThumbnails
import com.fyiplayer.app.core.SpriteSheet
import com.fyiplayer.app.core.VideoDetail
import com.fyiplayer.app.core.VideoRef
import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

private val jsonCodec = Json { ignoreUnknownKeys = true }

/** The one persisted identity for any YouTube video, however it was reached (watch/shorts/short URL). */
internal fun canonicalWatchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

@Serializable
private data class FlatEntryJson(
    val id: String? = null,
    val title: String? = null,
    val duration: Double? = null,
    val channel: String? = null,
    val uploader: String? = null,
    @SerialName("channel_url") val channelUrl: String? = null,
    @SerialName("uploader_url") val uploaderUrl: String? = null,
    val thumbnails: List<ThumbnailJson>? = null,
    val thumbnail: String? = null,
)

@Serializable
private data class ThumbnailJson(val url: String? = null, val width: Int? = null)

@Serializable
private data class FlatPlaylistRootJson(val entries: List<JsonElement>? = null)

/**
 * A `--flat-playlist -J` result (search, homepage, listing, shorts all share this shape) ->
 * [VideoRef]s. Pure: no Android, no network. A malformed entry (wrong shape, or missing the id a
 * canonical URL needs) is skipped rather than aborting the whole page.
 */
internal fun parseFlatPlaylistJson(json: String): List<VideoRef> {
    val root = try {
        jsonCodec.decodeFromString(FlatPlaylistRootJson.serializer(), json)
    } catch (e: SerializationException) {
        return emptyList()
    }
    return root.entries.orEmpty().mapNotNull { el ->
        val entry = runCatching { jsonCodec.decodeFromJsonElement(FlatEntryJson.serializer(), el) }
            .getOrNull() ?: return@mapNotNull null
        val id = entry.id ?: return@mapNotNull null
        VideoRef(
            sourceId = "youtube",
            pageUrl = canonicalWatchUrl(id),
            remoteId = id,
            title = entry.title ?: "",
            thumbnailUrl = entry.thumbnails?.maxByOrNull { it.width ?: 0 }?.url ?: entry.thumbnail,
            durationSeconds = entry.duration?.roundToInt(),
            uploader = entry.channel ?: entry.uploader,
            uploaderUrl = entry.channelUrl ?: entry.uploaderUrl,
        )
    }
}

@Serializable
private data class DetailJson(
    val title: String? = null,
    val description: String? = null,
    @SerialName("upload_date") val uploadDate: String? = null,
    @SerialName("view_count") val viewCount: Long? = null,
    @SerialName("like_count") val likeCount: Long? = null,
    val duration: Double? = null,
    val thumbnail: String? = null,
    val channel: String? = null,
    val uploader: String? = null,
    @SerialName("channel_url") val channelUrl: String? = null,
    @SerialName("uploader_url") val uploaderUrl: String? = null,
    @SerialName("channel_id") val channelId: String? = null,
)

/**
 * Full `-J --no-playlist` info JSON -> [VideoDetail]. The engine does not expose related videos
 * for this platform, so [VideoDetail.related] is always empty here -- an honest gap, not invented.
 */
internal fun parseDetailJson(json: String, ref: VideoRef): VideoDetail {
    val info = try {
        jsonCodec.decodeFromString(DetailJson.serializer(), json)
    } catch (e: SerializationException) {
        return VideoDetail(ref = ref)
    }
    val channelName = info.channel ?: info.uploader
    val channelKey = info.channelUrl ?: info.uploaderUrl
        ?: info.channelId?.let { "https://www.youtube.com/channel/$it" }
    val uploaderListing = if (channelKey != null) {
        Listing(sourceId = "youtube", kind = Listing.Kind.CHANNEL, key = channelKey, title = channelName ?: "")
    } else {
        null
    }
    val resolvedRef = ref.copy(
        title = info.title ?: ref.title,
        thumbnailUrl = info.thumbnail ?: ref.thumbnailUrl,
        durationSeconds = info.duration?.roundToInt() ?: ref.durationSeconds,
        uploader = channelName ?: ref.uploader,
        uploaderUrl = channelKey ?: ref.uploaderUrl,
    )
    return VideoDetail(
        ref = resolvedRef,
        related = emptyList(),
        uploader = uploaderListing,
        description = info.description,
        uploadDate = info.uploadDate,
        likeCount = info.likeCount,
        viewCount = info.viewCount,
    )
}

@Serializable
private data class StoryboardsRootJson(val formats: List<StoryboardFormatJson>? = null)

@Serializable
private data class StoryboardFormatJson(
    @SerialName("format_id") val formatId: String? = null,
    val fragments: List<FragmentJson>? = null,
    val columns: Int? = null,
    val rows: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
private data class FragmentJson(val url: String? = null)

/**
 * Storyboard formats (`format_id` "sb0", "sb1", ...) -> [SeekThumbnails.sprites]. Picks the format
 * with the most total tiles (finest scrub granularity) -- the engine lists several resolutions and
 * marks none of them "preferred".
 *
 * ponytail: the engine publishes no per-tile interval, only fragment/grid geometry. Approximated
 * as durationSeconds / totalTiles, falling back to a flat 10s guess when duration is unknown. A
 * real device run against a long and a short video would show how far off that guess runs.
 */
internal fun parseStoryboardsJson(json: String, durationSeconds: Int?): SeekThumbnails? {
    val root = try {
        jsonCodec.decodeFromString(StoryboardsRootJson.serializer(), json)
    } catch (e: SerializationException) {
        return null
    }
    val best = root.formats.orEmpty()
        .filter { it.formatId?.startsWith("sb") == true }
        .maxByOrNull { (it.fragments?.size ?: 0) * (it.columns ?: 0) * (it.rows ?: 0) }
        ?: return null
    val cols = best.columns ?: return null
    val rows = best.rows ?: return null
    val tileWidth = best.width ?: return null
    val tileHeight = best.height ?: return null
    val fragmentUrls = best.fragments.orEmpty().mapNotNull { it.url }
    if (fragmentUrls.isEmpty()) return null

    val tilesPerSheet = cols * rows
    val totalTiles = tilesPerSheet * fragmentUrls.size
    val interval = if (durationSeconds != null && totalTiles > 0) {
        durationSeconds.toDouble() / totalTiles
    } else {
        10.0
    }
    val sprites = fragmentUrls.map { url ->
        SpriteSheet(url = url, cols = cols, rows = rows, tileWidth = tileWidth, tileHeight = tileHeight, count = tilesPerSheet)
    }
    return SeekThumbnails(intervalSeconds = interval, sprites = sprites)
}
