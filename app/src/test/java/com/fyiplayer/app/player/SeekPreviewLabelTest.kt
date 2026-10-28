package com.fyiplayer.app.player

import com.fyiplayer.app.core.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class SeekPreviewLabelTest {

    private val eightMinutesMs = 480_000L
    private val twoMinutesMs = 120_000L
    private val smallFileChapter = Chapter("One small file", 100)

    @Test fun `a chapter at the scrub position replaces the total with its title`() {
        assertEquals(
            SeekPreviewLabel("2:00", "One small file", trailingIsChapter = true),
            seekPreviewLabel(twoMinutesMs, eightMinutesMs, smallFileChapter),
        )
    }

    @Test fun `no chapter keeps the total as today`() {
        assertEquals(
            SeekPreviewLabel("2:00", "/ 8:00", trailingIsChapter = false),
            seekPreviewLabel(twoMinutesMs, eightMinutesMs, chapter = null),
        )
    }

    @Test fun `a blank chapter title keeps the total instead of an empty trailing part`() {
        assertEquals(
            SeekPreviewLabel("2:00", "/ 8:00", trailingIsChapter = false),
            seekPreviewLabel(twoMinutesMs, eightMinutesMs, Chapter("  ", 100)),
        )
    }

    @Test fun `without a chapter the label joins to the same text as the bar clock`() {
        val hourLongDurationMs = 3_723_000L
        val fiveMinutesMs = 300_000L
        val label = seekPreviewLabel(fiveMinutesMs, hourLongDurationMs, chapter = null)
        assertEquals(formatPosition(fiveMinutesMs, hourLongDurationMs), "${label.timeText} ${label.trailingText}")
    }

    @Test fun `an hour-long video shows the position with hours even under an hour`() {
        val hourLongDurationMs = 7_200_000L
        val fiveMinutesMs = 300_000L
        assertEquals(
            SeekPreviewLabel("0:05:00", "One small file", trailingIsChapter = true),
            seekPreviewLabel(fiveMinutesMs, hourLongDurationMs, smallFileChapter),
        )
    }

    @Test fun `an hour-long video past one hour shows h mm ss`() {
        val hourLongDurationMs = 7_200_000L
        val oneHourFiveMinutesMs = 3_900_000L
        assertEquals("1:05:00", seekPreviewLabel(oneHourFiveMinutesMs, hourLongDurationMs, null).timeText)
    }
}
