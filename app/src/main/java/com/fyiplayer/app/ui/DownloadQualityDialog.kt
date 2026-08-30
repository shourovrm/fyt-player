package com.fyiplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.core.CaptionTrack
import com.fyiplayer.app.download.DownloadOption
import com.fyiplayer.app.download.DownloadQueue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
        is DownloadPickerState.Options -> {
            val context = LocalContext.current
            val queue = remember(context) { DownloadQueue.get(context) }
            // Not carried on DownloadPickerState itself -- reuses the queue's own last-resolve
            // cache (same one [DownloadQueue.fetchApproxBytes] reads) instead of a second resolve.
            val captions = remember(state) { queue.lastCaptions() }
            val prefLang by rememberFyiApp().prefs.contentLanguage.collectAsStateWithLifecycle(initialValue = "en")
            val defaultTrack = remember(state, prefLang, captions) { pickDefaultCaption(captions, prefLang) }
            var subtitlesChecked by remember(state) { mutableStateOf(true) }

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
                    "Download quality",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
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
                            modifier = Modifier.clickable {
                                val withSubtitle = if (subtitlesChecked && defaultTrack != null) {
                                    option.copy(subtitleLanguageCode = defaultTrack.languageCode)
                                } else {
                                    option
                                }
                                onSelect(withSubtitle)
                            },
                        )
                    }
                    if (defaultTrack != null) {
                        item(key = "subtitles") {
                            ListItem(
                                headlineContent = { Text("Also save subtitles (${defaultTrack.label})") },
                                leadingContent = {
                                    Checkbox(checked = subtitlesChecked, onCheckedChange = { subtitlesChecked = it })
                                },
                                modifier = Modifier.clickable { subtitlesChecked = !subtitlesChecked },
                            )
                        }
                    }
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

/** Preferred track: exact language match, then a regional variant of it (e.g. device "en" against
 *  a track tagged "en-US"), else whatever the platform listed first -- never nothing when at least
 *  one track exists, matching the "defaults on" checkbox behaviour the picker promises. */
private fun pickDefaultCaption(captions: List<CaptionTrack>, preferredLang: String): CaptionTrack? =
    captions.firstOrNull { it.languageCode == preferredLang }
        ?: captions.firstOrNull { it.languageCode.startsWith(preferredLang) }
        ?: captions.firstOrNull()
