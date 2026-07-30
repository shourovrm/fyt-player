package com.fyiplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.download.DownloadOption

/** What [VideoActionSheet]'s "Download" action is doing right now. Plain UI state, never a
 *  [com.fyiplayer.app.core.MediaFormat] -- [DownloadOption] already strips that down to a label,
 *  an approximate size and an opaque selector string. */
sealed class DownloadPickerState {
    object Resolving : DownloadPickerState()
    data class Options(val options: List<DownloadOption>) : DownloadPickerState()
    data class Error(val message: String) : DownloadPickerState()
}

/**
 * Shown every time "Download" is tapped -- the app never falls back to a stored resolution
 * preference for a download. The option list reuses [com.fyiplayer.app.player.QualitySheet]'s own
 * shape (a bottom sheet of [ListItem] rows) rather than a hand-rolled list; resolving and error are
 * plain [AlertDialog]s since there is nothing to list yet in either case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualityDialog(
    state: DownloadPickerState,
    onSelect: (DownloadOption) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is DownloadPickerState.Resolving -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Checking available sizes…") },
            text = {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        is DownloadPickerState.Options -> ModalBottomSheet(onDismissRequest = onDismiss) {
            Text(
                "Download quality",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                items(state.options, key = { it.formatId }) { option ->
                    val bytes = option.approxBytes
                    ListItem(
                        headlineContent = { Text(option.label) },
                        supportingContent = if (bytes != null) { { Text(formatBytes(bytes)) } } else null,
                        modifier = Modifier.clickable { onSelect(option) },
                    )
                }
            }
        }
        is DownloadPickerState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Can't download this video") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}
