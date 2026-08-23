package com.fyiplayer.app.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun `explicitTotal unknown when neither length nor clen is present`() {
        assertEquals(C.LENGTH_UNSET.toLong(), explicitTotal(0L, C.LENGTH_UNSET.toLong(), null))
    }

    @Test fun `explicitTotal ignores malformed clen`() {
        assertEquals(C.LENGTH_UNSET.toLong(), explicitTotal(0L, C.LENGTH_UNSET.toLong(), "not-a-number"))
    }
}
