package com.fyiplayer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsListTest {

    @Test fun `seconds under a minute pad to two digits`() {
        assertEquals("0:09", formatDuration(9))
        assertEquals("0:59", formatDuration(59))
    }

    @Test fun `minutes and seconds with no leading hour`() {
        assertEquals("1:00", formatDuration(60))
        assertEquals("12:34", formatDuration(754))
    }

    @Test fun `an hour or more shows hours`() {
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("2:03:04", formatDuration(2 * 3600 + 3 * 60 + 4))
    }

    @Test fun `auto load fires near the end with more pages and nothing in flight`() {
        assertTrue(shouldAutoLoadMore(nearEnd = true, hasMore = true, isLoadingMore = false, loadMoreFailed = false))
    }

    @Test fun `auto load never fires after a failed page even once loading stops`() {
        // The loop: loading flips true -> false after a failure and the effect re-ran.
        assertFalse(shouldAutoLoadMore(nearEnd = true, hasMore = true, isLoadingMore = false, loadMoreFailed = true))
    }

    @Test fun `auto load stays off away from the end, without more pages, or while loading`() {
        assertFalse(shouldAutoLoadMore(nearEnd = false, hasMore = true, isLoadingMore = false, loadMoreFailed = false))
        assertFalse(shouldAutoLoadMore(nearEnd = true, hasMore = false, isLoadingMore = false, loadMoreFailed = false))
        assertFalse(shouldAutoLoadMore(nearEnd = true, hasMore = true, isLoadingMore = true, loadMoreFailed = false))
    }
}
