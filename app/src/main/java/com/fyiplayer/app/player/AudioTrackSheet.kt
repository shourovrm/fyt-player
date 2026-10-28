package com.fyiplayer.app.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Spacing

/**
 * Audio-language (dub) picker for the item currently playing, same shape as [CaptionSheet].
 * [tracks] is [PlayerState.availableAudioTracks], so it only offers languages the item published.
 * [selectedTrackId] is the track actually playing, shown as the checked row. [onSelect] gets the
 * track id that [PlaybackSession.selectAudioTrack] takes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackSheet(
    tracks: List<AudioTrackOption>,
    selectedTrackId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.padding(bottom = Spacing.s16)) {
            item {
                Text(
                    stringResource(R.string.player_audio_language),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.s16, vertical = Spacing.s8),
                )
            }
            items(tracks, key = { it.trackId }) { track ->
                ListItem(
                    headlineContent = { Text(track.label) },
                    leadingContent = {
                        RadioButton(selected = track.trackId == selectedTrackId, onClick = { onSelect(track.trackId) })
                    },
                    modifier = Modifier.clickable { onSelect(track.trackId) },
                )
            }
        }
    }
}
