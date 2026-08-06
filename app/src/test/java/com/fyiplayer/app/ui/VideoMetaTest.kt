package com.fyiplayer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMetaTest {

    @Test fun `known units compact to a letter`() {
        assertEquals("3d", shortAge("3 days ago"))
        assertEquals("2w", shortAge("2 weeks ago"))
        assertEquals("5mo", shortAge("5 months ago"))
        assertEquals("1y", shortAge("1 year ago"))
    }

    @Test fun `Streamed prefix is stripped along with the unit`() {
        assertEquals("2w", shortAge("Streamed 2 weeks ago"))
    }

    @Test fun `unrecognised text passes through unchanged`() {
        assertEquals("premiered yesterday", shortAge("premiered yesterday"))
        assertEquals("just now", shortAge("just now"))
        assertEquals("3 hours ago", shortAge("3 hours ago"))
    }

    @Test fun `null stays null`() {
        assertEquals(null, shortAge(null))
    }
}
