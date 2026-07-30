package com.fyiplayer.app.data.repo

import com.fyiplayer.app.data.db.SearchHistoryDao
import com.fyiplayer.app.data.db.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SearchHistoryEntry(val query: String, val sourceId: String?, val searchedAt: Long)

class SearchHistoryRepository(private val dao: SearchHistoryDao) {
    fun observe(): Flow<List<SearchHistoryEntry>> = dao.observeAll().map { list ->
        list.map { SearchHistoryEntry(it.query, it.sourceId, it.searchedAt) }
    }

    suspend fun record(query: String, sourceId: String?, searchedAt: Long = System.currentTimeMillis()) =
        dao.upsert(SearchHistoryEntity(query, sourceId, searchedAt))

    suspend fun remove(query: String) = dao.delete(query)

    suspend fun clear() = dao.clear()
}
