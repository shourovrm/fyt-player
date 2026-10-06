package com.fyiplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.core.Chapter
import com.fyiplayer.app.player.PlaybackSession
import com.fyiplayer.app.player.mmss

/** The uploader's chapters above the description text. A tap seeks the video that is playing, the
 *  same call a timestamp link in the description makes; nothing is listed when there are none. */
internal fun LazyListScope.chaptersSection(chapters: List<Chapter>) {
    if (chapters.isEmpty()) return
    item {
        Text(
            "Chapters",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    items(chapters) { chapter ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { PlaybackSession.seekTo(chapter.startSeconds * 1000L) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                mmss(chapter.startSeconds * 1000L),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(chapter.title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
