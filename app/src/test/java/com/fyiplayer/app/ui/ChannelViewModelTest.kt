package com.fyiplayer.app.ui

import com.fyiplayer.app.core.ChannelTab
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.TAB_UNAVAILABLE_PREFIX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelViewModelTest {

    @Test fun `tab-unavailable prefix is recognised`() {
        assertTrue(isTabUnavailable(ExtractionError.Unsupported("${TAB_UNAVAILABLE_PREFIX}this channel has no courses tab")))
    }

    @Test fun `an ordinary Unsupported is not tab-unavailable`() {
        assertFalse(isTabUnavailable(ExtractionError.Unsupported("YouTube cannot browse channel tabs yet")))
    }

    @Test fun `a non-Unsupported error is never tab-unavailable`() {
        assertFalse(isTabUnavailable(ExtractionError.Network("timed out")))
        assertFalse(isTabUnavailable(RuntimeException("boom")))
    }

    @Test fun `tab-unavailable drops the tab from the available list`() {
        val current = listOf(ChannelTab.VIDEOS, ChannelTab.COURSES, ChannelTab.LIVE)
        val error = ExtractionError.Unsupported("${TAB_UNAVAILABLE_PREFIX}this channel has no courses tab")
        assertEquals(listOf(ChannelTab.VIDEOS, ChannelTab.LIVE), availableTabsAfter(current, ChannelTab.COURSES, error))
    }

    @Test fun `a non tab-unavailable error leaves every tab in place`() {
        val current = listOf(ChannelTab.VIDEOS, ChannelTab.COURSES, ChannelTab.LIVE)
        val error = ExtractionError.AccessChallenge("login wall")
        assertEquals(current, availableTabsAfter(current, ChannelTab.COURSES, error))
    }

    @Test fun `dedupeAppend keeps only genuinely new keys`() {
        val existing = listOf("a", "b")
        val incoming = listOf("b", "c", "c")
        assertEquals(listOf("a", "b", "c"), dedupeAppend(existing, incoming) { it })
    }

    @Test fun `nextPageToken stops when a page returns zero fresh items`() {
        assertEquals(null, nextPageToken(serverNextPage = "40", freshCount = 0))
    }

    @Test fun `nextPageToken advances when a page adds real items`() {
        assertEquals("40", nextPageToken(serverNextPage = "40", freshCount = 5))
    }

    @Test fun `a blank or empty channel search query is not sent`() {
        assertFalse(shouldSearchChannel(""))
        assertFalse(shouldSearchChannel("   "))
        assertTrue(shouldSearchChannel("cats"))
    }
}
