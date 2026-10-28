package com.fyiplayer.app.player

import com.fyiplayer.app.core.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterMarkersTest {

    private val intro = Chapter("Intro", 0)
    private val building = Chapter("Building", 60)
    private val flying = Chapter("Flying", 180)
    private val chapters = listOf(intro, building, flying)

    @Test fun `chapterAt is null for an empty list`() {
        assertNull(chapterAt(emptyList(), 5_000L))
    }

    @Test fun `chapterAt is the chapter whose start has passed`() {
        assertEquals(building, chapterAt(chapters, 90_000L))
    }

    @Test fun `chapterAt switches exactly at a chapter start`() {
        assertEquals(building, chapterAt(chapters, 60_000L))
        assertEquals(intro, chapterAt(chapters, 59_999L))
    }

    @Test fun `chapterAt past the last start is the last chapter`() {
        assertEquals(flying, chapterAt(chapters, 9_999_000L))
    }

    @Test fun `chapterAt is null before the first chapter starts`() {
        assertNull(chapterAt(listOf(Chapter("Later", 30)), 10_000L))
    }

    @Test fun `notch fractions skip a chapter at zero and one past the end`() {
        val fractions = chapterStartFractions(
            listOf(intro, building, flying, Chapter("Past the end", 300)),
            durationMs = 240_000L,
        )
        assertEquals(2, fractions.size)
        assertEquals(0.25f, fractions[0], 0.0001f)
        assertEquals(0.75f, fractions[1], 0.0001f)
    }

    @Test fun `notch fractions are empty without a known duration`() {
        assertTrue(chapterStartFractions(chapters, durationMs = 0L).isEmpty())
    }

    @Test fun `chapters apply only to the page that is playing`() {
        val pageChapters = PageChapters(pageUrl = "https://www.youtube.com/watch?v=a", chapters = chapters)
        assertEquals(chapters, chaptersForPage("https://www.youtube.com/watch?v=a", pageChapters))
        assertTrue(chaptersForPage("https://www.youtube.com/watch?v=b", pageChapters).isEmpty())
        assertTrue(chaptersForPage(null, pageChapters).isEmpty())
    }
}
