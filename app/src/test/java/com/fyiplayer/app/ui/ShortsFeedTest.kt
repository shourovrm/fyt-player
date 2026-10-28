package com.fyiplayer.app.ui

import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.TAB_UNAVAILABLE_PREFIX
import com.fyiplayer.app.core.SearchPage
import com.fyiplayer.app.core.VideoDetail
import com.fyiplayer.app.core.VideoRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Pure-function coverage for Shorts' subscription-composed feed -- no Android, no network. The
 *  merge/exclude/cap primitives themselves live in HomeFeed.kt and are exercised there; this file
 *  covers what's Shorts-specific: the recency-slot ordering, the newest-short date lookup,
 *  [fetchChannelShorts]'s per-channel error swallowing, and that a null duration never turns into
 *  a formatted string. */
class ShortsFeedTest {

    private val dayMillis = 24L * 60 * 60 * 1000
    private val nowMillis = 1_000 * dayMillis

    private fun ref(id: String, duration: Int? = null) = VideoRef(
        sourceId = "youtube",
        pageUrl = "https://y/watch?v=$id",
        remoteId = id,
        title = id,
        durationSeconds = duration,
    )

    private fun channel(newestAgeDays: Double?, vararg ids: String) = ChannelShorts(
        newestDateMillis = newestAgeDays?.let { nowMillis - (it * dayMillis).toLong() },
        shorts = ids.map { ref(it) },
    )

    private fun order(channels: List<ChannelShorts>, seen: Set<String> = emptySet(), keys: HashMap<String, Double> = HashMap(), seed: Int = 1) =
        orderShortsFeed(channels, nowMillis, seen.map { ref(it).pageUrl }.toSet(), keys, Random(seed)).map { it.remoteId }

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

    @Test fun `a missing shorts tab reports NoContent, not a failure`() = runBlocking {
        val result = fetchChannelShorts {
            throw ExtractionError.Unsupported("$TAB_UNAVAILABLE_PREFIX this channel has no shorts tab")
        }
        assertEquals(ChannelFetchOutcome.NoContent, result)
    }

    // The distinction that matters: a fetch failure must never be reported to the user as
    // "this channel posts no shorts", which is a claim we have no evidence for.
    @Test fun `a network failure reports Failed, not NoContent`() = runBlocking {
        val result = fetchChannelShorts { throw ExtractionError.Network("timed out") }
        assertEquals(ChannelFetchOutcome.Failed, result)
    }

    @Test fun `an unsupported error without the tab marker is a failure, not a missing tab`() = runBlocking {
        val result = fetchChannelShorts { throw ExtractionError.Unsupported("engine broke") }
        assertEquals(ChannelFetchOutcome.Failed, result)
    }

    // Uncapped on purpose: the ViewModel shows SHORTS_ITEMS_PER_CHANNEL and buffers the rest, so a
    // cap here would silently throw away the channel's first page beyond the first round. Watched
    // clips stay too: they are ordered last by orderShortsFeed, not dropped.
    @Test fun `fetchChannelShorts keeps every clip in tab order and the continuation`() = runBlocking {
        val page = SearchPage(items = (1..(FEED_ITEMS_PER_CHANNEL + 5)).map { ref("s$it") }, nextPage = "tok")
        val result = fetchChannelShorts { page } as ChannelFetchOutcome.Ok
        assertEquals(page.items, result.items)
        assertEquals("tok", result.nextPage)
    }

    @Test fun `slot 0 holds channels up to three days old and the boundary is inclusive`() {
        assertEquals(0, shortsSlotFor(nowMillis - 3 * dayMillis, nowMillis))
        assertEquals(0, shortsSlotFor(nowMillis + dayMillis, nowMillis)) // clock skew: never negative
        assertEquals(1, shortsSlotFor(nowMillis - 3 * dayMillis - 1, nowMillis))
    }

    @Test fun `each further three days is the next slot, with six days exactly in slot 1`() {
        assertEquals(1, shortsSlotFor(nowMillis - 6 * dayMillis, nowMillis))
        assertEquals(2, shortsSlotFor(nowMillis - 6 * dayMillis - 1, nowMillis))
        assertEquals(3, shortsSlotFor(nowMillis - 9 * dayMillis - 1, nowMillis))
    }

