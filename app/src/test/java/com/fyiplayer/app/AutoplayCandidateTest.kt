package com.fyiplayer.app

import com.fyiplayer.app.core.VideoRef
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayCandidateTest {

    private val current = VideoRef(
        sourceId = "youtube", pageUrl = "https://youtube.com/watch?v=current", remoteId = "current", title = "t",
    )

    private fun candidate(
        pageUrl: String = "https://youtube.com/watch?v=other",
        isShort: Boolean = false,
        isLive: Boolean = false,
        isUpcoming: Boolean = false,
    ) = VideoRef(sourceId = "youtube", pageUrl = pageUrl, remoteId = "other", title = "t", isShort = isShort, isLive = isLive, isUpcoming = isUpcoming)

    @Test fun `a plain other video qualifies`() {
        assertTrue(isAutoplayCandidate(candidate(), current))
    }

    @Test fun `the same video is rejected`() {
        assertFalse(isAutoplayCandidate(candidate(pageUrl = current.pageUrl), current))
    }

    @Test fun `a channel or playlist ref is rejected`() {
        assertFalse(isAutoplayCandidate(candidate(pageUrl = "https://youtube.com/channel/UCabc"), current))
    }

    @Test fun `a short is rejected`() {
        assertFalse(isAutoplayCandidate(candidate(isShort = true), current))
    }

    @Test fun `a live broadcast is rejected`() {
        assertFalse(isAutoplayCandidate(candidate(isLive = true), current))
    }

    @Test fun `an upcoming premiere is rejected`() {
        assertFalse(isAutoplayCandidate(candidate(isUpcoming = true), current))
    }
}
