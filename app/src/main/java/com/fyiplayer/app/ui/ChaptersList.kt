package com.fyiplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.core.Chapter
import com.fyiplayer.app.player.PlaybackSession
import com.fyiplayer.app.player.mmss

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
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTarget)
                .clickable { PlaybackSession.seekTo(chapter.startSeconds * 1000L) }
                .padding(horizontal = Spacing.pageMargin),
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
}
