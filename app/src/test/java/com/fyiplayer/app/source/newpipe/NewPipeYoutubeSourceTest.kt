package com.fyiplayer.app.source.newpipe

import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure helpers only -- no network, no NewPipe.init.
class NewPipeYoutubeSourceTest {

    @Test
    fun belowOneThousandIsExact() {
        assertEquals("999", compactCount(999))
        assertEquals("0", compactCount(0))
    }

    @Test
    fun thousandsDropTrailingZeroDecimal() {
        assertEquals("1K", compactCount(1_000))
        assertEquals("1.2K", compactCount(1_200))
        assertEquals("999K", compactCount(999_000))
    }

    @Test
    fun millionsAndBillionsFollowTheSamePattern() {
        assertEquals("1M", compactCount(1_000_000))
        assertEquals("1.2M", compactCount(1_200_000))
        assertEquals("1B", compactCount(1_000_000_000))
    }

    @Test
    fun negativeIsUnknownNeverInvented() {
        assertNull(compactCount(-1))
        assertNull(compactCount(-100))
    }

    @Test
    fun futureDateIsUpcoming() {
        val now = OffsetDateTime.parse("2026-08-08T00:00:00Z")
        val premiereStart = DateWrapper(now.plusHours(2))
        assertTrue(isUpcoming(premiereStart, now))
    }

    @Test
    fun pastOrNullDateIsNotUpcoming() {
        val now = OffsetDateTime.parse("2026-08-08T00:00:00Z")
        assertFalse(isUpcoming(DateWrapper(now.minusDays(1)), now))
        assertFalse(isUpcoming(null, now))
    }
}

/** The channel-search handler seam: the fork's extractor reads the query off the handler's
 *  ORIGINAL url, so the url [NewPipeYoutubeSource.searchChannel] feeds it must round-trip. Pure
 *  (link-handler factories never touch the network). */
class NewPipeChannelSearchHandlerTest {
    @Test
    fun searchUrlYieldsSearchTabHandlerThatKeepsTheQuery() {
        val factory = org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory.getInstance()
        val handler = factory.fromUrl(
            com.fyiplayer.app.source.youtube.channelSearchUrl("https://www.youtube.com/channel/UCabc123/videos", "cats & dogs"),
        )
        assertEquals("channel/UCabc123", handler.id)
        assertEquals(org.schabi.newpipe.extractor.linkhandler.ChannelTabs.SEARCH, handler.contentFilters.single().name)
        assertEquals(
            "cats & dogs",
            org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory
                .getSearchQueryFromUrl(handler.originalUrl),
        )
    }
}

class ShortByDurationTest {
    private fun ref(dur: Int?, short: Boolean = false) = com.fyiplayer.app.core.VideoRef(
        sourceId = "youtube", pageUrl = "https://y/watch?v=x", remoteId = "x", title = "t",
        durationSeconds = dur, isShort = short,
    )

    @Test fun atOrUnderSixtySecondsIsShort() {
        assertTrue(ref(60).shortByDuration().isShort)
        assertTrue(ref(17).shortByDuration().isShort)
    }

    @Test fun overSixtyOrUnknownStaysLongform() {
        assertFalse(ref(61).shortByDuration().isShort)
        assertFalse(ref(null).shortByDuration().isShort)
        assertFalse(ref(0).shortByDuration().isShort)
    }

    @Test fun realFlagIsNeverCleared() {
        assertTrue(ref(600, short = true).shortByDuration().isShort)
    }
}
