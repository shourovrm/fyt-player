package com.fyiplayer.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "pageUrl"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // SQLite does not auto-index FK columns; without this every cascade delete does a table scan
    indices = [Index(value = ["playlistId"])],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val pageUrl: String,
    val sourceId: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val sortIndex: Int,
    val addedAt: Long,
)

@Dao
interface PlaylistItemDao {
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY sortIndex ASC")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND pageUrl = :pageUrl")
    suspend fun delete(playlistId: Long, pageUrl: String)

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxSortIndex(playlistId: Long): Int

    /** Reorder without touching the row's other columns — a delete-and-re-add would rewrite
     *  `addedAt` for the whole list and cost two writes per item. */
    @Query("UPDATE playlist_items SET sortIndex = :sortIndex WHERE playlistId = :playlistId AND pageUrl = :pageUrl")
    suspend fun setSortIndex(playlistId: Long, pageUrl: String, sortIndex: Int)
}
