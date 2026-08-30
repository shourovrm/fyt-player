package com.fyiplayer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-function coverage for the Description tab's link routing -- no Android, no network. */
class DescriptionTabTest {

    @Test fun `parses pure-seconds timestamp`() {
        assertEquals(153L, parseYoutubeTimestamp("153"))
    }

    @Test fun `parses hms-style timestamp`() {
        assertEquals(3723L, parseYoutubeTimestamp("1h2m3s"))
        assertEquals(125L, parseYoutubeTimestamp("2m5s"))
        assertEquals(9L, parseYoutubeTimestamp("9s"))
    }

    @Test fun `rejects malformed timestamp`() {
        assertNull(parseYoutubeTimestamp(""))
        assertNull(parseYoutubeTimestamp("abc"))
        assertNull(parseYoutubeTimestamp("1x2y"))
    }

    @Test fun `extracts video id from watch url`() {
        assertEquals("abc123", youtubeVideoId("https://www.youtube.com/watch?v=abc123&t=10s"))
    }

    @Test fun `extracts video id from youtu-be url`() {
        assertEquals("abc123", youtubeVideoId("https://youtu.be/abc123"))
    }

    @Test fun `extracts video id from shorts url`() {
        assertEquals("abc123", youtubeVideoId("https://www.youtube.com/shorts/abc123"))
    }

    @Test fun `non-youtube or non-video url has no video id`() {
        assertNull(youtubeVideoId("https://example.com/watch?v=abc123"))
        assertNull(youtubeVideoId("https://www.youtube.com/@someone"))
    }

    @Test fun `recognises playlist urls`() {
        assertEquals(true, isYoutubePlaylistUrl("https://www.youtube.com/playlist?list=PL123"))
        assertEquals(true, isYoutubePlaylistUrl("https://www.youtube.com/watch?v=abc&list=PL123"))
        assertEquals(false, isYoutubePlaylistUrl("https://www.youtube.com/watch?v=abc"))
    }

    private val currentUrl = "https://www.youtube.com/watch?v=abc123"

    @Test fun `parses colon timestamp`() {
        assertEquals(125L, parseColonTimestamp("2:05"))
        assertEquals(3723L, parseColonTimestamp("1:02:03"))
        assertNull(parseColonTimestamp("abc"))
        assertNull(parseColonTimestamp("1:2:3:4"))
    }

    @Test fun `linkifies a bare url and trims trailing punctuation`() {
        val spans = findLinkSpans("(see https://example.com/foo).", currentUrl)
        assertEquals(1, spans.size)
        assertEquals("https://example.com/foo", spans[0].target)
        assertEquals("https://example.com/foo", "(see https://example.com/foo).".substring(spans[0].start, spans[0].end))
    }

    @Test fun `linkifies a scheme-less youtube link`() {
        val spans = findLinkSpans("more at youtube.com/watch?v=xyz", currentUrl)
        assertEquals(listOf("https://youtube.com/watch?v=xyz"), spans.map { it.target })
    }

    @Test fun `linkifies a handle mention but not an email`() {
        val spans = findLinkSpans("follow @cooluser123 not me@example.com", currentUrl)
        assertEquals(listOf("https://www.youtube.com/@cooluser123"), spans.map { it.target })
    }

    @Test fun `linkifies colon timestamps to a seek url for the current video`() {
        val spans = findLinkSpans("highlights at 1:23 and 1:02:03", currentUrl)
        assertEquals(
            listOf("$currentUrl&t=83s", "$currentUrl&t=3723s"),
            spans.map { it.target },
        )
    }

    @Test fun `no false positives in plain text`() {
        assertEquals(emptyList<LinkSpan>(), findLinkSpans("just a normal sentence, nothing to see.", currentUrl))
    }
}
