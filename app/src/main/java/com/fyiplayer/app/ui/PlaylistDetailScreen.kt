package com.fyiplayer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.PlaylistRepository
import com.fyiplayer.app.player.PlaybackSession
import kotlinx.coroutines.launch

/**
 * One playlist opened from the Playlists tab: header (count · play all · shuffle all · select),
 * per-row up/down reorder, and multi-select for a bulk remove. [id] is the decoded playlist id
 * from the `playlist/{id}` route -- Room's id is a Long, the route only carries strings.
 * [onDeleted] runs after a confirmed delete: the playlist is gone, so the caller pops this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(id: String, onOpenDetail: (String) -> Unit, onDeleted: () -> Unit = {}) {
    val playlistId = remember(id) { id.toLongOrNull() }
    if (playlistId == null) {
        LibraryEmptyState(stringResource(R.string.playlist_not_found))
        return
    }

    val app = rememberFyiApp()
    val context = LocalContext.current
    val playlists = remember { PlaylistRepository(app.database.playlistDao(), app.database.playlistItemDao()) }
    val scope = rememberCoroutineScope()
    val defaultTitle = stringResource(R.string.library_playlist_default_title)
    val renameFailedMessage = stringResource(R.string.library_rename_failed)

    // One-shot fetch: the route carries only the id, so unlike the other screens' nav args this
    // title isn't already on hand. Kept up to date locally after a rename instead of re-querying.
    var name by remember(playlistId) { mutableStateOf<String?>(null) }
    LaunchedEffect(playlistId) { name = app.database.playlistDao().get(playlistId)?.name }

    val items by playlists.observeItems(playlistId).collectAsStateWithLifecycle(initialValue = null)
    val videos = items.orEmpty()
    var selection by remember(playlistId) { mutableStateOf(emptySet<String>()) }
    val selecting = selection.isNotEmpty()
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = selecting) { selection = emptySet() }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selection.size,
                    onClose = { selection = emptySet() },
                    onSelectAll = { selection = selectAllOrNone(videos, selection) },
                    actions = {
                        IconButton(onClick = {
                            val urls = selection
                            selection = emptySet()
                            scope.launch { urls.forEach { playlists.removeItem(playlistId, it) } }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.playlist_remove_item), tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            } else {
                // No back arrow: the fixed route signature carries no `onBack` callback to wire
                // one to, and a tappable icon that does nothing would be worse than none. System
                // back/gesture already pops this destination via Navigation-Compose's own handling.
                TopAppBar(
                    title = { Text(name ?: defaultTitle, maxLines = 1) },
                    actions = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    // No remote URL for a locally created playlist -- share its
                                    // title plus one canonical video page URL per line instead.
                                    text = { Text(stringResource(R.string.action_share)) },
                                    onClick = { menuOpen = false; sharePlaylist(context, name ?: defaultTitle, pageUrl = null, videoUrls = videos.map { it.pageUrl }) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_rename)) },
                                    onClick = { menuOpen = false; renaming = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_delete_playlist_title)) },
                                    onClick = { menuOpen = false; confirmingDelete = true },
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selecting) {
                PlaySelectionFab(selection.size) {
                    val refs = selectedInOrder(videos, selection)
                    if (refs.isNotEmpty()) PlaybackSession.play(refs, 0, queueTitle = name ?: defaultTitle)
                    selection = emptySet()
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!selecting && videos.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        pluralStringResource(R.plurals.library_video_count, videos.size, videos.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { PlaybackSession.play(videos, 0, queueTitle = name ?: defaultTitle) }, contentPadding = PaddingValues(horizontal = Spacing.s16)) { Text(stringResource(R.string.playlist_play_all)) }
                    OutlinedButton(
                        onClick = { PlaybackSession.play(videos.shuffled(), 0, queueTitle = name ?: defaultTitle) },
                        contentPadding = PaddingValues(horizontal = Spacing.s16),
                        modifier = Modifier.padding(start = Spacing.s8),
                    ) { Text(stringResource(R.string.playlist_shuffle)) }
                }
            }
            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                videos.isEmpty() -> LibraryEmptyState(stringResource(R.string.playlist_empty))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (selecting) Sizes.fab + Spacing.s32 else 0.dp)) {
                    itemsIndexed(videos, key = { _, ref -> ref.pageUrl }) { index, ref ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                ResultRow(
                                    ref,
                                    onClick = {
                                        if (selecting) selection = selection.toggled(ref.pageUrl)
                                        else { RefCache.put(ref); onOpenDetail(ref.pageUrl) }
                                    },
                                    onLongPress = { selection = selection.toggled(ref.pageUrl) },
                                    selected = ref.pageUrl in selection,
                                    selecting = selecting,
                                )
                            }
                            if (!selecting) {
                                Column {
                                    IconButton(
                                        onClick = { scope.launch { reorder(playlists, playlistId, videos, index, -1) } },
                                        enabled = index > 0,
                                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.playlist_move_up)) }
                                    IconButton(
                                        onClick = { scope.launch { reorder(playlists, playlistId, videos, index, 1) } },
                                        enabled = index < videos.lastIndex,
                                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.playlist_move_down)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        NamePromptDialog(
            title = stringResource(R.string.library_rename_title),
            initial = name.orEmpty(),
            onConfirm = { newName ->
                scope.launch {
                    runCatching { playlists.rename(playlistId, newName) }
                        .onSuccess { name = newName }
                        .onFailure { showToast(context, renameFailedMessage) }
                }
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.library_delete_playlist_title),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmingDelete = false
                scope.launch {
                    playlists.delete(playlistId)
                    onDeleted()
                }
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * Moves the item at [from] by [delta] (-1 up, +1 down) and persists the new order. There's no
 * per-item sortIndex update in [PlaylistRepository]/its DAO -- `addItem` always appends at
 * `maxSortIndex + 1` -- so a reorder removes and re-adds every item in the new order. That
 * rewrites `addedAt` for the whole list, which is harmless: nothing surfaces it in the UI.
 */
private suspend fun reorder(playlists: PlaylistRepository, playlistId: Long, current: List<VideoRef>, from: Int, delta: Int) {
    val next = moved(current, from, delta)
    if (next === current) return
    playlists.reorder(playlistId, next.map { it.pageUrl })
}

/** Pure reorder maths, split out so it's unit-testable without Room or a coroutine. */
internal fun <T> moved(items: List<T>, from: Int, delta: Int): List<T> {
    val to = from + delta
    if (from !in items.indices || to !in items.indices) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
}
