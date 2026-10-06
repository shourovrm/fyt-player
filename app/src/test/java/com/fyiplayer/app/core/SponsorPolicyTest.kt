package com.fyiplayer.app.core

import com.fyiplayer.app.player.parseSponsorSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun segment(start: Long, end: Long, category: SponsorCategory) = SponsorSegment(start, end, category)

private fun policy(
    vararg modes: Pair<SponsorCategory, SponsorMode>,
    enabled: Boolean = true,
    whitelist: Set<String> = emptySet(),
) = SponsorPolicy(
    enabled = enabled,
    modes = SponsorCategory.entries.associateWith { it.defaultMode } + modes,
    whitelistedChannels = whitelist,
)

class SponsorPolicyTest {

    @Test fun `defaults keep today's behaviour - sponsor skips, the rest are off`() {
        val defaults = SponsorPolicy(enabled = true)
        assertEquals(SponsorMode.AUTO_SKIP, defaults.modeOf(SponsorCategory.SPONSOR))
        SponsorCategory.entries.filter { it != SponsorCategory.SPONSOR }.forEach {
            assertEquals(SponsorMode.OFF, defaults.modeOf(it))
        }
    }

    @Test fun `api names are the strings the SponsorBlock API uses`() {
        assertEquals(
            listOf("sponsor", "intro", "outro", "interaction", "selfpromo", "music_offtopic", "preview", "filler"),
            SponsorCategory.entries.map { it.apiName },
        )
        assertEquals(SponsorCategory.SELF_PROMO, SponsorCategory.fromApiName("selfpromo"))
        assertNull(SponsorCategory.fromApiName("poi_highlight"))
    }

    @Test fun `channel keys ignore www, scheme, trailing slash and query`() {
        val expected = "https://youtube.com/channel/UC123"
        assertEquals(expected, canonicalChannelKey("https://www.youtube.com/channel/UC123"))
        assertEquals(expected, canonicalChannelKey("http://m.youtube.com/channel/UC123/"))
        assertEquals(expected, canonicalChannelKey("https://youtube.com/channel/UC123?si=abc"))
        assertNull(canonicalChannelKey("https://youtube.com"))
        assertNull(canonicalChannelKey("not a url"))
    }

    @Test fun `whitelist matches whatever url form the listing carries`() {
        val key = canonicalChannelKey("https://www.youtube.com/channel/UC123")!!
        val withList = policy(whitelist = setOf(key))
        assertTrue(withList.isChannelWhitelisted("https://youtube.com/channel/UC123/"))
        assertFalse(withList.isChannelWhitelisted("https://youtube.com/channel/UC999"))
        assertFalse(withList.isChannelWhitelisted(null))
    }

    @Test fun `usable segments are empty when disabled or the channel is whitelisted`() {
        val segments = listOf(segment(0, 1000, SponsorCategory.SPONSOR))
        assertEquals(emptyList<SponsorSegment>(), usableSponsorSegments(segments, policy(enabled = false), null))
        val key = canonicalChannelKey("https://youtube.com/channel/UC1")!!
        assertEquals(
            emptyList<SponsorSegment>(),
            usableSponsorSegments(segments, policy(whitelist = setOf(key)), "https://youtube.com/channel/UC1"),
        )
    }

    @Test fun `usable segments drop categories that are off`() {
        val segments = listOf(segment(0, 1000, SponsorCategory.SPONSOR), segment(2000, 3000, SponsorCategory.INTRO))
        val usable = usableSponsorSegments(segments, policy(), null)
        assertEquals(listOf(SponsorCategory.SPONSOR), usable.map { it.category })
    }

    @Test fun `entering an auto-skip segment skips to its end once`() {
        val sponsor = segment(10_000, 20_000, SponsorCategory.SPONSOR)
        val first = decideSponsorAction(10_500, listOf(sponsor), policy(), lastSkippedStart = null)
        assertEquals(sponsor, first.skip)
        // The position still reads inside the segment right after the seek: no second skip.
        val second = decideSponsorAction(10_600, listOf(sponsor), policy(), lastSkippedStart = 10_000)
        assertNull(second.skip)
    }

    @Test fun `a show-button segment offers instead of skipping`() {
        val intro = segment(0, 5_000, SponsorCategory.INTRO)
        val decision = decideSponsorAction(1_000, listOf(intro), policy(SponsorCategory.INTRO to SponsorMode.SHOW_BUTTON), null)
        assertNull(decision.skip)
        assertEquals(intro, decision.offer)
    }

    @Test fun `outside every segment there is nothing to do`() {
        val sponsor = segment(10_000, 20_000, SponsorCategory.SPONSOR)
        val decision = decideSponsorAction(20_000, listOf(sponsor), policy(), null)
        assertNull(decision.skip)
        assertNull(decision.offer)
    }

    @Test fun `response parsing keeps only this video's skippable known categories`() {
        val body = """
            [
              {"videoID": "other", "segments": [{"category": "sponsor", "segment": [1.0, 2.0]}]},
              {"videoID": "abc", "segments": [
                {"category": "intro", "segment": [0.0, 5.5], "actionType": "skip"},
                {"category": "sponsor", "segment": [10.0, 20.0]},
                {"category": "sponsor", "segment": [30.0, 40.0], "actionType": "mute"},
                {"category": "poi_highlight", "segment": [50.0, 50.0]},
                {"category": "filler", "segment": [60.0, 60.0]}
              ]}
            ]
        """.trimIndent()
        val parsed = parseSponsorSegments(body, "abc")
        assertEquals(
            listOf(
                SponsorSegment(0, 5_500, SponsorCategory.INTRO),
                SponsorSegment(10_000, 20_000, SponsorCategory.SPONSOR),
            ),
            parsed,
        )
    }

    @Test fun `response without this video gives no segments`() {
        assertEquals(emptyList<SponsorSegment>(), parseSponsorSegments("""[{"videoID":"x","segments":[]}]""", "abc"))
    }
}
