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

/** v4 -> v5: per-channel Home/Shorts feed opt-out (Library eye toggle). Additive column,
 *  default keeps every existing subscription feeding -- never fall back to destructive migration,
 *  that wipes a user's library. */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN showInFeed INTEGER NOT NULL DEFAULT 1")
    }
}

/** v5 -> v6: a playlist row lacked the uploader's channel URL, so Detail could only ever show
 *  the uploader as plain text for a video opened from a playlist -- never a tappable link, until
 *  its own live re-fetch happened to land. Additive column, mirrors `MIGRATION_1_2`. */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlist_items ADD COLUMN uploaderUrl TEXT")
    }
}

/** v6 -> v7: download rows gain a thumbnail (Downloads-screen row image), start/finish
 *  timestamps (duration-taken display), and an optional subtitle-track language to fetch
 *  alongside the video. All additive/nullable columns -- never fall back to destructive
 *  migration, that wipes a user's download queue. */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN thumbnailUrl TEXT")
        db.execSQL("ALTER TABLE downloads ADD COLUMN startedAt INTEGER")
        db.execSQL("ALTER TABLE downloads ADD COLUMN finishedAt INTEGER")
        db.execSQL("ALTER TABLE downloads ADD COLUMN subtitleLanguageCode TEXT")
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
    version = 7,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                .also { instance = it }
        }
    }
}
