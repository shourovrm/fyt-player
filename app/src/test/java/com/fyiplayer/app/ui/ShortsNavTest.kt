package com.fyiplayer.app.ui

import com.fyiplayer.app.core.VideoRef
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

    @Test fun `append dedupes by page URL across paging rounds`() {
        val existing = listOf(ref("a"), ref("b"))
        val incoming = listOf(ref("b"), ref("c")) // "b" reappears from a feed that shifted underneath
        val merged = appendDeduped(existing, incoming)
        assertEquals(listOf("a", "b", "c"), merged.map { it.remoteId })
    }

    private fun ref(id: String) = VideoRef(
        sourceId = "youtube", pageUrl = "https://example.com/$id", remoteId = id, title = id,
    )
}
