package com.fyiplayer.app.ui

import com.fyiplayer.app.core.VideoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-function coverage for the search shelf's list split -- no Android, no Compose. */
class ShortsShelfTest {

    private fun ref(id: String, isShort: Boolean) = VideoRef(
        sourceId = "youtube",
        pageUrl = "https://y/watch?v=$id",
        remoteId = id,
        title = id,
        isShort = isShort,
    )

    @Test fun `partitionShorts splits shorts from longform, each preserving order`() {
        val items = listOf(
            ref("a", isShort = false),
            ref("b", isShort = true),
            ref("c", isShort = false),
            ref("d", isShort = true),
        )
        val (shorts, longform) = partitionShorts(items)
        assertEquals(listOf("b", "d"), shorts.map { it.remoteId })
        assertEquals(listOf("a", "c"), longform.map { it.remoteId })
    }

    @Test fun `partitionShorts of nothing is nothing`() {
        val (shorts, longform) = partitionShorts(emptyList())
        assertTrue(shorts.isEmpty())
        assertTrue(longform.isEmpty())
    }

    @Test fun `partitionShorts of all-longform leaves the shelf empty`() {
        val items = listOf(ref("a", isShort = false), ref("b", isShort = false))
        val (shorts, longform) = partitionShorts(items)
        assertTrue(shorts.isEmpty())
        assertEquals(items, longform)
    }

    @Test fun `partitionShorts of all-shorts leaves the regular rows empty`() {
        val items = listOf(ref("a", isShort = true), ref("b", isShort = true))
        val (shorts, longform) = partitionShorts(items)
        assertEquals(items, shorts)
        assertTrue(longform.isEmpty())
    }
}
