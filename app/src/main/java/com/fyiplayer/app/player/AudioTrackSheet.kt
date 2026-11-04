package com.fyiplayer.app.player

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
            item { SheetTitleRow(stringResource(R.string.player_audio_language), onClose = onDismiss) }
            items(tracks, key = { it.trackId }) { track ->
                ChoiceListItem(
                    headline = track.label,
                    selected = track.trackId == selectedTrackId,
                    onSelect = { onSelect(track.trackId) },
                )
            }
        }
    }
}
