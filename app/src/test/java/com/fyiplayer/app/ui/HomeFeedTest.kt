package com.fyiplayer.app.ui

import com.fyiplayer.app.core.VideoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-function coverage for Home's watched-channels feed -- no Android, no network. */
class HomeFeedTest {

    private fun ref(id: String, uploaderUrl: String? = "https://y/@$id", sourceId: String = "youtube") = VideoRef(
        sourceId = sourceId,
        pageUrl = "https://y/watch?v=$id",
        remoteId = id,
        title = id,
        uploader = id,
        uploaderUrl = uploaderUrl,
    )

    @Test fun `recentDistinctChannels keeps most-recent order and dedupes`() {
        // newest-first, as WatchHistoryDao.observeAll() hands it back
        val history = listOf(
            ref("v1", uploaderUrl = "https://y/@a"),
            ref("v2", uploaderUrl = "https://y/@b"),
            ref("v3", uploaderUrl = "https://y/@a"), // repeat of @a, already seen -- dropped
            ref("v4", uploaderUrl = "https://y/@c"),
        )
        val channels = recentDistinctChannels(history)
        assertEquals(listOf("https://y/@a", "https://y/@b", "https://y/@c"), channels.map { it.uploaderUrl })
    }

    @Test fun `recentDistinctChannels skips rows with no channel url`() {
        val history = listOf(ref("v1", uploaderUrl = null), ref("v2", uploaderUrl = "https://y/@b"))
        assertEquals(listOf("https://y/@b"), recentDistinctChannels(history).map { it.uploaderUrl })
    }

    @Test fun `recentDistinctChannels caps at the given size`() {
        val history = (1..10).map { ref("v$it", uploaderUrl = "https://y/@c$it") }
        val channels = recentDistinctChannels(history, cap = MAX_FEED_CHANNELS)
        assertEquals(MAX_FEED_CHANNELS, channels.size)
        assertEquals(listOf("https://y/@c1", "https://y/@c2", "https://y/@c3", "https://y/@c4"), channels.map { it.uploaderUrl })
    }

    @Test fun `recentDistinctChannels on empty history yields no channels`() {
        assertTrue(recentDistinctChannels(emptyList()).isEmpty())
    }

    @Test fun `interleave round-robins uneven channel lists without crashing`() {
        val a = listOf(ref("a1"), ref("a2"), ref("a3"))
        val b = listOf(ref("b1"))
        val c = emptyList<VideoRef>()
        val merged = interleave(listOf(a, b, c))
        assertEquals(listOf("a1", "b1", "a2", "a3"), merged.map { it.remoteId })
    }

    @Test fun `interleave of nothing is nothing`() {
        assertTrue(interleave(emptyList()).isEmpty())
        assertTrue(interleave(listOf(emptyList(), emptyList())).isEmpty())
    }

    @Test fun `excludeWatched drops pages already in history`() {
        val candidates = listOf(ref("new1"), ref("watched1"), ref("new2"))
        val watched = setOf(ref("watched1").pageUrl)
        val fresh = excludeWatched(candidates, watched)
        assertEquals(listOf("new1", "new2"), fresh.map { it.remoteId })
    }

    @Test fun `excludeWatched against an empty watched set keeps everything`() {
        val candidates = listOf(ref("a"), ref("b"))
        assertEquals(candidates, excludeWatched(candidates, emptySet()))
    }
}
