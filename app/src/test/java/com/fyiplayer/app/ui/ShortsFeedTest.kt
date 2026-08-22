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

    @Test fun `a missing shorts tab reports NoContent, not a failure`() = runBlocking {
        val result = fetchChannelShorts(watched = emptySet()) {
            throw ExtractionError.Unsupported("$TAB_UNAVAILABLE_PREFIX this channel has no shorts tab")
        }
        assertEquals(ChannelFetchOutcome.NoContent, result)
    }

    // The distinction that matters: a fetch failure must never be reported to the user as
    // "this channel posts no shorts", which is a claim we have no evidence for.
    @Test fun `a network failure reports Failed, not NoContent`() = runBlocking {
        val result = fetchChannelShorts(watched = emptySet()) { throw ExtractionError.Network("timed out") }
        assertEquals(ChannelFetchOutcome.Failed, result)
    }

    @Test fun `an unsupported error without the tab marker is a failure, not a missing tab`() = runBlocking {
        val result = fetchChannelShorts(watched = emptySet()) { throw ExtractionError.Unsupported("engine broke") }
        assertEquals(ChannelFetchOutcome.Failed, result)
    }

    // Uncapped on purpose: the ViewModel shows FEED_ITEMS_PER_CHANNEL and buffers the rest, so a
    // cap here would silently throw away the channel's first page beyond the first round.
    @Test fun `fetchChannelShorts excludes watched, keeps the rest and the continuation`() = runBlocking {
        val page = SearchPage(items = (1..(FEED_ITEMS_PER_CHANNEL + 5)).map { ref("s$it") }, nextPage = "tok")
        val watched = setOf(ref("s1").pageUrl)
        val result = fetchChannelShorts(watched = watched) { page } as ChannelFetchOutcome.Ok
        assertEquals(FEED_ITEMS_PER_CHANNEL + 4, result.items.size)
        assertTrue(result.items.none { it.pageUrl == ref("s1").pageUrl })
        assertEquals("tok", result.nextPage)
    }

    private fun cursor(buffer: List<VideoRef> = emptyList(), nextPage: String? = null, exhausted: Boolean = false) =
        ChannelShortsCursor(sourceId = "youtube", key = "c", buffer = buffer, nextPage = nextPage, exhausted = exhausted)

    @Test fun `next serves from the buffer without fetching when it holds enough`() = runBlocking {
        val (served, after) = cursor(buffer = (1..5).map { ref("b$it") }, nextPage = "tok").next(3, emptySet()) {
            error("must not fetch")
        }
        assertEquals(listOf("b1", "b2", "b3"), served.map { it.remoteId })
        assertEquals(listOf("b4", "b5"), after.buffer.map { it.remoteId })
        assertTrue(after.hasMore)
    }

    @Test fun `next refills from the continuation, excluding watched, and advances the token`() = runBlocking {
        val calls = mutableListOf<String?>()
        val (served, after) = cursor(buffer = listOf(ref("b1")), nextPage = "t1").next(3, setOf(ref("w").pageUrl)) { page ->
            calls += page
            SearchPage(items = listOf(ref("w"), ref("p1"), ref("p2")), nextPage = "t2")
        }
        assertEquals(listOf("t1"), calls)
        assertEquals(listOf("b1", "p1", "p2"), served.map { it.remoteId })
        assertEquals("t2", after.nextPage)
        assertTrue(after.hasMore)
    }

    @Test fun `a continuation that returns its own token ends the channel`() = runBlocking {
        val (_, after) = cursor(nextPage = "same").next(3, emptySet()) { SearchPage(items = emptyList(), nextPage = "same") }
        assertTrue(after.exhausted)
        assertTrue(!after.hasMore)
    }

    @Test fun `refills are bounded per round so an all-watched tab never storms`() = runBlocking {
        var calls = 0
        val (served, after) = cursor(nextPage = "t0").next(3, setOf(ref("w").pageUrl)) { page ->
            calls++
            SearchPage(items = listOf(ref("w")), nextPage = "t$calls")
        }
        assertEquals(MAX_REFILLS_PER_ROUND, calls)
        assertTrue(served.isEmpty())
        assertTrue(after.hasMore) // token still live: the next scroll asks again
    }

    @Test fun `a fetch failure ends paging but still serves what was buffered`() = runBlocking {
        val (served, after) = cursor(buffer = listOf(ref("b1")), nextPage = "t1").next(3, emptySet()) {
            throw ExtractionError.Network("timed out")
        }
        assertEquals(listOf("b1"), served.map { it.remoteId })
        assertTrue(after.exhausted)
        assertTrue(!after.hasMore)
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