    @Test fun `an unknown date lands in the last slot`() {
        assertEquals(UNKNOWN_DATE_SLOT, shortsSlotFor(null, nowMillis))
        assertTrue(UNKNOWN_DATE_SLOT > shortsSlotFor(nowMillis - 10_000 * dayMillis, nowMillis))
    }

    @Test fun `a fresh channel comes before an old one regardless of subscription order`() {
        val result = order(listOf(channel(30.0, "old1", "old2"), channel(1.0, "new1", "new2")))
        assertEquals(setOf("new1", "new2"), result.take(2).toSet())
    }

    @Test fun `a channel with an unknown date comes after every dated channel`() {
        val result = order(listOf(channel(null, "unknown1"), channel(400.0, "old1")))
        assertEquals(listOf("old1", "unknown1"), result)
    }

    @Test fun `within a slot unseen clips come before seen clips`() {
        val result = order(listOf(channel(1.0, "a", "b", "c", "d")), seen = setOf("b", "d"))
        assertEquals(setOf("a", "c"), result.take(2).toSet())
        assertEquals(setOf("b", "d"), result.takeLast(2).toSet())
    }

    @Test fun `a seen clip stays in its own slot instead of sinking to the end of the feed`() {
        val result = order(listOf(channel(1.0, "seenNew"), channel(30.0, "unseenOld")), seen = setOf("seenNew"))
        assertEquals(listOf("seenNew", "unseenOld"), result)
    }

    // A channel landing later must not reshuffle clips already shown. Their keys are reused, so
    // their relative order holds; the new clips are placed among them, and an older slot stays last.
    @Test fun `a channel landing later keeps earlier clips in their relative order`() {
        val keys = HashMap<String, Double>()
        val firstPass = order(listOf(channel(1.0, "a", "b", "c", "d")), keys = keys, seed = 5)
        val secondPass = order(
            listOf(channel(1.0, "a", "b", "c", "d"), channel(1.0, "x", "y"), channel(30.0, "old")),
            keys = keys,
            seed = 99,
        )
        assertEquals(firstPass, secondPass.filter { it in firstPass })
        assertEquals(setOf("a", "b", "c", "d", "x", "y", "old"), secondPass.toSet())
        assertEquals("old", secondPass.last())
    }

    @Test fun `a clip listed by two channels appears once, in the first channel's slot`() {
        val result = order(listOf(channel(1.0, "shared", "a2"), channel(30.0, "shared", "b2")))
        assertEquals(1, result.count { it == "shared" })
        assertEquals(setOf("shared", "a2"), result.take(2).toSet()) // slot 0 is the first channel's
        assertEquals("b2", result.last())
    }

    // 2026-10-05T00:00:00Z. yt-dlp has no time of day, so the date is read as UTC midnight.
    @Test fun `parseUploadEpochMs reads yt-dlp YYYYMMDD as UTC midnight`() {
        assertEquals(1791158400000L, parseUploadEpochMs("20261005"))
    }

    // The shape NewPipeYoutubeSource.detail produces: the parsed instant in UTC. 2026-08-05T11:00:27Z.
    @Test fun `parseUploadEpochMs reads ISO-8601 in UTC`() {
        assertEquals(1785927627000L, parseUploadEpochMs("2026-08-05T11:00:27Z"))
    }

    // The same instant written with a local offset: 2026-08-05T04:00:27 at UTC-07:00.
    @Test fun `parseUploadEpochMs reads ISO-8601 with a non-UTC offset`() {
        assertEquals(1785927627000L, parseUploadEpochMs("2026-08-05T04:00:27-07:00"))
    }

    // No extractor path hands these shapes to parseUploadEpochMs (a date-only microformat date is
    // parsed to a full UTC instant first), so they stay null rather than being accepted untested.
    @Test fun `parseUploadEpochMs returns null for absent or unrecognised text rather than guessing`() {
        assertNull(parseUploadEpochMs(null))
        assertNull(parseUploadEpochMs(""))
        assertNull(parseUploadEpochMs("3 days ago"))
        assertNull(parseUploadEpochMs("2026-13-45T00:00:00Z"))
        assertNull(parseUploadEpochMs("2026-10-05"))
        assertNull(parseUploadEpochMs("Oct 5, 2026"))
    }

