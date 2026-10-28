package com.fyiplayer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortsNavTest {

    @Test fun `session not started yet does nothing`() {
        assertEquals(ShortsNavAction.NONE, shortsNavAction(page = 0, index = -1))
    }

    @Test fun `settling on the current item does nothing`() {
        assertEquals(ShortsNavAction.NONE, shortsNavAction(page = 2, index = 2))
    }

    @Test fun `settling one page forward reuses the prefetch`() {
        assertEquals(ShortsNavAction.NEXT, shortsNavAction(page = 3, index = 2))
    }

    @Test fun `settling one page back always re-resolves`() {
        assertEquals(ShortsNavAction.PREVIOUS, shortsNavAction(page = 1, index = 2))
    }

    @Test fun `settling more than one page away is a jump`() {
        assertEquals(ShortsNavAction.JUMP, shortsNavAction(page = 5, index = 2))
        assertEquals(ShortsNavAction.JUMP, shortsNavAction(page = 0, index = 3))
    }

    @Test fun `grid index in range maps straight through`() {
        assertEquals(3, clampGridIndex(3, itemCount = 10))
    }

    @Test fun `grid index clamps to the last item when the feed shrank`() {
        assertEquals(4, clampGridIndex(9, itemCount = 5))
    }

    @Test fun `negative grid index clamps to zero`() {
        assertEquals(0, clampGridIndex(-1, itemCount = 5))
    }

    @Test fun `empty feed clamps to zero, never divides or crashes`() {
        assertEquals(0, clampGridIndex(0, itemCount = 0))
    }

    @Test fun `progress fraction is the plain ratio`() {
        assertEquals(0.5f, shortsProgressFraction(positionMs = 5_000, durationMs = 10_000))
    }

    @Test fun `progress fraction with zero duration never divides, renders empty`() {
        assertEquals(0f, shortsProgressFraction(positionMs = 5_000, durationMs = 0))
    }

    @Test fun `progress fraction with unknown negative duration renders empty`() {
        assertEquals(0f, shortsProgressFraction(positionMs = 5_000, durationMs = -1))
    }

    @Test fun `progress fraction clamps past the end to 1`() {
        assertEquals(1f, shortsProgressFraction(positionMs = 12_000, durationMs = 10_000))
    }

    @Test fun `progress fraction clamps a negative position to 0`() {
        assertEquals(0f, shortsProgressFraction(positionMs = -500, durationMs = 10_000))
    }

    private fun clip(name: String) =
        com.fyiplayer.app.core.VideoRef(sourceId = "youtube", pageUrl = "https://y/$name", remoteId = name, title = name)

    @Test fun `page maps to the session index by page url, not by position`() {
        val items = listOf(clip("a"), clip("x"), clip("b"), clip("c")) // x landed late, interleaved
        val session = listOf(clip("a"), clip("b"), clip("c"))
        assertEquals(1, sessionIndexForPage(session, items, page = 2)) // b
        assertEquals(null, sessionIndexForPage(session, items, page = 1)) // x not enqueued yet
    }

    @Test fun `session index maps back to the pager page by page url`() {
        val items = listOf(clip("a"), clip("x"), clip("b"))
        val session = listOf(clip("a"), clip("b"))
        assertEquals(2, pageForSessionIndex(session, sessionIndex = 1, items = items))
        assertEquals(null, pageForSessionIndex(session, sessionIndex = 5, items = items))
        assertEquals(null, pageForSessionIndex(listOf(clip("q")), sessionIndex = 0, items = items))
    }

    @Test fun `session holds the feed only when every queued clip is in it`() {
        val items = listOf(clip("a"), clip("b"), clip("c"))
        assertEquals(true, sessionHoldsFeed(listOf(clip("a"), clip("c")), items))
        assertEquals(false, sessionHoldsFeed(listOf(clip("a"), clip("z")), items)) // same first clip, other queue
        assertEquals(false, sessionHoldsFeed(emptyList(), items))
    }

    @Test fun `tap opens the clip by page url when the feed re-ordered under the grid`() {
        val items = listOf(clip("x"), clip("a"), clip("b")) // b was painted at index 1 before x landed
        assertEquals(2, pagerPageForTap(items, tappedPageUrl = "https://y/b", paintedIndex = 1))
    }

    @Test fun `tap falls back to the painted index when the clip left the feed`() {
        val items = listOf(clip("a"), clip("b"))
        assertEquals(1, pagerPageForTap(items, tappedPageUrl = "https://y/gone", paintedIndex = 5))
    }

    @Test fun `landing with the pager closed takes the re-ordered feed as is`() {
        val current = listOf(clip("a"), clip("b"))
        val reordered = listOf(clip("c"), clip("b"), clip("a"))
        assertEquals(reordered, feedAfterLanding(current, reordered, pagerOpen = false))
    }

    @Test fun `landing with the pager open keeps shown clips in place and appends new ones`() {
        val current = listOf(clip("a"), clip("b"))
        val reordered = listOf(clip("c"), clip("b"), clip("a"), clip("d"))
        assertEquals(
            listOf("a", "b", "c", "d"),
            feedAfterLanding(current, reordered, pagerOpen = true).map { it.remoteId },
        )
    }

    @Test fun `missing feed items are found by page url in feed order`() {
        val items = listOf(clip("a"), clip("x"), clip("b"), clip("y"))
        val session = listOf(clip("a"), clip("b"))
        assertEquals(listOf("x", "y"), feedItemsMissingFromSession(session, items).map { it.remoteId })
    }
}
