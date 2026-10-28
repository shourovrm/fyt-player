package com.fyiplayer.app.ui

import com.fyiplayer.app.core.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-function coverage for removing the uploader's timestamp list from the Description tab. */
class ChapterLinesTest {

    private val kiteChapters = listOf(Chapter("Intro", 0), Chapter("Building", 90), Chapter("Flying", 250))

    @Test fun `strips a chapters block with its heading and keeps the prose around it`() {
        val description = """
            Great video about kites.

            Chapters:
            0:00 Intro
            1:30 Building
            4:10 Flying

            Follow me on social.
        """.trimIndent()
        val expected = "Great video about kites.\n\nFollow me on social."
        assertEquals(expected, stripChapterLines(description, kiteChapters))
    }

    @Test fun `strips bullets, brackets and parenthesised timestamps`() {
        val description = "Intro text\n- [0:00] Intro\n▶ (1:30) Building\n4:10 - Flying\nOutro text"
        assertEquals("Intro text\nOutro text", stripChapterLines(description, kiteChapters))
    }

    @Test fun `keeps a timestamp in prose that is not a chapter start`() {
        val description = "see 1:30 for the kite build\n0:00 Intro"
        assertEquals("see 1:30 for the kite build", stripChapterLines(description, kiteChapters))
    }

    @Test fun `leaves the description unchanged when the video has no chapters`() {
        val description = "Chapters:\n0:00 Intro\n1:30 Building"
        assertEquals(description, stripChapterLines(description, emptyList()))
    }

    @Test fun `keeps a heading when no chapter line follows it`() {
        val description = "Chapters:\nSubscribe for more kites."
        assertEquals(description, stripChapterLines(description, kiteChapters))
    }

    @Test fun `removes a heading with an emoji and colon`() {
        val description = "⏱️ Timestamps:\n1:30 Building\nDone"
        assertEquals("Done", stripChapterLines(description, kiteChapters))
    }

    @Test fun `removes a chapter list at the very end without a trailing gap`() {
        val description = "Kites are fun.\n\nChapters\n0:00 Intro\n1:30 Building"
        assertEquals("Kites are fun.", stripChapterLines(description, kiteChapters))
    }

    @Test fun `a description with no chapter lines is unchanged`() {
        val description = "Line one.\n\n\nLine two."
        assertEquals(description, stripChapterLines(description, kiteChapters))
    }
}
