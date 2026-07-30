package com.fyiplayer.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.LikesRepository
import com.fyiplayer.app.data.repo.PlaylistRepository
import com.fyiplayer.app.download.DownloadQueue
import com.fyiplayer.app.download.EnqueueOutcome
import com.fyiplayer.app.player.PlaybackSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Long-press action sheet shared by every result row. Queue actions call straight into
 * [PlaybackSession] -- the one player seam this layer may touch; everything else goes through the
 * matching repository. Opened once per user press, never per visible row, so the one-shot "is this
 * liked" read here is not the per-list-item I/O Rule 6 forbids.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionSheet(ref: VideoRef, onDismiss: () -> Unit) {
    val app = rememberFyiApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val likes = remember { LikesRepository(app.database.likeDao()) }
    val playlists = remember { PlaylistRepository(app.database.playlistDao(), app.database.playlistItemDao()) }
    val downloads = remember(context) { DownloadQueue.get(context) }

    var liked by remember(ref.pageUrl) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(ref.pageUrl) { liked = likes.observeIsLiked(ref.pageUrl).first() }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            SheetAction("Play next") { PlaybackSession.playNext(ref); onDismiss() }
            SheetAction("Add to queue") { PlaybackSession.enqueue(ref); onDismiss() }
            SheetAction("Add to playlist") { showPlaylistPicker = true }
            SheetAction(if (liked == true) "Unlike" else "Like") {
                scope.launch { if (liked == true) likes.unlike(ref.pageUrl) else likes.like(ref) }
                onDismiss()
            }
            SheetAction("Share") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    // Only the canonical page URL is ever put on the share sheet -- never a
                    // signed media URL (project rule: persist/share canonical page URLs only).
                    putExtra(Intent.EXTRA_TEXT, ref.pageUrl)
                    putExtra(Intent.EXTRA_TITLE, ref.title)
                }
                context.startActivity(Intent.createChooser(send, "Share link"))
                onDismiss()
            }
            SheetAction("Download") {
                scope.launch {
                    // Straight through the queue, never a hand-rolled row: it is what picks a
                    // playable video+audio pair (the highest single stream is often video-only,
                    // which downloads silently without sound) and what starts the service.
                    val outcome = downloads.enqueue(ref)
                    val message = when (outcome) {
                        is EnqueueOutcome.Queued -> "Download queued"
                        is EnqueueOutcome.Failed -> outcome.message
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }
        }
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            ref = ref,
            playlists = playlists,
            onDismiss = { showPlaylistPicker = false; onDismiss() },
        )
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** Existing playlists to add to, or create a new one inline. Read once when the dialog opens --
 *  not on the scroll path. */
@Composable
private fun PlaylistPickerDialog(ref: VideoRef, playlists: PlaylistRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val existing by playlists.observePlaylists().collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            // A plain Column, not LazyColumn: an unbounded-height lazy list inside a dialog's
            // wrap-content text slot throws (measured with an infinite max height), and a
            // playlist count here is small enough that virtualization buys nothing.
            Column {
                existing.forEach { playlist ->
                    Text(
                        playlist.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { playlists.addItem(playlist.id, ref) }
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                    )
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New playlist") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newName.isNotBlank()) {
                            scope.launch {
                                val id = playlists.create(newName.trim())
                                playlists.addItem(id, ref)
                            }
                            onDismiss()
                        }
                    }),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newName.isNotBlank()) {
                    scope.launch {
                        val id = playlists.create(newName.trim())
                        playlists.addItem(id, ref)
                    }
                }
                onDismiss()
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
