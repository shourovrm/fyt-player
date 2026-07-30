package com.fyiplayer.app.download

import com.fyiplayer.app.core.VideoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure string logic behind file deletion -- no File/filesystem I/O in a unit test. safeBaseName
// and matchesDownloadFile decide which on-disk names a "delete file too" confirmation removes;
// getting the prefix wrong either leaves a .part file behind or deletes another row's file.
private fun ref(pageUrl: String, title: String = "A Title") =
    VideoRef(sourceId = "youtube", pageUrl = pageUrl, remoteId = pageUrl, title = title)

class DownloadFileNamingTest {

    @Test fun `matches the finished file and every engine sidecar for the same row`() {
        val r = ref("https://example.invalid/watch?v=abc")
        val base = safeBaseName(r)

        assertTrue(matchesDownloadFile("$base.mp4", r))
        assertTrue(matchesDownloadFile("$base.mp4.part", r))
        assertTrue(matchesDownloadFile("$base.mp4.ytdl", r))
        assertTrue(matchesDownloadFile("$base.webm", r))
    }

    @Test fun `does not match a different row's file even with a similar title`() {
        val a = ref("https://example.invalid/watch?v=aaa", title = "Same Title")
        val b = ref("https://example.invalid/watch?v=bbb", title = "Same Title")

        // Same sanitized title, different page URL -- the pageUrl-derived suffix must still keep
        // their base names apart, or clearing one row's file would delete the other's too.
        assertFalse(safeBaseName(a) == safeBaseName(b))
        assertFalse(matchesDownloadFile("${safeBaseName(b)}.mp4", a))
    }

    @Test fun `does not match a name that merely starts with the same letters`() {
        val r = ref("https://example.invalid/watch?v=xyz")
        val base = safeBaseName(r)

        // "<base>Extra.mp4" starts with the same characters as "<base>" but not with "<base>.",
        // so it must be rejected -- a prefix check without the trailing dot would over-match.
        assertFalse(matchesDownloadFile("${base}Extra.mp4", r))
    }

    @Test fun `unsafe filename characters in the title are sanitized`() {
        val r = ref("https://example.invalid/watch?v=q", title = "a/b\\c:d*e?f\"g<h>i|j")
        val base = safeBaseName(r)

        assertFalse(base.any { it in "\\/:*?\"<>|" })
    }

    @Test fun `base name is stable across repeated calls for the same ref, for --continue to keep matching`() {
        val r = ref("https://example.invalid/watch?v=stable")
        assertEquals(safeBaseName(r), safeBaseName(r))
    }

    @Test fun `blank title falls back to a non-empty base name`() {
        val r = ref("https://example.invalid/watch?v=blank", title = "   ")
        assertTrue(safeBaseName(r).isNotBlank())
    }
}
