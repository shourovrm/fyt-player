package com.fyiplayer.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "likes")
data class LikeEntity(
    @PrimaryKey val pageUrl: String,
    val sourceId: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val likedAt: Long,
)

@Dao
interface LikeDao {
    @Query("SELECT * FROM likes ORDER BY likedAt DESC")
    fun observeAll(): Flow<List<LikeEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM likes WHERE pageUrl = :pageUrl)")
    fun observeIsLiked(pageUrl: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(like: LikeEntity)

    @Query("DELETE FROM likes WHERE pageUrl = :pageUrl")
    suspend fun delete(pageUrl: String)
}
