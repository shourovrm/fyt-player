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
}
