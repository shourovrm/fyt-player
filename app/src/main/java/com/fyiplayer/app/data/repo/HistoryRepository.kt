package com.fyiplayer.app.data.repo

import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.db.WatchHistoryDao
import com.fyiplayer.app.data.db.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// remoteId/viewCountText aren't columns here -- they're not needed to re-resolve a page, and
// remoteId is reconstructed as pageUrl so a caller always has *a* stable id back. uploaderUrl IS
// a column: Home's feed needs it to know which channels a user actually watches.
fun WatchHistoryEntity.toVideoRef(): VideoRef = VideoRef(
    sourceId = sourceId,
    pageUrl = pageUrl,
    remoteId = pageUrl,
    title = title,
    thumbnailUrl = thumbnailUrl ?: youtubeThumbnailFor(pageUrl),
    durationSeconds = durationSeconds,
    uploader = uploader,
    uploaderUrl = uploaderUrl,
)

fun VideoRef.toWatchHistoryEntity(watchedAt: Long): WatchHistoryEntity = WatchHistoryEntity(
    pageUrl = pageUrl,
    sourceId = sourceId,
    title = title,
    uploader = uploader,
    durationSeconds = durationSeconds,
    thumbnailUrl = canonicalThumbnailUrl(thumbnailUrl),
    watchedAt = watchedAt,
    uploaderUrl = uploaderUrl,
)

class HistoryRepository(private val dao: WatchHistoryDao) {
    fun observe(): Flow<List<VideoRef>> = dao.observeAll().map { list -> list.map { it.toVideoRef() } }

    suspend fun record(ref: VideoRef, watchedAt: Long = System.currentTimeMillis()) =
        dao.upsert(ref.toWatchHistoryEntity(watchedAt))

    suspend fun remove(pageUrl: String) = dao.delete(pageUrl)

    suspend fun clear() = dao.clear()
}
