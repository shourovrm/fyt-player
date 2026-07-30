package com.fyiplayer.app.data.repo

import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.db.DownloadDao
import com.fyiplayer.app.data.db.DownloadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DownloadState { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }

// downloads carry fields a VideoRef doesn't (format, file, progress), so the domain type wraps
// a ref instead of being one.
data class DownloadItem(
    val ref: VideoRef,
    val formatId: String,
    val filePath: String?,
    val state: DownloadState,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val updatedAt: Long,
)

fun DownloadEntity.toDownloadItem(): DownloadItem = DownloadItem(
    ref = VideoRef(sourceId = sourceId, pageUrl = pageUrl, remoteId = pageUrl, title = title),
    formatId = formatId,
    filePath = filePath,
    state = DownloadState.valueOf(state),
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    updatedAt = updatedAt,
)

fun DownloadItem.toEntity(): DownloadEntity = DownloadEntity(
    pageUrl = ref.pageUrl,
    sourceId = ref.sourceId,
    title = ref.title,
    formatId = formatId,
    filePath = filePath,
    state = state.name,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    updatedAt = updatedAt,
)

class DownloadRepository(private val dao: DownloadDao) {
    fun observe(): Flow<List<DownloadItem>> = dao.observeAll().map { list -> list.map { it.toDownloadItem() } }

    suspend fun get(pageUrl: String): DownloadItem? = dao.get(pageUrl)?.toDownloadItem()

    suspend fun upsert(item: DownloadItem) = dao.upsert(item.toEntity())

    suspend fun remove(pageUrl: String) = dao.delete(pageUrl)
}
