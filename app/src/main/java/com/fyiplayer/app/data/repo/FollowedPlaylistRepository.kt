package com.fyiplayer.app.data.repo

import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.data.db.FollowedPlaylistDao
import com.fyiplayer.app.data.db.FollowedPlaylistEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Same shape as SubscriptionRepository.toListing(): Listing already carries everything a followed
// row needs (sourceId, key=pageUrl, title, thumbnailUrl), no second domain type to convert between.
fun FollowedPlaylistEntity.toListing(): Listing = Listing(sourceId, Listing.Kind.PLAYLIST, pageUrl, title, thumbnailUrl)

class FollowedPlaylistRepository(private val dao: FollowedPlaylistDao) {
    fun observeAll(): Flow<List<Listing>> = dao.observeAll().map { list -> list.map { it.toListing() } }

    /** [Listing.key] is the canonical playlist page URL for a PLAYLIST-kind listing (see [Listing]
     *  doc); the PK on that column is what makes a repeat follow a no-op. [thumbnailUrl] is the
     *  first video's thumbnail from the listing page already in hand at follow time -- canonicalized
     *  before it's stored (rule: no signed/tokenised URL ever hits the database). */
    suspend fun follow(listing: Listing, addedAt: Long = System.currentTimeMillis(), thumbnailUrl: String? = null) =
        dao.follow(FollowedPlaylistEntity(listing.key, listing.sourceId, listing.title, addedAt, canonicalThumbnailUrl(thumbnailUrl)))

    suspend fun unfollow(pageUrl: String) = dao.unfollow(pageUrl)

    /** Backfill for a row followed before this column existed -- see [com.fyiplayer.app.ui.LibraryViewModel]. */
    suspend fun updateThumbnail(pageUrl: String, thumbnailUrl: String?) = dao.updateThumbnail(pageUrl, canonicalThumbnailUrl(thumbnailUrl))
    suspend fun fillTitle(pageUrl: String, title: String) = dao.fillTitle(pageUrl, title)
}
