package com.fyiplayer.app.player

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.core.Chapter
import com.fyiplayer.app.ui.theme.fyiExtras

/** The chapters the detail page loaded, tagged with the page they belong to. Kept whether or not
 *  that page is playing yet, because the detail page can load before the user presses Play. */
data class PageChapters(val pageUrl: String, val chapters: List<Chapter>)

/** The chapters that apply to the page playing now. Another page's chapters never apply, so a
 *  queued video cannot show the chapters of the video the detail page showed last. */
internal fun chaptersForPage(playingPageUrl: String?, pageChapters: PageChapters): List<Chapter> =
    if (playingPageUrl != null && playingPageUrl == pageChapters.pageUrl) pageChapters.chapters else emptyList()

/** The chapter the playhead is in: the last one that started at or before [positionMs], or null
 *  before the first chapter. [chapters] must be in start order, which the extractor keeps. */
internal fun chapterAt(chapters: List<Chapter>, positionMs: Long): Chapter? =
    chapters.lastOrNull { it.startSeconds * 1000L <= positionMs }

/** The two parts of the scrub preview label. [timeText] is the playhead clock. [trailingText] is
 *  "/ total" when no chapter applies, or the chapter title when one does. [trailingIsChapter] tells
 *  the card which of the two it is drawing, so it can cap a long title at the card width. */
internal data class SeekPreviewLabel(val timeText: String, val trailingText: String, val trailingIsChapter: Boolean)

/** The scrub preview label for [positionMs]. A blank chapter title counts as no chapter: a bare
 *  "2:00 " with nothing after it would read as a missing value. */
internal fun seekPreviewLabel(positionMs: Long, durationMs: Long, chapter: Chapter?): SeekPreviewLabel {
    val timeText = positionClock(positionMs, durationMs)
    if (chapter == null || chapter.title.isBlank()) {
        return SeekPreviewLabel(timeText, "/ ${mmss(durationMs)}", trailingIsChapter = false)
    }
    return SeekPreviewLabel(timeText, chapter.title, trailingIsChapter = true)
}

/** Left edge, in container pixels, of a scrub label centred over the card. The card is
 *  [cardLeftPx] .. [cardLeftPx] + [cardWidthPx] inside a container [containerWidthPx] wide. The
 *  label is shifted toward the middle only as far as needed to keep [marginPx] clear of both
 *  container edges. A label wider than the container minus both margins can never fit, so it is
 *  pinned to the left margin and the caller ellipsises its title. */
internal fun seekPreviewLabelLeftPx(
    cardLeftPx: Int,
    cardWidthPx: Int,
    labelWidthPx: Int,
    containerWidthPx: Int,
    marginPx: Int,
): Int {
    val lowestLeftPx = marginPx
    val highestLeftPx = containerWidthPx - marginPx - labelWidthPx
    if (highestLeftPx < lowestLeftPx) return lowestLeftPx
    val centredLeftPx = cardLeftPx + (cardWidthPx - labelWidthPx) / 2
    return centredLeftPx.coerceIn(lowestLeftPx, highestLeftPx)
}

/** Each chapter start as a fraction of [durationMs], for the seek-bar notches. A chapter at 0
 *  gets no notch because the bar's own left edge already marks it, and a start at or past the end
 *  has no place on the bar. */
internal fun chapterStartFractions(chapters: List<Chapter>, durationMs: Long): List<Float> {
    if (durationMs <= 0) return emptyList()
    return chapters
        .filter { it.startSeconds > 0 && it.startSeconds * 1000L < durationMs }
        .map { it.startSeconds * 1000f / durationMs }
}

/** A thin notch on the seek bar at each chapter start. Drawn over the played fill and the
 *  sponsored spans, so it stays visible wherever it falls on the track. Same Canvas shape as
 *  [SponsorMarkers]. */
@Composable
internal fun ChapterMarkers(chapters: List<Chapter>, durationMs: Long, modifier: Modifier = Modifier) {
    val fractions = chapterStartFractions(chapters, durationMs)
    if (fractions.isEmpty()) return
    val notchColor = MaterialTheme.fyiExtras.videoScrim
    Canvas(modifier) {
        val notchWidth = 2.dp.toPx()
        fractions.forEach { fraction ->
            drawRect(
                color = notchColor,
                // Centred on the start so the notch marks the exact position, not the pixel after it.
                topLeft = Offset(fraction * size.width - notchWidth / 2, 0f),
                size = Size(notchWidth, size.height),
            )
        }
    }
}
