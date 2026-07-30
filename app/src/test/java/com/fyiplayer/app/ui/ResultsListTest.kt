package com.fyiplayer.app.ui

import org.junit.Assert.assertEquals
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
}
