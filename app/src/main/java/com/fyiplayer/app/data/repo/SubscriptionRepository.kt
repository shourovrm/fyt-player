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

class SubscriptionRepository(private val dao: SubscriptionDao) {
    /** Newest-subscribed first -- Home's channel feed order. */
    fun observeAll(): Flow<List<Listing>> = dao.observeAll().map { list -> list.map { it.toListing() } }

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
}
