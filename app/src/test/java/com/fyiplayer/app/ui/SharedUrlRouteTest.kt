package com.fyiplayer.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Share/open-with routing: only TikTok hosts go to the shorts pager. Pure host match. */
class SharedUrlRouteTest {
    @Test fun tiktokHostsAreVerticalClips() {
        assertTrue(isVerticalClipUrl("https://www.tiktok.com/@complex/video/7626254334065511711"))
        assertTrue(isVerticalClipUrl("https://vm.tiktok.com/ZSabc123/"))
        assertTrue(isVerticalClipUrl("https://vt.tiktok.com/ZSabc123/"))
    }

    @Test fun everythingElseIsDetail() {
        assertFalse(isVerticalClipUrl("https://www.facebook.com/cnn/videos/10155529876156509/"))
        assertFalse(isVerticalClipUrl("https://www.youtube.com/shorts/abc"))
        assertFalse(isVerticalClipUrl("https://tiktok.com.evil.example/x"))
        assertFalse(isVerticalClipUrl("not a url"))
    }
}
