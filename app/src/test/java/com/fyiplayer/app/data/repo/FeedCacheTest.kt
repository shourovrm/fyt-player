package com.fyiplayer.app.data.repo

import com.fyiplayer.app.core.VideoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedCacheTest {
    private val listed = VideoRef(
        sourceId = "youtube",
        pageUrl = "https://www.youtube.com/watch?v=abcdefghijk",
        remoteId = "abcdefghijk",
        title = "A title",
        thumbnailUrl = "https://i.ytimg.com/vi/abcdefghijk/hq720.jpg?sqp=signed&rs=token",
        durationSeconds = 336,
        uploader = "Channel",
        uploaderUrl = "https://www.youtube.com/channel/UC1",
        viewCountText = "1.8M views",
        uploadedText = "21 hours ago",
        uploadEpochMs = 1_700_000_000_000L,
    )

    @Test fun `a saved feed reads back with what a card needs`() {
        val read = decodeFeed(encodeFeed(listOf(listed))).single()
        assertEquals(listed.pageUrl, read.pageUrl)
        assertEquals(listed.title, read.title)
        assertEquals(listed.uploader, read.uploader)
        assertEquals(listed.durationSeconds, read.durationSeconds)
        assertEquals(listed.uploadEpochMs, read.uploadEpochMs)
    }

    @Test fun `the signed part of a thumbnail address is never written`() {
        val text = encodeFeed(listOf(listed))
        assertEquals(false, text.contains("sqp"))
    }

    @Test fun `a YouTube thumbnail is rebuilt from the video id`() {
        val read = decodeFeed(encodeFeed(listOf(listed))).single()
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", read.thumbnailUrl)
    }

    @Test fun `another site keeps its unsigned thumbnail path`() {
        val other = listed.copy(sourceId = "vimeo", pageUrl = "https://vimeo.com/1", thumbnailUrl = "https://i.vimeocdn.com/a.jpg?sig=x")
        assertEquals("https://i.vimeocdn.com/a.jpg", decodeFeed(encodeFeed(listOf(other))).single().thumbnailUrl)
    }

    @Test fun `texts that go stale are not kept`() {
        val read = decodeFeed(encodeFeed(listOf(listed))).single()
        assertNull(read.viewCountText)
        assertNull(read.uploadedText)
    }
}
