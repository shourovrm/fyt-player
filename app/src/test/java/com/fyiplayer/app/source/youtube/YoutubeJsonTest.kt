package com.fyiplayer.app.source.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure JSON -> model mapping, no Android, no network.
class YoutubeJsonTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().readText()

    @Test
    fun flatPlaylistSkipsMalformedEntriesAndBuildsCanonicalUrl() {
        val refs = parseFlatPlaylistJson(fixture("/youtube/flat_playlist.json"))

        assertEquals(2, refs.size) // missing-id entry and the non-object entry are both skipped

        val first = refs[0]
        assertEquals("https://www.youtube.com/watch?v=abc12345678", first.pageUrl)
        assertEquals("abc12345678", first.remoteId)
        assertEquals(125, first.durationSeconds)
        assertEquals("Channel One", first.uploader)
        assertEquals("https://www.youtube.com/channel/UC1", first.uploaderUrl)
        assertEquals("https://example.invalid/thumb_large.jpg", first.thumbnailUrl) // widest wins

        val second = refs[1]
        assertEquals("https://www.youtube.com/watch?v=xyz98765432", second.pageUrl)
        assertEquals("Uploader Two", second.uploader)
        assertEquals("https://example.invalid/single_thumb.jpg", second.thumbnailUrl)
    }

    @Test
    fun storyboardsPicksFinestFormatAndComputesInterval() {
        val thumbs = checkNotNull(parseStoryboardsJson(fixture("/youtube/storyboards.json"), durationSeconds = 300))

        // sb1 wins: 3 fragments * 10x10 = 300 tiles beats sb0's 2 fragments * 5x5 = 50 tiles.
        assertEquals(3, thumbs.sprites.size)
        assertEquals(10, thumbs.sprites[0].cols)
        assertEquals(10, thumbs.sprites[0].rows)
        assertEquals(100, thumbs.sprites[0].count)
        assertEquals(1.0, thumbs.intervalSeconds, 0.001) // 300s / 300 tiles
    }

    @Test
    fun storyboardsFallsBackToFlatIntervalWithoutDuration() {
        val thumbs = checkNotNull(parseStoryboardsJson(fixture("/youtube/storyboards.json"), durationSeconds = null))
        assertEquals(10.0, thumbs.intervalSeconds, 0.001)
    }

    @Test
    fun commentsMapRootToNullParentAndSkipMalformed() {
        val comments = parseCommentsJson(fixture("/youtube/comments.json"))

        assertEquals(2, comments.size) // the id-less entry is skipped

        val top = comments.first { it.id == "c1" }
        assertEquals(null, top.parentId) // raw "root" becomes null
        assertEquals("Top Commenter", top.author)
        assertEquals(42L, top.likeCount)
        assertEquals(true, top.isHearted)
        assertEquals(false, top.isUploader)
        assertEquals("2 days ago", top.timeText)

        val reply = comments.first { it.id == "c1-r1" }
        assertEquals("c1", reply.parentId) // a reply carries its parent's id
        assertEquals(true, reply.isUploader)
    }

    @Test
    fun channelUploadsExcludeCurrentVideoAndRespectLimit() {
        val related = parseChannelUploadsForRelated(
            fixture("/youtube/channel_uploads.json"),
            currentPageUrl = "https://www.youtube.com/watch?v=current12345",
            limit = 12,
        )

        assertEquals(2, related.size)
        assertTrue(related.none { it.pageUrl == "https://www.youtube.com/watch?v=current12345" })
    }

    @Test
    fun channelUploadsRespectsLimitWhenLower() {
        val related = parseChannelUploadsForRelated(
            fixture("/youtube/channel_uploads.json"),
            currentPageUrl = "https://www.youtube.com/watch?v=current12345",
            limit = 1,
        )
        assertEquals(1, related.size)
    }
}
