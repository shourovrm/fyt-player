package com.fyiplayer.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.core.Chapter
import com.fyiplayer.app.player.ChapterRow
import com.fyiplayer.app.player.PlaybackSession

/** The uploader's chapters above the description text. A tap seeks the video that is playing, the
 *  same call a timestamp link in the description makes; nothing is listed when there are none. */
internal fun LazyListScope.chaptersSection(chapters: List<Chapter>) {
    if (chapters.isEmpty()) return
    item {
        Text(
            stringResource(R.string.detail_chapters),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        )
    }
    items(chapters) { chapter ->
        ChapterRow(chapter, highlighted = false, horizontalPadding = Spacing.pageMargin) {
            PlaybackSession.seekTo(chapter.startSeconds * 1000L)
        }
    }
}
