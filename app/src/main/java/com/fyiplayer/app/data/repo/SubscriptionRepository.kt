package com.fyiplayer.app.data.repo

import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.data.db.SubscriptionDao
import com.fyiplayer.app.data.db.SubscriptionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Listing already has exactly the shape a subscribed channel needs (sourceId, key=channelUrl,
// title) -- no reason to invent a second domain type the UI would have to convert between.
fun SubscriptionEntity.toListing(): Listing = Listing(sourceId, Listing.Kind.CHANNEL, channelUrl, title)

/** Library's Channels tab row shape: a [Listing] to navigate/display plus the feed-visibility flag
 *  the eye toggle reads and flips. Feed builders never see this -- they only want [Listing]s that
 *  already passed the filter, see [SubscriptionRepository.observeFeedChannels]. */
data class SubscriptionRow(val listing: Listing, val showInFeed: Boolean)

private fun SubscriptionEntity.toRow(): SubscriptionRow = SubscriptionRow(toListing(), showInFeed)

class SubscriptionRepository(private val dao: SubscriptionDao) {
    /** Newest-subscribed first, every row -- what Library's Channels tab renders. */
    fun observeAllRows(): Flow<List<SubscriptionRow>> = dao.observeAll().map { list -> list.map { it.toRow() } }

    /** Newest-subscribed first, hidden channels excluded -- Home/Shorts feed order and input. */
    fun observeFeedChannels(): Flow<List<Listing>> = dao.observeFeedChannels().map { list -> list.map { it.toListing() } }

    fun isSubscribed(channelUrl: String): Flow<Boolean> = dao.isSubscribed(channelUrl)

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun unsubscribe(channelUrl: String) = dao.unsubscribe(channelUrl)

    /** What a subscribe button actually needs: flip current state in one call. */
    suspend fun toggle(channelUrl: String, sourceId: String, title: String) {
        if (dao.isSubscribed(channelUrl).first()) {
            dao.unsubscribe(channelUrl)
        } else {
            dao.subscribe(SubscriptionEntity(channelUrl, sourceId, title, System.currentTimeMillis()))
        }
    }

    /** The Library eye toggle -- channel stays subscribed, just opts out of Home/Shorts. */
    suspend fun setShowInFeed(channelUrl: String, show: Boolean) = dao.setShowInFeed(channelUrl, show)
}