    @Test fun `a failed newest-short lookup is unknown date, not an error`() = runBlocking {
        val millis = lookupUploadMillis { throw ExtractionError.Network("timed out") }
        assertNull(millis)
    }

    @Test fun `a successful newest-short lookup yields the literal instant of a yt-dlp date`() = runBlocking {
        val millis = lookupUploadMillis { VideoDetail(ref = ref("n"), uploadDate = "20261005") }
        assertEquals(1791158400000L, millis)
    }

    @Test fun `a newest-short lookup with the NewPipe UTC instant yields that instant`() = runBlocking {
        val millis = lookupUploadMillis { VideoDetail(ref = ref("n"), uploadDate = "2026-08-05T11:00:27Z") }
        assertEquals(1785927627000L, millis)
    }

    @Test fun `a newest-short lookup with unparseable date text yields unknown`() = runBlocking {
        val millis = lookupUploadMillis { VideoDetail(ref = ref("n"), uploadDate = "3 days ago") }
        assertNull(millis)
    }

    @Test fun `shuffled order differs from tab order within a slot`() {
        val tab = (1..8).map { "s$it" }
        val result = order(listOf(channel(1.0, *tab.toTypedArray())), seed = 3)
        assertTrue(result != tab)
    }

    private fun cursor(buffer: List<VideoRef> = emptyList(), nextPage: String? = null, exhausted: Boolean = false) =
        ChannelShortsCursor(sourceId = "youtube", key = "c", buffer = buffer, nextPage = nextPage, exhausted = exhausted)

    @Test fun `next serves from the buffer without fetching when it holds enough`() = runBlocking {
        val (served, after) = cursor(buffer = (1..5).map { ref("b$it") }, nextPage = "tok").next(3) {
            error("must not fetch")
        }
        assertEquals(listOf("b1", "b2", "b3"), served.map { it.remoteId })
        assertEquals(listOf("b4", "b5"), after.buffer.map { it.remoteId })
        assertTrue(after.hasMore)
    }

    @Test fun `next refills from the continuation, skipping clips already buffered, and advances the token`() = runBlocking {
        val calls = mutableListOf<String?>()
        val (served, after) = cursor(buffer = listOf(ref("b1")), nextPage = "t1").next(3) { page ->
            calls += page
            SearchPage(items = listOf(ref("b1"), ref("p1"), ref("p2")), nextPage = "t2")
        }
        assertEquals(listOf("t1"), calls)
        assertEquals(listOf("b1", "p1", "p2"), served.map { it.remoteId })
        assertEquals("t2", after.nextPage)
        assertTrue(after.hasMore)
    }

    @Test fun `a continuation that returns its own token ends the channel`() = runBlocking {
        val (_, after) = cursor(nextPage = "same").next(3) { SearchPage(items = emptyList(), nextPage = "same") }
        assertTrue(after.exhausted)
        assertTrue(!after.hasMore)
    }

    @Test fun `refills are bounded per round when a page adds nothing new`() = runBlocking {
        var calls = 0
        val (served, after) = cursor(buffer = listOf(ref("w")), nextPage = "t0").next(3) { _ ->
            calls++
            SearchPage(items = listOf(ref("w")), nextPage = "t$calls")
        }
        assertEquals(MAX_REFILLS_PER_ROUND, calls)
        assertEquals(listOf("w"), served.map { it.remoteId })
        assertTrue(after.hasMore) // token still live: the next scroll asks again
    }

    @Test fun `a fetch failure ends paging but still serves what was buffered`() = runBlocking {
        val (served, after) = cursor(buffer = listOf(ref("b1")), nextPage = "t1").next(3) {
            throw ExtractionError.Network("timed out")
        }
        assertEquals(listOf("b1"), served.map { it.remoteId })
        assertTrue(after.exhausted)
        assertTrue(!after.hasMore)
    }

    @Test fun `no subscriptions means an empty feed`() {
        // mirrors ShortsViewModel.refreshFeed's early-return path: nothing to interleave, no crash
        assertTrue(interleave(emptyList()).isEmpty())
    }

    @Test fun `a null duration is never coerced -- nothing downstream can format it`() {
        val short = ref("noduration", duration = null)
        val merged = interleave(listOf(listOf(short)))
        assertEquals(null, merged.single().durationSeconds)
    }
}
