package com.fyiplayer.app.source.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeSourceTest {
    private val source = YoutubeSource()

    @Test
    fun matchesRealHostForms() {
        assertTrue(source.matches("https://www.youtube.com/watch?v=abc123"))
        assertTrue(source.matches("https://youtube.com/watch?v=abc123"))
        assertTrue(source.matches("https://m.youtube.com/watch?v=abc123"))
        assertTrue(source.matches("https://music.youtube.com/watch?v=abc123"))
        assertTrue(source.matches("https://youtu.be/abc123"))
        assertTrue(source.matches("https://www.youtube.com/shorts/abc123"))
    }

    @Test
    fun rejectsLookalikeHost() {
        assertFalse(source.matches("https://notyoutube.com.evil.tld/watch?v=abc123"))
        assertFalse(source.matches("https://www.youtube.com.evil.tld/watch?v=abc123"))
        assertFalse(source.matches("https://evil.tld/path/youtube.com/watch?v=abc123"))
    }

    @Test
    fun idAndDisplayName() {
        assertEquals("youtube", source.id)
        assertEquals("YouTube", source.displayName)
    }
}
