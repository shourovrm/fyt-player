package com.fyiplayer.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-function coverage for the search-row tap dispatch (Home playAndOpen). */
class HomeScreenTest {

    @Test fun `channel URL shapes are recognised`() {
        assertTrue(isChannelPageUrl("https://www.youtube.com/channel/UCabc123"))
        assertTrue(isChannelPageUrl("https://www.youtube.com/@SomeHandle"))
        assertTrue(isChannelPageUrl("https://www.youtube.com/c/SomeName"))
        assertTrue(isChannelPageUrl("https://www.youtube.com/user/SomeName"))
        assertTrue(isChannelPageUrl("https://m.youtube.com/channel/UCabc123"))
    }

    @Test fun `video and shorts URLs are not channels`() {
        assertFalse(isChannelPageUrl("https://www.youtube.com/watch?v=abc123"))
        assertFalse(isChannelPageUrl("https://www.youtube.com/shorts/abc123"))
        assertFalse(isChannelPageUrl("https://youtu.be/abc123"))
    }

    @Test fun `unrelated or unparseable URLs stay conservative`() {
        assertFalse(isChannelPageUrl("https://example.com/channel/foo"))
        assertFalse(isChannelPageUrl("not a url"))
        assertFalse(isChannelPageUrl(""))
    }
}
