package com.fyiplayer.app.ui

import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.TAB_UNAVAILABLE_PREFIX
import com.fyiplayer.app.core.SearchPage
import com.fyiplayer.app.core.VideoRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-function coverage for Shorts' subscription-composed feed -- no Android, no network. The
 *  merge/exclude/cap primitives themselves live in HomeFeed.kt and are exercised there; this file
 *  covers what's Shorts-specific: [fetchChannelShorts]'s per-channel error swallowing, and that a
 *  null duration (real shape of a shorts-tab entry) never turns into a formatted string. */
class ShortsFeedTest {

    private fun ref(id: String, duration: Int? = null) = VideoRef(
        sourceId = "youtube",
        pageUrl = "https://y/watch?v=$id",
        remoteId = id,
        title = id,
        durationSeconds = duration,
    )

    @Test fun `interleave round-robins channels with uneven shorts counts`() {
        val a = listOf(ref("a1"), ref("a2"), ref("a3"))
        val b = listOf(ref("b1"))
        val merged = interleave(listOf(a, b))
        assertEquals(listOf("a1", "b1", "a2", "a3"), merged.map { it.remoteId })
    }

    @Test fun `interleave dedupes by page URL across channels`() {
        val a = listOf(ref("shared"), ref("a2"))
        val b = listOf(ref("shared"), ref("b2")) // same short reachable via two subscribed channels
        val merged = interleave(listOf(a, b))
        assertEquals(listOf("shared", "a2", "b2"), merged.map { it.remoteId })
    }

    @Test fun `excludeWatched drops shorts already in history`() {
        val candidates = listOf(ref("new1"), ref("watched1"))
        val fresh = excludeWatched(candidates, setOf(ref("watched1").pageUrl))
        assertEquals(listOf("new1"), fresh.map { it.remoteId })
    }

    @Test fun `a missing shorts tab reports NoShortsTab, not a failure`() = runBlocking {
        val result = fetchChannelShorts(watched = emptySet()) {
            throw ExtractionError.Unsupported("$TAB_UNAVAILABLE_PREFIX this channel has no shorts tab")
        }
        assertEquals(ChannelShortsOutcome.NoShortsTab, result)
    }

    // The distinction that matters: a fetch failure must never be reported to the user as
    // "this channel posts no shorts", which is a claim we have no evidence for.
    @Test fun `a network failure reports Failed, not NoShortsTab`() = runBlocking {
        val result = fetchChannelShorts(watched = emptySet()) { throw ExtractionError.Network("timed out") }
        assertEquals(ChannelShortsOutcome.Failed, result)
    }

    @Test fun `an unsupported error without the tab marker is a failure, not a missing tab`() = runBlocking {
        val result = fetchChannelShorts(watched = emptySet()) { throw ExtractionError.Unsupported("engine broke") }
        assertEquals(ChannelShortsOutcome.Failed, result)
    }

    @Test fun `fetchChannelShorts excludes watched and caps to the per-channel limit`() = runBlocking {
        val page = SearchPage(items = (1..(FEED_ITEMS_PER_CHANNEL + 5)).map { ref("s$it") })
        val watched = setOf(ref("s1").pageUrl)
        val result = itemsOf(fetchChannelShorts(watched = watched) { page })
        assertEquals(FEED_ITEMS_PER_CHANNEL, result.size)
        assertTrue(result.none { it.pageUrl == ref("s1").pageUrl })
    }

    @Test fun `no subscriptions means no channels to fetch and an empty feed`() {
        val channels = capChannels(emptyList())
        assertTrue(channels.isEmpty())
        // mirrors ShortsViewModel.refreshFeed's early-return path: nothing to interleave, no crash
        assertTrue(interleave(emptyList()).isEmpty())
    }

    @Test fun `a null duration is never coerced -- nothing downstream can format it`() {
        val short = ref("noduration", duration = null)
        val merged = interleave(listOf(listOf(short)))
        assertEquals(null, merged.single().durationSeconds)
    }
}
