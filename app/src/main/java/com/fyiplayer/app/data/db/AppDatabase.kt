package com.fyiplayer.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchHistoryEntity::class,
        PlaybackPositionEntity::class,
        SearchHistoryEntity::class,
        LikeEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        CookieEntity::class,
        DownloadEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun likeDao(): LikeDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistItemDao(): PlaylistItemDao
    abstract fun cookieDao(): CookieDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fyi-player.db")
                .build()
                .also { instance = it }
        }
    }
}
