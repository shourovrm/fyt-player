package com.fyiplayer.app.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.core.Chapter
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.fyiExtras

/**
 * The top-end corner of the player: the Chapters button, then the Dub (audio language) button to
 * its right. The caller places this at Alignment.TopEnd and shows it only while the controls are
 * up. Each button shows only when it has something to offer: Chapters when the video has any,
 * Dub when there is more than one audio language to switch between.
 */
@Composable
internal fun PlayerTopActions(
    audioTracks: List<AudioTrackOption>,
    onOpenAudioTracks: () -> Unit,
    chapters: List<Chapter>,
    onOpenChapters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = MaterialTheme.fyiExtras
    Row(
        modifier = modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chapters.isNotEmpty()) {
            val description = stringResource(R.string.player_chapters)
            IconButton(
                onClick = onOpenChapters,
                colors = IconButtonDefaults.iconButtonColors(containerColor = extras.videoScrim),
                modifier = Modifier
                    .size(Sizes.touchTarget)
                    .semantics { contentDescription = description },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = extras.onVideo,
                    modifier = Modifier.size(Sizes.icon),
                )
            }
        }
        if (audioTracks.size > 1) {
            val description = stringResource(R.string.player_audio_language)
            IconButton(
                onClick = onOpenAudioTracks,
                colors = IconButtonDefaults.iconButtonColors(containerColor = extras.videoScrim),
                modifier = Modifier
                    .size(Sizes.touchTarget)
                    .semantics { contentDescription = description },
            ) {
                GlobeGlyph(tint = extras.onVideo, size = Sizes.icon)
            }
        }
    }
}
