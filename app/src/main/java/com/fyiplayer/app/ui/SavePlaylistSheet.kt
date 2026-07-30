package com.fyiplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val existing by playlists.observePlaylists().collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Add to playlist", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
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
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().clickable { showCreate = true }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("New playlist", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showCreate) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val name = text.trim()
                        scope.launch {
                            val id = runCatching { playlists.create(name) }
                                .onFailure { showToast(context, "Couldn't create -- that name may already be used") }
                                .getOrNull()
                            if (id != null) refs.forEach { playlists.addItem(id, it) }
                        }
                        showCreate = false
                        onDismiss()
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }
}
