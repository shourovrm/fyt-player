package com.fyiplayer.app.ui

import com.fyiplayer.app.core.VideoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTrailTest {

    private fun video(name: String) = VideoRef(
        sourceId = "youtube",
        pageUrl = "https://www.youtube.com/watch?v=$name",
        remoteId = name,
        title = name,
    )

    @Test fun `a fresh trail has nothing to go forward to`() {
        assertNull(WatchTrail().nextForward())
    }

    @Test fun `Previous records the video left and Next returns to it`() {
        val a = video("a")
        val trail = WatchTrail().recordLeft(a)

        assertEquals(a, trail.nextForward())
        assertEquals(emptyList<VideoRef>(), trail.popForward().forward)
    }

    @Test fun `forward history is last in first out`() {
        val trail = WatchTrail().recordLeft(video("a")).recordLeft(video("b"))

        assertEquals(video("b"), trail.nextForward())
        assertEquals(video("a"), trail.popForward().nextForward())
    }

    @Test fun `a pop of the back stack keeps the forward list`() {
        val trail = WatchTrail(knownDepth = 3).recordLeft(video("a")).onBackStackDepthChanged(2)

        assertEquals(video("a"), trail.nextForward())
    }

    @Test fun `the push that Next starts keeps the rest of the forward list`() {
        val trail = WatchTrail(knownDepth = 3)
            .recordLeft(video("a"))
            .recordLeft(video("b"))
            .popForward()
            .onBackStackDepthChanged(4)

        assertEquals(video("a"), trail.nextForward())
    }

    @Test fun `a push from elsewhere clears the forward list`() {
        val trail = WatchTrail(knownDepth = 3).recordLeft(video("a")).onBackStackDepthChanged(4)

        assertNull(trail.nextForward())
    }

    @Test fun `the awaited push flag is spent by the next stack change`() {
        val afterTrailPush = WatchTrail(knownDepth = 3)
            .recordLeft(video("a"))
            .recordLeft(video("b"))
            .popForward()
            .onBackStackDepthChanged(4)

        val afterOtherPush = afterTrailPush.recordLeft(video("c")).onBackStackDepthChanged(5)

        assertNull(afterOtherPush.nextForward())
    }

    @Test fun `Next goes to the trail first, then to Similar only while autoplay is on`() {
        val trail = WatchTrail().recordLeft(video("a"))

        assertEquals(video("a"), trail.nextTarget(similar = video("s"), autoplayNext = false))
        assertEquals(video("s"), WatchTrail().nextTarget(similar = video("s"), autoplayNext = true))
        assertNull(WatchTrail().nextTarget(similar = video("s"), autoplayNext = false))
    }

    @Test fun `cleared drops the forward list`() {
        val trail = WatchTrail().recordLeft(video("a")).cleared()

        assertNull(trail.nextForward())
    }

    @Test fun `Previous is offered only when the entry below is a Detail page`() {
        assertTrue(WatchTrail.canGoPrevious(Routes.DETAIL))
        assertFalse(WatchTrail.canGoPrevious(Routes.HOME))
        assertFalse(WatchTrail.canGoPrevious(Routes.LIBRARY))
        assertFalse(WatchTrail.canGoPrevious(null))
    }
}
