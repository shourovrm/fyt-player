package com.fyiplayer.app.data.repo

import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.db.PlaylistDao
import com.fyiplayer.app.data.db.PlaylistEntity
import com.fyiplayer.app.data.db.PlaylistItemDao
import com.fyiplayer.app.data.db.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Playlist(val id: Long, val name: String, val createdAt: Long)

fun PlaylistEntity.toPlaylist(): Playlist = Playlist(id, name, createdAt)

fun PlaylistItemEntity.toVideoRef(): VideoRef = VideoRef(
    sourceId = sourceId,
    pageUrl = pageUrl,
    remoteId = pageUrl,
    title = title,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    uploader = uploader,
)

class PlaylistRepository(private val playlists: PlaylistDao, private val items: PlaylistItemDao) {
    fun observePlaylists(): Flow<List<Playlist>> = playlists.observeAll().map { list -> list.map { it.toPlaylist() } }

    fun observeItems(playlistId: Long): Flow<List<VideoRef>> =
        items.observeItems(playlistId).map { list -> list.map { it.toVideoRef() } }

    suspend fun create(name: String, createdAt: Long = System.currentTimeMillis()): Long =
        playlists.insert(PlaylistEntity(name = name, createdAt = createdAt))

    suspend fun rename(id: Long, name: String) = playlists.rename(id, name)

    suspend fun delete(id: Long) = playlists.delete(id) // FK cascade drops its items

    suspend fun addItem(playlistId: Long, ref: VideoRef, addedAt: Long = System.currentTimeMillis()) {
        val sortIndex = items.maxSortIndex(playlistId) + 1
        items.upsert(
            PlaylistItemEntity(
                playlistId = playlistId,
                pageUrl = ref.pageUrl,
                sourceId = ref.sourceId,
                title = ref.title,
                uploader = ref.uploader,
                durationSeconds = ref.durationSeconds,
                thumbnailUrl = ref.thumbnailUrl,
                sortIndex = sortIndex,
                addedAt = addedAt,
            ),
        )
    }

    suspend fun removeItem(playlistId: Long, pageUrl: String) = items.delete(playlistId, pageUrl)
}
