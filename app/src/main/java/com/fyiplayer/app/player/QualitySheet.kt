package com.fyiplayer.app.player

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.NumeralStyle
import com.fyiplayer.app.ui.theme.Spacing

/**
 * Resolution picker for the item currently playing. [heights] is
 * [PlaybackSession.state]'s `availableHeights` — derived by [PlaybackSession] from that item's
 * actual resolved formats, so this can never offer a height nothing here has. [onSelect] null
 * means "Auto" (the configured ceiling decides); a height forces that rendition via
 * [PlaybackSession.selectQuality].
 *
 * ponytail: "Auto" never shows as selected once a format is chosen — [PlaybackSession] doesn't
 * track whether the current pick came from the ceiling or a manual override, only the resulting
 * height, so the check mark can't tell them apart. The picker still works correctly either way;
 * add an explicit override flag only if that highlight turns out to matter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(
    heights: List<Int>,
    selectedHeight: Int?,
    playingDownload: Boolean,
    onSelect: (Int?) -> Unit,
    onStream: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.padding(bottom = Spacing.s16)) {
            item { SheetTitleRow(stringResource(R.string.player_menu_quality), onClose = onDismiss) }
            if (playingDownload) {
                // A download has one rendition; the only other choice is streaming the video.
                item {
                    ChoiceListItem(
                        headline = selectedHeight?.let { stringResource(R.string.player_quality_downloaded_height, it) }
                            ?: stringResource(R.string.player_quality_downloaded),
                        selected = true,
                        onSelect = onDismiss,
                    )
                }
                item {
                    ChoiceListItem(
                        headline = stringResource(R.string.player_quality_stream),
                        selected = false,
                        onSelect = onStream,
                    )
                }
                return@LazyColumn
            }
            item {
                ChoiceListItem(
                    headline = stringResource(R.string.player_quality_auto),
                    selected = selectedHeight == null,
                    onSelect = { onSelect(null) },
                )
            }
            items(heights) { height ->
                ChoiceListItem(
                    headline = stringResource(R.string.player_quality_height, height),
                    selected = height == selectedHeight,
                    onSelect = { onSelect(height) },
                    headlineStyle = MaterialTheme.typography.bodyLarge.merge(NumeralStyle),
                )
            }
        }
    }
}
