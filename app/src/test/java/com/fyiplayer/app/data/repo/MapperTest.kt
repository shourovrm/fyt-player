package com.fyiplayer.app.data.repo

import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.db.SubscriptionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pure entity<->VideoRef mapping, no Room/Android involved -- runs under testDebugUnitTest.
class MapperTest {
    private val ref = VideoRef(
        sourceId = "youtube",
        pageUrl = "https://example.com/watch?v=abc123",
        remoteId = "abc123",
        title = "Title",
        thumbnailUrl = "https://example.com/thumb.jpg",
        durationSeconds = 120,
        uploader = "Uploader",
    )

    @Test
    fun watchHistoryRoundTripPreservesDisplayFields() {
        val entity = ref.toWatchHistoryEntity(watchedAt = 42L)
        val back = entity.toVideoRef()

        assertEquals(ref.sourceId, back.sourceId)
        assertEquals(ref.pageUrl, back.pageUrl)
        assertEquals(ref.title, back.title)
        assertEquals(ref.thumbnailUrl, back.thumbnailUrl)
        assertEquals(ref.durationSeconds, back.durationSeconds)
        assertEquals(ref.uploader, back.uploader)
        assertEquals(42L, entity.watchedAt)
        // remoteId isn't a column -- reconstructed as pageUrl, never the original remote id
        assertEquals(ref.pageUrl, back.remoteId)
    }

    @Test
    fun likeRoundTripPreservesDisplayFields() {
        val entity = ref.toLikeEntity(likedAt = 7L)
        val back = entity.toVideoRef()

        assertEquals(ref.sourceId, back.sourceId)
        assertEquals(ref.pageUrl, back.pageUrl)
        assertEquals(ref.title, back.title)
        assertEquals(ref.thumbnailUrl, back.thumbnailUrl)
        assertEquals(ref.durationSeconds, back.durationSeconds)
        assertEquals(ref.uploader, back.uploader)
        assertEquals(7L, entity.likedAt)
    }

    @Test
    fun playlistItemRoundTripPreservesDisplayFields() {
        val entity = ref.toWatchHistoryEntity(watchedAt = 1L) // reuse to build a shared ref
        val playlistItemRef = com.fyiplayer.app.data.db.PlaylistItemEntity(
            playlistId = 1L,
            pageUrl = entity.pageUrl,
            sourceId = entity.sourceId,
            title = entity.title,
            uploader = entity.uploader,
            durationSeconds = entity.durationSeconds,
            thumbnailUrl = entity.thumbnailUrl,
            sortIndex = 0,
            addedAt = 5L,
        ).toVideoRef()

        assertEquals(ref.sourceId, playlistItemRef.sourceId)
        assertEquals(ref.pageUrl, playlistItemRef.pageUrl)
        assertEquals(ref.title, playlistItemRef.title)
        assertEquals(ref.uploader, playlistItemRef.uploader)
    }

    @Test
    fun canonicalThumbnailUrlStripsSignatureQuery() {
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            canonicalThumbnailUrl("https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=-sqp&rs=rs-sig"),
        )
    }

    @Test
    fun canonicalThumbnailUrlLeavesBareUrlUnchanged() {
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            canonicalThumbnailUrl("https://i.ytimg.com/vi/abc/hqdefault.jpg"),
        )
    }

    @Test
    fun canonicalThumbnailUrlNullIsNull() {
        assertNull(canonicalThumbnailUrl(null))
    }

    @Test
    fun watchHistoryEntityStripsThumbnailSignature() {
        val signed = ref.copy(thumbnailUrl = "https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=-sqp&rs=rs-sig")
        val entity = signed.toWatchHistoryEntity(watchedAt = 1L)

        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", entity.thumbnailUrl)
        // in-memory ref keeps the signed URL
        assertEquals(signed.thumbnailUrl, signed.thumbnailUrl)
    }

    @Test
    fun likeEntityStripsThumbnailSignature() {
        val signed = ref.copy(thumbnailUrl = "https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=-sqp&rs=rs-sig")
        val entity = signed.toLikeEntity(likedAt = 1L)

        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", entity.thumbnailUrl)
    }

    @Test
    fun playlistItemEntityStripsThumbnailSignature() {
        val signed = ref.copy(thumbnailUrl = "https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=-sqp&rs=rs-sig")
        val entity = signed.toPlaylistItemEntity(playlistId = 1L, sortIndex = 0, addedAt = 1L)

        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", entity.thumbnailUrl)
    }

    @Test
    fun downloadItemRoundTripPreservesStateAndRef() {
        val item = DownloadItem(
            ref = VideoRef(sourceId = "youtube", pageUrl = ref.pageUrl, remoteId = ref.pageUrl, title = ref.title),
            formatId = "137",
            filePath = "/downloads/x.mp4",
            state = DownloadState.RUNNING,
            bytesDownloaded = 100L,
            totalBytes = 1000L,
            updatedAt = 9L,
        )
        val entity = item.toEntity()
        val back = entity.toDownloadItem()

        assertEquals(item.ref.pageUrl, back.ref.pageUrl)
        assertEquals(item.formatId, back.formatId)
        assertEquals(item.filePath, back.filePath)
        assertEquals(item.state, back.state)
        assertEquals(item.bytesDownloaded, back.bytesDownloaded)
        assertEquals(item.totalBytes, back.totalBytes)
    }

    @Test
    fun subscriptionEntityMapsToAChannelListing() {
        val entity = SubscriptionEntity(
            channelUrl = "https://example.com/@channel",
            sourceId = "youtube",
            title = "Channel Name",
            subscribedAt = 99L,
        )
        val listing = entity.toListing()

        assertEquals(entity.sourceId, listing.sourceId)
        assertEquals(Listing.Kind.CHANNEL, listing.kind)
        // channelUrl is Listing.key -- the exact value a source.listing() call needs back
        assertEquals(entity.channelUrl, listing.key)
        assertEquals(entity.title, listing.title)
    }
}
