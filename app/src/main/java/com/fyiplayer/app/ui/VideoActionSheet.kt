package com.fyiplayer.app.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.LikesRepository
import com.fyiplayer.app.data.repo.PlaylistRepository
import com.fyiplayer.app.download.DownloadOption
import com.fyiplayer.app.download.DownloadQueue
import com.fyiplayer.app.download.EnqueueOutcome
import com.fyiplayer.app.download.ResolveOutcome
import com.fyiplayer.app.download.SubtitleOutcome
import com.fyiplayer.app.player.PlaybackSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Like/save/download/share state and handlers shared by the long-press sheet ([VideoActionSheet])
 * and the video-page action row (`ui/DetailScreen.kt`) -- one implementation so both surfaces
 * toggle the same repositories the same way. Owned by the caller's composition via
 * [rememberVideoActions], keyed on [ref]'s page URL.
 *
 * `appScope`, not the composition scope, backs every write: every caller here dismisses its own UI
 * (sheet close, dialog close) right on top of the write, and a composition-scoped launch would be
 * cancelled by that disposal before the download enqueue or like toggle actually lands.
 */
class VideoActions internal constructor(
    val ref: VideoRef,
    /** Continuous, not a one-shot read -- a toggle from the sheet must show up in the row (and
     *  vice versa) without leaving and re-entering the page. */
    val likedFlow: Flow<Boolean>,
    val playlists: PlaylistRepository,
    private val scope: CoroutineScope,
    private val likes: LikesRepository,
    private val downloads: DownloadQueue,
    private val context: Context,
) {
    var downloadPicker by mutableStateOf<DownloadPickerState?>(null)
        private set

    fun toggleLike(currentlyLiked: Boolean) {
        scope.launch { if (currentlyLiked) likes.unlike(ref.pageUrl) else likes.like(ref) }
    }

    fun share() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            // Only the canonical page URL is ever put on the share sheet -- never a signed media
            // URL (project rule: persist/share canonical page URLs only).
            putExtra(Intent.EXTRA_TEXT, ref.pageUrl)
            putExtra(Intent.EXTRA_TITLE, ref.title)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.detail_share_chooser)))
    }

    /** Every download asks which size, every time -- never a silent fall-back to the playback
     *  resolution preference. Resolving takes seconds, so this runs on [scope]: the caller's own
     *  dialog may still be showing when it lands, but if it isn't, nothing was written yet --
     *  resolving alone touches no row. */
    fun startDownload() {
        downloadPicker = DownloadPickerState.Resolving()
        scope.launch {
            val outcome = downloads.resolveOptions(ref) {
                downloadPicker = DownloadPickerState.Resolving(engineFallback = true)
            }
            downloadPicker = when (outcome) {
                is ResolveOutcome.Ready -> DownloadPickerState.Options(outcome.options)
                is ResolveOutcome.Failed -> DownloadPickerState.Error(outcome.message)
            }
        }
    }

    fun confirmDownload(option: DownloadOption, onMessage: (String) -> Unit) {
        scope.launch {
            val outcome = downloads.start(ref, option)
            onMessage(
                when (outcome) {
                    is EnqueueOutcome.Queued -> context.getString(R.string.detail_download_queued)
                    is EnqueueOutcome.Failed -> outcome.message
                },
            )
        }
        downloadPicker = null
    }

    fun dismissDownloadPicker() {
        downloadPicker = null
    }

    /** No picker (CLAUDE.md rule doesn't apply here -- this isn't a signed media URL choice, just
     *  one caption track): resolves, picks a track ([DownloadQueue.downloadSubtitles]), saves
     *  `.srt`, and reports the outcome as a toast. */
    fun downloadSubtitles() {
        scope.launch {
            val message = when (val outcome = downloads.downloadSubtitles(ref)) {
                SubtitleOutcome.Saved -> context.getString(R.string.detail_captions_saved)
                SubtitleOutcome.NoSubtitles -> context.getString(R.string.detail_captions_none)
                SubtitleOutcome.Unsupported -> context.getString(R.string.detail_captions_unsupported)
                is SubtitleOutcome.Failed -> outcome.message
            }
            showToast(context, message)
        }
    }
}

@Composable
fun rememberVideoActions(ref: VideoRef): VideoActions {
    val app = rememberFyiApp()
    val context = LocalContext.current
    val likes = remember { LikesRepository(app.database.likeDao()) }
    val playlists = remember { PlaylistRepository(app.database.playlistDao(), app.database.playlistItemDao()) }
    val downloads = remember(context) { DownloadQueue.get(context) }
    return remember(ref.pageUrl) {
        VideoActions(ref, likes.observeIsLiked(ref.pageUrl), playlists, app.appScope, likes, downloads, context)
    }
}

/** Long-press action sheet shared by every result row. Queue actions call straight into
 *  [PlaybackSession] -- the one player seam this layer may touch; everything else goes through
 *  [VideoActions], same as the video-page action row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionSheet(ref: VideoRef, onDismiss: () -> Unit) {
    val actions = rememberVideoActions(ref)
    val liked by actions.likedFlow.collectAsState(initial = false)
    var showPlaylistPicker by remember(ref.pageUrl) { mutableStateOf(false) }
    val context = LocalContext.current
    val addedToQueueMessage = stringResource(R.string.detail_added_to_queue)
    val alreadyInQueueMessage = stringResource(R.string.detail_already_in_queue)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Scrollable: in landscape the sheet is shorter than its items and Download fell off the end.
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.s16)) {
            SheetAction(stringResource(R.string.detail_play_next)) { PlaybackSession.playNext(ref); onDismiss() }
            SheetAction(stringResource(R.string.action_add_to_queue)) {
                showToast(context, if (PlaybackSession.enqueue(ref)) addedToQueueMessage else alreadyInQueueMessage)
                onDismiss()
            }
            SheetAction(stringResource(R.string.action_add_to_playlist)) { showPlaylistPicker = true }
            SheetAction(stringResource(if (liked) R.string.action_unlike else R.string.action_like)) { actions.toggleLike(liked); onDismiss() }
            SheetAction(stringResource(R.string.action_share)) { actions.share(); onDismiss() }
            SheetAction(stringResource(R.string.action_download)) { actions.startDownload() }
            SheetAction(stringResource(R.string.detail_download_captions)) { actions.downloadSubtitles(); onDismiss() }
        }
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            ref = ref,
            playlists = actions.playlists,
            onDismiss = { showPlaylistPicker = false; onDismiss() },
        )
    }

    actions.downloadPicker?.let { state ->
        DownloadQualityDialog(
            state = state,
            onSelect = { option: DownloadOption ->
                actions.confirmDownload(option) { message -> showToast(context, message) }
                onDismiss()
            },
            onDismiss = { actions.dismissDownloadPicker() },
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
 *  not on the scroll path. Not private: also used by the video-page action row's Save button
 *  (`ui/DetailScreen.kt`, same package). */
@Composable
fun PlaylistPickerDialog(ref: VideoRef, playlists: PlaylistRepository, onDismiss: () -> Unit) {
    // Process scope: picking a playlist dismisses this dialog, and disposal would cancel the
    // write before it lands.
    val scope = rememberFyiApp().appScope
    val existing by playlists.observePlaylists().collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_to_playlist)) },
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
                            .heightIn(min = Sizes.touchTarget)
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.playlist_new)) },
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
                    modifier = Modifier.padding(top = Spacing.s8),
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
            }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
