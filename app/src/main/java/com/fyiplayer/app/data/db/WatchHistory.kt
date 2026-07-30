package com.fyiplayer.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val pageUrl: String,
    val sourceId: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val watchedAt: Long,
    /** Uploader's canonical channel URL -- lets Home rebuild "channels you watch" without guessing. */
    val uploaderUrl: String? = null,
)

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun observeAll(): Flow<List<WatchHistoryEntity>>

    // re-watching the same page just bumps watchedAt, one row per page
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE pageUrl = :pageUrl")
    suspend fun delete(pageUrl: String)

    @Query("DELETE FROM watch_history")
    suspend fun clear()
}
