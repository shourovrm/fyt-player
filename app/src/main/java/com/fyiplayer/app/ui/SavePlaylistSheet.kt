package com.fyiplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.PlaylistRepository
import kotlinx.coroutines.launch

/**
 * "Add to playlist" bottom sheet: one row per existing playlist, tap adds every ref in [refs] to
 * it, plus an inline "New playlist" row. Takes a list rather than one [VideoRef] so the Library
 * multi-select batch action and a single-video call site share one sheet -- a single-item list
 * covers the one-video case.
 *
 * [ui/VideoActionSheet.kt] already ships its own working "Add to playlist" dialog (a plain
 * [AlertDialog], not this sheet) and isn't touched here -- see the handoff report for why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavePlaylistSheet(refs: List<VideoRef>, playlists: PlaylistRepository, onDismiss: () -> Unit) {
    // Process scope, not rememberCoroutineScope: every action here dismisses the sheet, and
    // disposal cancels a composition scope mid-write -- a multi-second download enqueue never
    // survived to write its row.
    val scope = rememberFyiApp().appScope
    val context = LocalContext.current
    val existing by playlists.observePlaylists().collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreate by remember { mutableStateOf(false) }
    val createFailedMessage = stringResource(R.string.playlist_create_failed)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.action_add_to_playlist),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = 4.dp),
        )
        Column {
            existing.forEach { playlist ->
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { refs.forEach { playlists.addItem(playlist.id, it) } }
                            onDismiss()
                        }
                        .heightIn(min = Sizes.touchTarget)
                        .padding(horizontal = Spacing.pageMargin)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
            Row(
                Modifier.fillMaxWidth().clickable { showCreate = true }.heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.pageMargin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.playlist_new), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Spacing.s16), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (showCreate) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.playlist_new)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val name = text.trim()
                        scope.launch {
                            val id = runCatching { playlists.create(name) }
                                .onFailure { showToast(context, createFailedMessage) }
                                .getOrNull()
                            if (id != null) refs.forEach { playlists.addItem(id, it) }
                        }
                        showCreate = false
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.playlist_create)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
