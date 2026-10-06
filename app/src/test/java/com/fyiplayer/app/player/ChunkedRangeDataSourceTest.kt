package com.fyiplayer.app.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// NOTE: android.net.Uri/DataSpec are stubbed ("RuntimeException: Stub!") in this module's plain
// JVM unit tests -- no Robolectric is configured (see build.gradle.kts, out of this task's file
// scope). open()'s Uri-touching decision points are therefore pulled out as pure functions
// (isChunkable, explicitTotal, parseContentRange) and exercised directly here.
class ChunkedRangeDataSourceTest {

    @Test fun `parseContentRange extracts total from normal header`() {
        assertEquals(12345L, parseContentRange("bytes 0-0/12345"))
    }

    @Test fun `parseContentRange reads total from the first window's response, not a 0-0 probe`() {
        // The window is range=0-10485759 (CHUNK_BYTES-1) -- proves the total comes off a real
        // 10 MB window's Content-Range, not a throwaway range=0-0 probe response.
        assertEquals(52428800L, parseContentRange("bytes 0-10485759/52428800"))
    }

    @Test fun `parseContentRange returns null when slash is missing`() {
        assertNull(parseContentRange("bytes 0-0"))
    }

    @Test fun `parseContentRange returns null for asterisk total`() {
        assertNull(parseContentRange("bytes 0-0/*"))
    }

    @Test fun `parseContentRange returns null for malformed values`() {
        assertNull(parseContentRange("not-bytes 0-0/12345"))
        assertNull(parseContentRange("bytes 0-0/abc"))
        assertNull(parseContentRange("bytes 0-0/"))
        assertNull(parseContentRange(null))
    }

    @Test fun `isChunkable accepts query-style videoplayback URLs`() {
        assertTrue(isChunkable("/videoplayback", "id=abc&itag=136"))
    }

    @Test fun `isChunkable rejects HLS segment URLs -- path-encoded, no query string`() {
        assertFalse(isChunkable("/videoplayback/id/abc/itag/136/range/0-999", null))
        assertFalse(isChunkable("/videoplayback/id/abc/itag/136/range/0-999", ""))
    }

    @Test fun `isChunkable rejects non-videoplayback URLs`() {
        assertFalse(isChunkable("/subtitles", "lang=en"))
        assertFalse(isChunkable(null, "id=abc"))
    }

    @Test fun `explicitTotal -- clen path unchanged`() {
        assertEquals(12345L, explicitTotal(0L, C.LENGTH_UNSET.toLong(), "12345"))
    }

    @Test fun `explicitTotal prefers ExoPlayer's requested span over clen`() {
        assertEquals(1500L, explicitTotal(1000L, 500L, "999999"))
    }

    @Test fun `bounded DASH segment read -- total is the segment end and open reports the segment length`() {
        // DataSpec(position=5_000_000, length=800_000) as DefaultDashChunkSource sends it; clen is
        // the whole file and must lose. open() returns total - position, i.e. exactly the length.
        val position = 5_000_000L
        val length = 800_000L
        val total = explicitTotal(position, length, "90000000")
        assertEquals(5_800_000L, total)
        assertEquals(length, total - position)
    }

    @Test fun `bounded read under one window ends without reopening`() {
        // A 800 KB segment is one range= window: the single END_OF_INPUT arrives at position ==
        // endExclusive, so no second request is made.
        val windows = ScriptedWindows(ArrayDeque(listOf(500_000, 300_000, C.RESULT_END_OF_INPUT)))
        val chain = WindowChain(
            startPosition = 5_000_000, endExclusive = 5_800_000,
            readWindow = windows::read,
            openNextWindow = { windows.reopenCount++ },
        )
        val buffer = ByteArray(1_000_000)
        assertEquals(500_000, chain.read(buffer, 0, buffer.size))
        assertEquals(300_000, chain.read(buffer, 0, buffer.size))
        assertEquals(C.RESULT_END_OF_INPUT, chain.read(buffer, 0, buffer.size))
        assertEquals(0, windows.reopenCount)
    }

    @Test fun `explicitTotal unknown when neither length nor clen is present`() {
        assertEquals(C.LENGTH_UNSET.toLong(), explicitTotal(0L, C.LENGTH_UNSET.toLong(), null))
    }

    @Test fun `explicitTotal ignores malformed clen`() {
        assertEquals(C.LENGTH_UNSET.toLong(), explicitTotal(0L, C.LENGTH_UNSET.toLong(), "not-a-number"))
    }

    /** Scripted upstream: each entry is what one read() call returns; opens are counted. */
    private class ScriptedWindows(private val script: ArrayDeque<Int>) {
        var reopenCount = 0
        fun read(buffer: ByteArray, offset: Int, length: Int): Int = script.removeFirstOrNull() ?: C.RESULT_END_OF_INPUT
    }

    @Test fun `window chain advances position and chains into the next window`() {
        val windows = ScriptedWindows(ArrayDeque(listOf(4, C.RESULT_END_OF_INPUT, 2, C.RESULT_END_OF_INPUT)))
        val chain = WindowChain(
            startPosition = 0, endExclusive = 6,
            readWindow = windows::read,
            openNextWindow = { windows.reopenCount++ },
        )
        val buffer = ByteArray(8)
        assertEquals(4, chain.read(buffer, 0, 8))
        assertEquals(2, chain.read(buffer, 0, 8)) // END from window one, reopen, then 2 bytes
        assertEquals(C.RESULT_END_OF_INPUT, chain.read(buffer, 0, 8)) // position == end
        assertEquals(1, windows.reopenCount)
    }

    @Test fun `window chain throws instead of recursing when a reopened window is empty`() {
        // Server answers every window with zero bytes while position < end: the old recursive
        // read() looped to StackOverflowError.
        val windows = ScriptedWindows(ArrayDeque())
        val chain = WindowChain(
            startPosition = 0, endExclusive = 100,
            readWindow = windows::read,
            openNextWindow = { windows.reopenCount++ },
        )
        try {
            chain.read(ByteArray(8), 0, 8)
            fail("expected IOException")
        } catch (expected: java.io.IOException) {
            assertEquals(1, windows.reopenCount) // one reopen, never a storm
        }
    }
}
