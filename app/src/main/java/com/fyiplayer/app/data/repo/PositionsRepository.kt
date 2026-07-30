package com.fyiplayer.app.data.repo

import com.fyiplayer.app.data.db.PlaybackPositionDao
import com.fyiplayer.app.data.db.PlaybackPositionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PlaybackPosition(val positionMs: Long, val durationMs: Long, val updatedAt: Long)

class PositionsRepository(private val dao: PlaybackPositionDao) {
    suspend fun get(pageUrl: String): PlaybackPosition? =
        dao.get(pageUrl)?.let { PlaybackPosition(it.positionMs, it.durationMs, it.updatedAt) }

    // one collect for a whole list screen's "resume" badges -- never a query per visible row
    fun observeAll(): Flow<Map<String, PlaybackPosition>> = dao.observeAll().map { list ->
        list.associate { it.pageUrl to PlaybackPosition(it.positionMs, it.durationMs, it.updatedAt) }
    }

    suspend fun save(pageUrl: String, positionMs: Long, durationMs: Long, updatedAt: Long = System.currentTimeMillis()) =
        dao.upsert(PlaybackPositionEntity(pageUrl, positionMs, durationMs, updatedAt))

    suspend fun clear(pageUrl: String) = dao.delete(pageUrl)
}
