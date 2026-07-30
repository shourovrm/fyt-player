package com.fyiplayer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.PlaylistRepository
import com.fyiplayer.app.player.PlaybackSession
import kotlinx.coroutines.launch

/**
 * One playlist opened from the Playlists tab: header (count · play all · shuffle all · select),
 * per-row up/down reorder, and multi-select for a bulk remove. [id] is the decoded playlist id
 * from the `playlist/{id}` route -- Room's id is a Long, the route only carries strings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(id: String, onOpenDetail: (String) -> Unit) {
    val playlistId = remember(id) { id.toLongOrNull() }
    if (playlistId == null) {
        LibraryEmptyState("Playlist not found", "No playlist id $id.")
        return
    }

    val app = rememberFyiApp()
    val playlists = remember { PlaylistRepository(app.database.playlistDao(), app.database.playlistItemDao()) }
    val scope = rememberCoroutineScope()

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
                            Icon(Icons.Filled.Delete, contentDescription = "Remove from playlist", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            } else {
                // No back arrow: the fixed route signature carries no `onBack` callback to wire
                // one to, and a tappable icon that does nothing would be worse than none. System
                // back/gesture already pops this destination via Navigation-Compose's own handling.
                TopAppBar(
                    title = { Text(name ?: "Playlist", maxLines = 1) },
                    actions = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = { menuOpen = false; renaming = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete playlist") },
                                    onClick = { menuOpen = false; scope.launch { playlists.delete(playlistId) } },
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
                    if (refs.isNotEmpty()) PlaybackSession.play(refs, 0)
                    selection = emptySet()
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!selecting && videos.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${videos.size} videos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { PlaybackSession.play(videos, 0) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Play all") }
                    OutlinedButton(
                        onClick = { PlaybackSession.play(videos.shuffled(), 0) },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Shuffle") }
                }
            }
            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                videos.isEmpty() -> LibraryEmptyState("No videos in this playlist yet", "Add one from a video's long-press menu.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (selecting) 88.dp else 0.dp)) {
                    itemsIndexed(videos, key = { _, ref -> ref.pageUrl }) { index, ref ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .background(if (ref.pageUrl in selection) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                            ) {
                                ResultRow(
                                    ref,
                                    onClick = {
                                        if (selecting) selection = selection.toggled(ref.pageUrl)
                                        else { RefCache.put(ref); onOpenDetail(ref.pageUrl) }
                                    },
                                    onLongPress = { selection = selection.toggled(ref.pageUrl) },
                                )
                            }
                            if (!selecting) {
                                Column {
                                    IconButton(
                                        onClick = { scope.launch { reorder(playlists, playlistId, videos, index, -1) } },
                                        enabled = index > 0,
                                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                                    IconButton(
                                        onClick = { scope.launch { reorder(playlists, playlistId, videos, index, 1) } },
                                        enabled = index < videos.lastIndex,
                                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
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
            title = "Rename playlist",
            initial = name.orEmpty(),
            onConfirm = { newName ->
                scope.launch { runCatching { playlists.rename(playlistId, newName) }.onSuccess { name = newName } }
                renaming = false
            },
            onDismiss = { renaming = false },
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
