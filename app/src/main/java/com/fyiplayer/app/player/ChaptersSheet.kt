package com.fyiplayer.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.core.Chapter
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

/** Chapter picker for the video playing, same shape as [AudioTrackSheet]. The row under the
 *  playhead is highlighted. That position is read once when the sheet opens, so the highlight
 *  stays put while the sheet is up. [onSelect] gets the tapped chapter; the caller seeks to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(chapters: List<Chapter>, onSelect: (Chapter) -> Unit, onDismiss: () -> Unit) {
    val playingChapter = remember(chapters) { chapterAt(chapters, PlaybackSession.progress.value.positionMs) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.padding(bottom = Spacing.s16)) {
            item {
                Text(
                    stringResource(R.string.player_chapters),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.s16, vertical = Spacing.s8),
                )
            }
            items(chapters) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    highlighted = chapter == playingChapter,
                    horizontalPadding = Spacing.s16,
                    onClick = { onSelect(chapter) },
                )
            }
        }
    }
}

/** One chapter as a tappable row: its start time in the primary colour, then its title. Shared by
 *  the sheet and the Description tab's list, so both show a chapter the same way. */
@Composable
internal fun ChapterRow(chapter: Chapter, highlighted: Boolean, horizontalPadding: Dp, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .background(if (highlighted) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            mmss(chapter.startSeconds * 1000L),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(chapter.title, style = MaterialTheme.typography.bodyMedium)
    }
}
