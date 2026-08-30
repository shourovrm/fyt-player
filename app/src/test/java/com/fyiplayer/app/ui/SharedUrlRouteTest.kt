package com.fyiplayer.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Share/open-with routing: only TikTok hosts go to the shorts pager. Pure host match. */
class SharedUrlRouteTest {
    @Test fun tiktokHostsAreVerticalClips() {
        assertTrue(isVerticalClipUrl("https://www.tiktok.com/@complex/video/7626254334065511711"))
        assertTrue(isVerticalClipUrl("https://vm.tiktok.com/ZSabc123/"))
        assertTrue(isVerticalClipUrl("https://vt.tiktok.com/ZSabc123/"))
    }

    @Test fun youtubeShortsPathIsVerticalClip() {
        assertTrue(isVerticalClipUrl("https://www.youtube.com/shorts/aRF4IF6oi4Y"))
        assertTrue(isVerticalClipUrl("https://youtube.com/shorts/aRF4IF6oi4Y?feature=share"))
        assertTrue(isVerticalClipUrl("https://m.youtube.com/shorts/aRF4IF6oi4Y"))
    }

    @Test fun everythingElseIsDetail() {
        assertFalse(isVerticalClipUrl("https://www.facebook.com/cnn/videos/10155529876156509/"))
        assertFalse(isVerticalClipUrl("https://www.youtube.com/watch?v=abc"))
        assertFalse(isVerticalClipUrl("https://youtu.be/abc"))
        assertFalse(isVerticalClipUrl("https://tiktok.com.evil.example/x"))
        assertFalse(isVerticalClipUrl("not a url"))
    }

    @Test fun sharedPlaylistUrlsResolveToCanonicalListing() {
        assertEquals(
            "https://www.youtube.com/playlist?list=PLabc123",
            youtubePlaylistUrl("https://www.youtube.com/playlist?list=PLabc123"),
        )
        // watch?v=X&list=PL.. -- prefer the playlist listing (issue #7).
        assertEquals(
            "https://www.youtube.com/playlist?list=PLabc123",
            youtubePlaylistUrl("https://www.youtube.com/watch?v=xyz&list=PLabc123"),
        )
    }

    @Test fun mixRadioPlaylistsAreNotRouted() {
        // RD* = mix/radio, the extractor rejects it ("Unable to recognize playlist") --
        // must not be treated as an openable playlist listing.
        assertNull(youtubePlaylistUrl("https://www.youtube.com/watch?v=xyz&list=RDabc123"))
    }

    @Test fun nonYoutubeOrListlessUrlsAreNotPlaylists() {
        assertNull(youtubePlaylistUrl("https://www.youtube.com/watch?v=xyz"))
        assertNull(youtubePlaylistUrl("https://www.tiktok.com/@x/video/123"))
        assertNull(youtubePlaylistUrl("not a url"))
    }
}
