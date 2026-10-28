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
