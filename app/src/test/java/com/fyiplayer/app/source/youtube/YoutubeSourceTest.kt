package com.fyiplayer.app.source.youtube

import com.fyiplayer.app.core.ChannelTab
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.TAB_UNAVAILABLE_PREFIX
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

    @Test
    fun channelTabUrlAppendsSegment() {
        assertEquals(
            "https://www.youtube.com/@handle/videos",
            channelTabUrl("https://www.youtube.com/@handle", ChannelTab.VIDEOS),
        )
        assertEquals(
            "https://www.youtube.com/@handle/shorts",
            channelTabUrl("https://www.youtube.com/@handle", ChannelTab.SHORTS),
        )
        assertEquals(
            "https://www.youtube.com/@handle/streams",
            channelTabUrl("https://www.youtube.com/@handle", ChannelTab.LIVE),
        )
    }

    @Test
    fun channelTabUrlHandlesTrailingSlash() {
        assertEquals(
            "https://www.youtube.com/@handle/playlists",
            channelTabUrl("https://www.youtube.com/@handle/", ChannelTab.PLAYLISTS),
        )
    }

    @Test
    fun channelTabUrlReplacesExistingTabSuffix() {
        assertEquals(
            "https://www.youtube.com/@handle/courses",
            channelTabUrl("https://www.youtube.com/@handle/videos", ChannelTab.COURSES),
        )
    }

    @Test
    fun channelSearchUrlEncodesQuery() {
        val url = channelSearchUrl("https://www.youtube.com/@handle", "cats & dogs")
        assertEquals("https://www.youtube.com/@handle/search?query=cats+%26+dogs", url)
    }

    @Test
    fun channelSearchUrlStripsExistingTabSuffixAndTrailingSlash() {
        val url = channelSearchUrl("https://www.youtube.com/@handle/videos/", "a b")
        assertEquals("https://www.youtube.com/@handle/search?query=a+b", url)
    }

    @Test
    fun isTabUnavailableMatchesEngineWording() {
        val err = ExtractionError.Unsupported(
            "unknown engine failure",
            RuntimeException("ERROR: [youtube:tab] This channel does not have a courses tab"),
        )
        assertTrue(isTabUnavailable(err))
    }

    @Test
    fun isTabUnavailableFalseForUnrelatedFailure() {
        val err = ExtractionError.Unsupported("unknown engine failure", RuntimeException("ERROR: something else broke"))
        assertFalse(isTabUnavailable(err))
    }

    @Test
    fun tabUnavailableErrorCarriesMarkerPrefixAndNoRawEngineText() {
        val err = tabUnavailableError(ChannelTab.LIVE)
        assertTrue(err.message!!.startsWith(TAB_UNAVAILABLE_PREFIX))
        assertTrue(err.message!!.contains("streams"))
    }
}
