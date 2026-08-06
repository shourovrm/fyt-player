package com.fyiplayer.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** [pageUrl] is the canonical playlist page URL -- same value as [com.fyiplayer.app.core.Listing.key],
 *  so a followed row round-trips straight into a listing() call with no extra lookup. Same shape
 *  as [SubscriptionEntity], one table down. */
@Entity(tableName = "followed_playlists")
data class FollowedPlaylistEntity(
    @PrimaryKey val pageUrl: String,
    val sourceId: String,
    val title: String,
    val addedAt: Long,
)

@Dao
interface FollowedPlaylistDao {
    @Query("SELECT * FROM followed_playlists ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FollowedPlaylistEntity>>

    // IGNORE, not REPLACE: pageUrl is the primary key, so a repeat follow is a no-op instead of
    // bumping addedAt and reshuffling the list order.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun follow(entity: FollowedPlaylistEntity)

    @Query("DELETE FROM followed_playlists WHERE pageUrl = :pageUrl")
    suspend fun unfollow(pageUrl: String)
}
