package com.fyiplayer.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure decisions behind StreamDownloader: which part file may be resumed, whether a finished part
// is really complete, and what a finished request window means. The HTTP loop itself is not unit
// tested (no MockWebServer dependency); these functions carry every branch that decides "done".
class StreamDownloadDecisionsTest {

    // --- part naming: a part is resumable only by the format that wrote it ---

    @Test fun `part names differ between formats of the same video`() {
        val first = partFileName("Title-abc", "dl", "137")
        val second = partFileName("Title-abc", "dl", "136")
        assertNotEquals(first, second)
    }

    @Test fun `part name is stable for the same format so resume still finds it`() {
        assertEquals(partFileName("Title-abc", "video", "137"), partFileName("Title-abc", "video", "137"))
    }

    @Test fun `part name keeps the delete-matching prefix and the part extension`() {
        val name = partFileName("Title-abc", "audio", "140")
        assertTrue(name.startsWith("Title-abc."))
        assertTrue(name.endsWith(".part"))
    }

    @Test fun `format id characters unsafe in a file name are replaced`() {
        val name = partFileName("base", "dl", "a/b:c d")
        assertFalse(name.removePrefix("base.dl.").any { it in "/: " })
    }

    @Test fun `an overlong format id cannot grow the part name without bound`() {
        val name = partFileName("base", "video", "x".repeat(500))
        assertTrue(name.length <= "base".length + ".video.".length + 24 + ".part".length)
    }

    // --- stale part cleanup ---

    @Test fun `parts of other formats and the legacy name are stale, the kept part is not`() {
        val keep = setOf(partFileName("T-1", "video", "136"), partFileName("T-1", "audio", "140"))
        val onDisk = listOf(
            partFileName("T-1", "video", "136"),
            partFileName("T-1", "audio", "140"),
            partFileName("T-1", "video", "137"),
            "T-1.dl.part",
            "T-1.video.part",
        )
        assertEquals(
            listOf(partFileName("T-1", "video", "137"), "T-1.dl.part", "T-1.video.part"),
            stalePartFileNames(onDisk, "T-1", keep),
        )
    }

    @Test fun `stale cleanup leaves finished files, subtitles and other rows alone`() {
        val onDisk = listOf("T-1.mp4", "T-1.srt", "T-12.dl.137.part", "Other-9.dl.137.part")
        assertEquals(emptyList<String>(), stalePartFileNames(onDisk, "T-1", emptySet()))
    }

    // --- completion check ---

    @Test fun `a part with exactly the expected length is complete`() {
        assertEquals(CompletionCheck.COMPLETE, completionCheck(actualBytes = 1000, expectedBytes = 1000))
    }

    @Test fun `a shorter part than the known total is a failure that keeps the part`() {
        assertEquals(CompletionCheck.SHORT, completionCheck(actualBytes = 999, expectedBytes = 1000))
    }

    @Test fun `a longer part than the known total is oversize`() {
        assertEquals(CompletionCheck.OVERSIZE, completionCheck(actualBytes = 1001, expectedBytes = 1000))
    }

    @Test fun `an unknown total accepts any non-empty part`() {
        assertEquals(CompletionCheck.COMPLETE, completionCheck(actualBytes = 5, expectedBytes = null))
    }

    @Test fun `an unknown total rejects an empty part`() {
        assertEquals(CompletionCheck.SHORT, completionCheck(actualBytes = 0, expectedBytes = null))
    }

    // --- window outcome ---

    private val window = 10_000L

    @Test fun `a connection closed before the declared length is truncated, never EOF`() {
        val step = windowStep(
            knownTotal = null, offset = 4_000, windowStart = 0, windowSize = window,
            declaredLength = 10_000, bytesRead = 4_000,
        )
        assertEquals(WindowStep.TRUNCATED, step)
    }

    @Test fun `truncation is caught even when the total is known`() {
        val step = windowStep(
            knownTotal = 50_000, offset = 4_000, windowStart = 0, windowSize = window,
            declaredLength = 10_000, bytesRead = 4_000,
        )
        assertEquals(WindowStep.TRUNCATED, step)
    }

    @Test fun `reaching the known total finishes`() {
        val step = windowStep(
            knownTotal = 25_000, offset = 25_000, windowStart = 20_000, windowSize = window,
            declaredLength = 5_000, bytesRead = 5_000,
        )
        assertEquals(WindowStep.FINISHED, step)
    }

    @Test fun `a full window below the known total continues`() {
        val step = windowStep(
            knownTotal = 25_000, offset = 10_000, windowStart = 0, windowSize = window,
            declaredLength = 10_000, bytesRead = 10_000,
        )
        assertEquals(WindowStep.CONTINUE, step)
    }

    @Test fun `an empty window finishes`() {
        val step = windowStep(
            knownTotal = null, offset = 20_000, windowStart = 20_000, windowSize = window,
            declaredLength = 0, bytesRead = 0,
        )
        assertEquals(WindowStep.FINISHED, step)
    }

    @Test fun `unknown total and a short window the server declared complete is EOF`() {
        val step = windowStep(
            knownTotal = null, offset = 23_000, windowStart = 20_000, windowSize = window,
            declaredLength = 3_000, bytesRead = 3_000,
        )
        assertEquals(WindowStep.FINISHED, step)
    }

    @Test fun `unknown total and a short window with no declared length is not trusted as EOF`() {
        // No Content-Length means a clean close mid-window looks the same as the real end, so the
        // next (empty) window is what confirms EOF.
        val step = windowStep(
            knownTotal = null, offset = 23_000, windowStart = 20_000, windowSize = window,
            declaredLength = -1, bytesRead = 3_000,
        )
        assertEquals(WindowStep.CONTINUE, step)
    }

    @Test fun `known total and a short window the server declared complete continues to the next window`() {
        val step = windowStep(
            knownTotal = 50_000, offset = 23_000, windowStart = 20_000, windowSize = window,
            declaredLength = 3_000, bytesRead = 3_000,
        )
        assertEquals(WindowStep.CONTINUE, step)
    }
}
