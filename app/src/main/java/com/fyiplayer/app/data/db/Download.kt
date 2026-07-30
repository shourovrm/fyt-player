package com.fyiplayer.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val pageUrl: String,
    val sourceId: String,
    val title: String,
    val formatId: String,
    val filePath: String?,
    // DownloadState.name -- one enum, not worth a TypeConverter
    val state: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val updatedAt: Long,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE pageUrl = :pageUrl")
    suspend fun get(pageUrl: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE pageUrl = :pageUrl")
    suspend fun delete(pageUrl: String)
}
