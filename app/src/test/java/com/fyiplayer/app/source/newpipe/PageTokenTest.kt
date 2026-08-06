package com.fyiplayer.app.source.newpipe

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.Page

// Pure codec, no network, no NewPipe.init.
class PageTokenTest {

    @Test
    fun roundTripsEveryFieldIncludingBodyBytes() {
        val original = Page(
            "https://example.invalid/continue",
            "id-123",
            listOf("a", "b", "c"),
            mapOf("session" to "abc", "other" to "xyz"),
            byteArrayOf(1, 2, 3, 4, 5, -1, 0),
        )

        val restored = original.toToken().toPage()

        assertEquals(original.url, restored.url)
        assertEquals(original.id, restored.id)
        assertEquals(original.ids, restored.ids)
        assertEquals(original.cookies, restored.cookies)
        assertArrayEquals(original.body, restored.body)
    }

    @Test
    fun roundTripsNullFields() {
        // The single-arg constructor leaves id/ids/cookies/body null -- the codec must not invent
        // empty collections in their place, or a caller checking `== null` downstream would break.
        val original = Page("https://example.invalid/only-url")

        val restored = original.toToken().toPage()

        assertEquals(original.url, restored.url)
        assertNull(restored.id)
        assertNull(restored.ids)
        assertNull(restored.cookies)
        assertNull(restored.body)
    }

    @Test
    fun roundTripsEmptyBody() {
        val original = Page("https://example.invalid/x", byteArrayOf())

        val restored = original.toToken().toPage()

        assertArrayEquals(original.body, restored.body)
    }
}
