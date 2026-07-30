package com.fyiplayer.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val pageUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions WHERE pageUrl = :pageUrl")
    suspend fun get(pageUrl: String): PlaybackPositionEntity?

    // bulk read for "resume" badges on a list -- one collect, not one query per visible row
    @Query("SELECT * FROM playback_positions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaybackPositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE pageUrl = :pageUrl")
    suspend fun delete(pageUrl: String)
}
