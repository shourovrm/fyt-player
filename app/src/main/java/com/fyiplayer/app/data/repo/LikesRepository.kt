package com.fyiplayer.app.data.repo

import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.db.LikeDao
import com.fyiplayer.app.data.db.LikeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun LikeEntity.toVideoRef(): VideoRef = VideoRef(
    sourceId = sourceId,
    pageUrl = pageUrl,
    remoteId = pageUrl, // not persisted, see HistoryRepository
    title = title,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    uploader = uploader,
)

fun VideoRef.toLikeEntity(likedAt: Long): LikeEntity = LikeEntity(
    pageUrl = pageUrl,
    sourceId = sourceId,
    title = title,
    uploader = uploader,
    durationSeconds = durationSeconds,
    thumbnailUrl = canonicalThumbnailUrl(thumbnailUrl),
    likedAt = likedAt,
)

class LikesRepository(private val dao: LikeDao) {
    fun observe(): Flow<List<VideoRef>> = dao.observeAll().map { list -> list.map { it.toVideoRef() } }

    fun observeIsLiked(pageUrl: String): Flow<Boolean> = dao.observeIsLiked(pageUrl)

    suspend fun like(ref: VideoRef, likedAt: Long = System.currentTimeMillis()) =
        dao.upsert(ref.toLikeEntity(likedAt))

    suspend fun unlike(pageUrl: String) = dao.delete(pageUrl)
}
