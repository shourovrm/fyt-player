package com.fyiplayer.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** [channelUrl] is the canonical channel page URL -- the same value used as [com.fyiplayer.app.core.Listing.key],
 *  so a subscription round-trips straight into a listing() call with no extra lookup. */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelUrl: String,
    val sourceId: String,
    val title: String,
    val subscribedAt: Long,
    /** Per-channel Home/Shorts feed opt-out (Library eye toggle) -- subscribed still, just not
     *  contributing to the feeds. Defaults true so every pre-existing row keeps feeding. */
    val showInFeed: Boolean = true,
)

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY subscribedAt DESC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    /** What Home/Shorts feed builders read -- hidden channels never enter the fan-out. */
    @Query("SELECT * FROM subscriptions WHERE showInFeed = 1 ORDER BY subscribedAt DESC")
    fun observeFeedChannels(): Flow<List<SubscriptionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelUrl = :channelUrl)")
    fun isSubscribed(channelUrl: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM subscriptions")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun subscribe(entity: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channelUrl = :channelUrl")
    suspend fun unsubscribe(channelUrl: String)

    @Query("UPDATE subscriptions SET showInFeed = :show WHERE channelUrl = :channelUrl")
    suspend fun setShowInFeed(channelUrl: String, show: Boolean)
}
