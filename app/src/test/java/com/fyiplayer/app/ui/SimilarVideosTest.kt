package com.fyiplayer.app.ui

import com.fyiplayer.app.core.VideoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-function coverage for the Similar tab's query builder -- no Android, no network. This is
 *  the whole quality of the feature (DECISIONS.md: no related/recommended list from the engine,
 *  so "similar" is a search on the video's own title). */
class SimilarVideosTest {

    private fun ref(id: String, title: String = id, sourceId: String = "youtube") = VideoRef(
        sourceId = sourceId,
        pageUrl = "https://y/watch?v=$id",
        remoteId = id,
        title = title,
    )

    @Test fun `strips bracketed and parenthesised noise`() {
        val q = buildSimilarQuery("Amazing Trick (Official Video) [4K]")
        assertFalse(q.contains("Official", ignoreCase = true))
        assertFalse(q.contains("4K", ignoreCase = true))
        assertTrue(q.contains("Amazing"))
        assertTrue(q.contains("Trick"))
    }

    @Test fun `strips emoji`() {
        val q = buildSimilarQuery("🔥🔥 Insane Basketball Dunks 🏀🔥")
        assertEquals("Insane Basketball Dunks", q.trim())
    }

    @Test fun `strips pipe-delimited official-video decoration`() {
        val q = buildSimilarQuery("Artist Name - Song Title | Official Music Video")
        assertFalse(q.contains("Official", ignoreCase = true))
        assertTrue(q.contains("Song"))
        assertTrue(q.contains("Title"))
    }

    @Test fun `all-caps clickbait title degrades to a sane non-blank query`() {
        val q = buildSimilarQuery("YOU WON'T BELIEVE WHAT HAPPENED NEXT (MUST WATCH)")
        assertTrue(q.isNotBlank())
        assertFalse(q.contains("MUST WATCH", ignoreCase = true))
        assertTrue(q.split(" ").size <= 6)
    }

    @Test fun `very short title is kept as-is`() {
        assertEquals("Cats", buildSimilarQuery("Cats"))
    }

    @Test fun `title that is entirely noise degrades to something sane, never empty`() {
        val q = buildSimilarQuery("(Official Video) [HD] 🔥🔥🔥")
        assertTrue(q.isNotBlank())
    }

    @Test fun `title that is pure emoji still never returns a blank query`() {
        val q = buildSimilarQuery("🔥🔥🔥")
        assertTrue(q.isNotBlank())
        assertEquals("video", q)
    }

    @Test fun `strips episode and part numbering`() {
        val q = buildSimilarQuery("My Show Episode 12 Part 2")
        assertFalse(q.contains("Episode", ignoreCase = true))
        assertFalse(q.contains("12"))
        assertFalse(q.contains("Part", ignoreCase = true))
    }

    @Test fun `caps to a handful of words`() {
        val q = buildSimilarQuery("one two three four five six seven eight nine ten", maxWords = 6)
        assertEquals(6, q.split(" ").size)
    }

    @Test fun `excludeCurrent drops the video matching by page URL`() {
        val current = ref("abc", "Current Video")
        val results = listOf(ref("abc", "Current Video (dup title in results)"), ref("def"), ref("ghi"))
        val filtered = excludeCurrent(results, current)
        assertEquals(listOf("def", "ghi"), filtered.map { it.remoteId })
    }

    @Test fun `excludeCurrent on a list without the current video changes nothing`() {
        val current = ref("abc")
        val results = listOf(ref("def"), ref("ghi"))
        assertEquals(results, excludeCurrent(results, current))
    }
}
