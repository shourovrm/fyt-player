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
 * means "Auto" (the player picks at or under the configured ceiling); a height pins that
 * rendition via [PlaybackSession.selectQuality].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(
    heights: List<Int>,
    playingHeight: Int?,
    isAuto: Boolean,
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
                        headline = playingHeight?.let { stringResource(R.string.player_quality_downloaded_height, it) }
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
                    // On Auto the row also says what the player is on right now.
                    headline = if (isAuto && playingHeight != null) {
                        stringResource(R.string.player_quality_auto_height, playingHeight)
                    } else {
                        stringResource(R.string.player_quality_auto)
                    },
                    selected = isAuto,
                    onSelect = { onSelect(null) },
                )
            }
            items(heights) { height ->
                ChoiceListItem(
                    headline = stringResource(R.string.player_quality_height, height),
                    selected = !isAuto && height == playingHeight,
                    onSelect = { onSelect(height) },
                    headlineStyle = MaterialTheme.typography.bodyLarge.merge(NumeralStyle),
                )
            }
        }
    }
}
