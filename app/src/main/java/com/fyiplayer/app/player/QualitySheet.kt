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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Resolution picker for the item currently playing. [heights] is
 * [PlaybackSession.state]'s `availableHeights` — derived by [PlaybackSession] from that item's
 * actual resolved formats, so this can never offer a height nothing here has. [onSelect] null
 * means "Auto" (the configured ceiling decides); a height forces that rendition via
 * [PlaybackSession.selectQuality].
 *
 * ponytail: "Auto" never shows as selected once a format is chosen — [PlaybackSession] doesn't
 * track whether the current pick came from the ceiling or a manual override, only the resulting
 * height, so the radio can't tell them apart. The picker still works correctly either way; add an
 * explicit override flag only if that highlight turns out to matter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(
    heights: List<Int>,
    selectedHeight: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
            item {
                ListItem(
                    headlineContent = { Text("Auto") },
                    leadingContent = { RadioButton(selected = selectedHeight == null, onClick = { onSelect(null) }) },
                    modifier = Modifier.clickable { onSelect(null) },
                )
            }
            items(heights) { height ->
                ListItem(
                    headlineContent = { Text("${height}p") },
                    leadingContent = { RadioButton(selected = height == selectedHeight, onClick = { onSelect(height) }) },
                    modifier = Modifier.clickable { onSelect(height) },
                )
            }
        }
    }
}
