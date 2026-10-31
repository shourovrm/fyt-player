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
    isShort = isShort,
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
    isShort = isShort,
)

class HistoryRepository(private val dao: WatchHistoryDao) {
    fun observe(): Flow<List<VideoRef>> = dao.observeAll().map { list -> list.map { it.toVideoRef() } }

    suspend fun record(ref: VideoRef, watchedAt: Long = System.currentTimeMillis()) =
        dao.upsert(ref.toWatchHistoryEntity(watchedAt))

    suspend fun remove(pageUrl: String) = dao.delete(pageUrl)

    /** Deletes the rows and returns them whole (watchedAt included) so [restore] can undo exactly.
     *  Chunked: SQLite caps the number of bound variables in one IN (...). */
    suspend fun removeAll(pageUrls: Collection<String>): List<WatchHistoryEntity> {
        val removed = pageUrls.chunked(IN_CHUNK).flatMap { dao.getByUrls(it) }
        pageUrls.chunked(IN_CHUNK).forEach { dao.deleteByUrls(it) }
        return removed
    }

    suspend fun restore(entries: List<WatchHistoryEntity>) = dao.upsertAll(entries)

    suspend fun clear() = dao.clear()

    private companion object {
        const val IN_CHUNK = 500
    }
}
