package com.fyiplayer.app.ui

import com.fyiplayer.app.core.VideoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-function coverage for Home's subscriptions feed -- no Android, no network. */
class HomeFeedTest {

    private fun ref(id: String, sourceId: String = "youtube") = VideoRef(
        sourceId = sourceId,
        pageUrl = "https://y/watch?v=$id",
        remoteId = id,
        title = id,
        uploader = id,
    )

    @Test fun `interleave round-robins uneven channel lists without crashing`() {
        val a = listOf(ref("a1"), ref("a2"), ref("a3"))
        val b = listOf(ref("b1"))
        val c = emptyList<VideoRef>()
        val merged = interleave(listOf(a, b, c))
        assertEquals(listOf("a1", "b1", "a2", "a3"), merged.map { it.remoteId })
    }

    @Test fun `interleave round-robins across the full 8-channel cap`() {
        val bySource = (1..8).map { i -> (1..i).map { ref("s${i}v$it") } }
        val merged = interleave(bySource)
        assertEquals(bySource.sumOf { it.size }, merged.size)
        // round 0 pulls one item from every one of the 8 channels, in channel order
        assertEquals((1..8).map { "s${it}v1" }, merged.take(8).map { it.remoteId })
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

    @Test fun `sortByRecency orders newest first with null dates last, stably`() {
        val old = ref("old").copy(uploadEpochMs = 100L)
        val new = ref("new").copy(uploadEpochMs = 200L)
        val n1 = ref("n1") // no date
        val n2 = ref("n2") // no date, must stay after n1 (stable)
        val sorted = sortByRecency(listOf(old, n1, new, n2))
        assertEquals(listOf("new", "old", "n1", "n2"), sorted.map { it.remoteId })
    }

    @Test fun `no subscriptions means an empty feed`() {
        // mirrors HomeViewModel.refreshFeed's early-return path: nothing to interleave, no crash
        assertTrue(interleave(emptyList()).isEmpty())
    }
}
