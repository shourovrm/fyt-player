package com.fyiplayer.app.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChunkedRangeDataSourceTest {

    @Test fun `parseContentRange extracts total from normal header`() {
        assertEquals(12345L, parseContentRange("bytes 0-0/12345"))
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

    @Test fun `resolveChunkTotal uses explicit total when known`() {
        assertEquals(12345L, resolveChunkTotal(true, 12345L, null))
    }

    @Test fun `resolveChunkTotal prefers explicit total over probe`() {
        assertEquals(12345L, resolveChunkTotal(true, 12345L, 67890L))
    }

    @Test fun `resolveChunkTotal uses probed total when explicit is unknown`() {
        assertEquals(67890L, resolveChunkTotal(true, C.LENGTH_UNSET.toLong(), 67890L))
    }

    @Test fun `resolveChunkTotal falls back to passthrough when probe fails`() {
        assertEquals(C.LENGTH_UNSET.toLong(), resolveChunkTotal(true, C.LENGTH_UNSET.toLong(), null))
    }

    @Test fun `resolveChunkTotal always passes through non videoplayback`() {
        assertEquals(C.LENGTH_UNSET.toLong(), resolveChunkTotal(false, 12345L, null))
        assertEquals(C.LENGTH_UNSET.toLong(), resolveChunkTotal(false, C.LENGTH_UNSET.toLong(), null))
    }
}
