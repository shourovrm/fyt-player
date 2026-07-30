package com.fyiplayer.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 -> v2: Home's feed needs to know which channel a watched video belongs to. Additive column,
 *  no data loss -- never fall back to destructive migration, that wipes a user's history. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE watch_history ADD COLUMN uploaderUrl TEXT")
    }
}

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
    version = 2,
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
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
