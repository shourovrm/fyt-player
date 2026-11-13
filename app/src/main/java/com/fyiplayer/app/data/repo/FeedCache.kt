package com.fyiplayer.app.data.repo

import android.util.Log
import com.fyiplayer.app.core.VideoRef
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val MAX_CACHED_ITEMS = 60

/**
 * The last "For you" feed, kept in one JSON file so the next app start shows it at once while the
 * network refresh runs. A failed read or write is logged and treated as "no cache": the feed
 * still loads from the network, so a broken cache file must not stop the app.
 */
class FeedCache(private val file: File) {
    suspend fun load(): List<VideoRef> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            decodeFeed(file.readText())
        } catch (e: SerializationException) {
            // Cut short by a kill mid-write, or written by another app version.
            Log.w("FeedCache", "unreadable feed cache dropped: ${e::class.simpleName}")
            file.delete()
            emptyList()
        } catch (e: IOException) {
            Log.w("FeedCache", "feed cache read failed: ${e::class.simpleName}")
            emptyList()
        }
    }

    suspend fun save(items: List<VideoRef>) = withContext(Dispatchers.IO) {
        try {
            file.writeText(encodeFeed(items.take(MAX_CACHED_ITEMS)))
        } catch (e: IOException) {
            Log.w("FeedCache", "feed cache write failed: ${e::class.simpleName}")
        }
    }
}

/**
 * What of a [VideoRef] goes to disk. Thumbnails are stored as the unsigned path only (project
 * rule: signed URLs stay in memory). View counts and "3 hours ago" texts are left out: they
 * would be stale when shown.
 */
@Serializable
private data class CachedFeedItem(
    val sourceId: String,
    val pageUrl: String,
    val remoteId: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val uploader: String?,
    val uploaderUrl: String?,
    val uploadEpochMs: Long?,
    val isLive: Boolean,
    val isUpcoming: Boolean,
)

internal fun encodeFeed(items: List<VideoRef>): String {
    val cached = items.map {
        CachedFeedItem(
            sourceId = it.sourceId,
            pageUrl = it.pageUrl,
            remoteId = it.remoteId,
            title = it.title,
            thumbnailUrl = canonicalThumbnailUrl(it.thumbnailUrl),
            durationSeconds = it.durationSeconds,
            uploader = it.uploader,
            uploaderUrl = it.uploaderUrl,
            uploadEpochMs = it.uploadEpochMs,
            isLive = it.isLive,
            isUpcoming = it.isUpcoming,
        )
    }
    return Json.encodeToString(cached)
}

internal fun decodeFeed(text: String): List<VideoRef> =
    Json.decodeFromString<List<CachedFeedItem>>(text).map {
        VideoRef(
            sourceId = it.sourceId,
            pageUrl = it.pageUrl,
            remoteId = it.remoteId,
            title = it.title,
            // A YouTube listing thumbnail often does not exist without its signed part (seen on
            // device: the card stayed grey), so YouTube pages use the address built from the id.
            thumbnailUrl = youtubeThumbnailFor(it.pageUrl) ?: it.thumbnailUrl,
            durationSeconds = it.durationSeconds,
            uploader = it.uploader,
            uploaderUrl = it.uploaderUrl,
            uploadEpochMs = it.uploadEpochMs,
            isLive = it.isLive,
            isUpcoming = it.isUpcoming,
        )
    }
