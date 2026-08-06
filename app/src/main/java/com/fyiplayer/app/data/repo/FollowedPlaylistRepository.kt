package com.fyiplayer.app.data.repo

import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.data.db.FollowedPlaylistDao
import com.fyiplayer.app.data.db.FollowedPlaylistEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Same shape as SubscriptionRepository.toListing(): Listing already carries everything a followed
// row needs (sourceId, key=pageUrl, title), no second domain type to convert between.
fun FollowedPlaylistEntity.toListing(): Listing = Listing(sourceId, Listing.Kind.PLAYLIST, pageUrl, title)

class FollowedPlaylistRepository(private val dao: FollowedPlaylistDao) {
    fun observeAll(): Flow<List<Listing>> = dao.observeAll().map { list -> list.map { it.toListing() } }

    /** [Listing.key] is the canonical playlist page URL for a PLAYLIST-kind listing (see [Listing]
     *  doc); the PK on that column is what makes a repeat follow a no-op. */
    suspend fun follow(listing: Listing, addedAt: Long = System.currentTimeMillis()) =
        dao.follow(FollowedPlaylistEntity(listing.key, listing.sourceId, listing.title, addedAt))

    suspend fun unfollow(pageUrl: String) = dao.unfollow(pageUrl)
}
