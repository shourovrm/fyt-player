package com.fyiplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.download.DownloadOption
import com.fyiplayer.app.download.DownloadQueue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** What [VideoActionSheet]'s "Download" action is doing right now. Plain UI state, never a
 *  [com.fyiplayer.app.core.MediaFormat] -- [DownloadOption] already strips that down to a label,
 *  an approximate size and an opaque selector string. */
sealed class DownloadPickerState {
    /** [engineFallback] is true while the slower engine lookup runs (17-24 s); the first lookup
     *  came back with no downloadable track, so the wait needs a reason on screen. */
    data class Resolving(val engineFallback: Boolean = false) : DownloadPickerState()
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
            title = { Text(stringResource(R.string.detail_download_checking)) },
            text = {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    if (state.engineFallback) {
                        Text(
                            stringResource(R.string.detail_download_checking_more),
                            modifier = Modifier.padding(top = Spacing.s8),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        is DownloadPickerState.Options -> {
            val context = LocalContext.current
            val queue = remember(context) { DownloadQueue.get(context) }

            // Sizes the dialog fetches itself, one HEAD probe per option that had none up front --
            // "…" until each resolves, blank if the probe itself came back empty.
            val sizes = remember(state) { mutableStateMapOf<String, Long?>() }
            LaunchedEffect(state) {
                coroutineScope {
                    state.options.filter { it.approxBytes == null }.forEach { option ->
                        launch { sizes[option.formatId] = queue.fetchApproxBytes(option) }
                    }
                }
            }

            ModalBottomSheet(onDismissRequest = onDismiss) {
                Text(
                    stringResource(R.string.detail_download_quality),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
                )
                LazyColumn(modifier = Modifier.padding(bottom = Spacing.s16)) {
                    items(state.options, key = { it.formatId }) { option ->
                        val bytes = option.approxBytes ?: sizes[option.formatId]
                        val stillFetching = option.approxBytes == null && !sizes.containsKey(option.formatId)
                        ListItem(
                            headlineContent = { Text(option.label) },
                            supportingContent = when {
                                bytes != null -> { { Text(formatBytes(bytes)) } }
                                stillFetching -> { { Text("…") } }
                                else -> null
                            },
                            modifier = Modifier.clickable { onSelect(option) },
                        )
                    }
                }
            }
        }
        is DownloadPickerState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.detail_download_failed_title)) },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
