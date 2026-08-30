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
    // v6->v7, all additive/nullable (MIGRATION_6_7):
    // canonical (unsigned) thumbnail for the Downloads-screen row -- never the raw signed one.
    val thumbnailUrl: String? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    // Track language the user asked to also save as a caption file, re-matched at download time --
    // never the caption file's own signed URL.
    val subtitleLanguageCode: String? = null,
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
