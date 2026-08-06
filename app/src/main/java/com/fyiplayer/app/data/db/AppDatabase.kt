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

/** v2 -> v3: Home moves from watch-history-derived channels to explicit subscriptions. New table,
 *  additive -- never fall back to destructive migration, that wipes a user's library. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS subscriptions (
                channelUrl TEXT NOT NULL PRIMARY KEY,
                sourceId TEXT NOT NULL,
                title TEXT NOT NULL,
                subscribedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

/** v3 -> v4: follow a remote playlist without adding its videos to a local one. New table,
 *  additive -- never fall back to destructive migration, that wipes a user's library. */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS followed_playlists (
                pageUrl TEXT NOT NULL PRIMARY KEY,
                sourceId TEXT NOT NULL,
                title TEXT NOT NULL,
                addedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
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
        SubscriptionEntity::class,
        FollowedPlaylistEntity::class,
    ],
    version = 4,
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
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun followedPlaylistDao(): FollowedPlaylistDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fyi-player.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
