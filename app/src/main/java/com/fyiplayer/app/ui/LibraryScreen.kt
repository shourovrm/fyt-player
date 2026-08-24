package com.fyiplayer.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.PlaybackPosition
import com.fyiplayer.app.data.repo.SubscriptionRow
import com.fyiplayer.app.player.PlaybackSession
import com.fyiplayer.app.ui.theme.fyiExtras
import kotlinx.coroutines.launch

/**
 * Likes / Playlists / History (DESIGN.md §5, §7). Tab + selection live in [LibraryViewModel] so
 * they survive tab switches, a trip to Detail/PlaylistDetail and back, and rotation. Multi-select
 * only exists on Likes -- Playlists has its own CRUD, and History's bulk action is "clear" outright.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenDetail: (String) -> Unit, onOpenPlaylist: (String) -> Unit, onOpenListing: (Listing) -> Unit) {
    val vm: LibraryViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val selecting = vm.selection.isNotEmpty()
    BackHandler(enabled = selecting) { vm.clearSelection() }

    val liked by vm.likes.observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val positions by vm.positions.observeAll().collectAsStateWithLifecycle(initialValue = emptyMap())
    val channels by vm.subscriptions.observeAllRows().collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showUnsubscribeConfirm by remember { mutableStateOf(false) }
    var showUnfollowConfirm by remember { mutableStateOf(false) }

    val openDetail: (VideoRef) -> Unit = { ref -> RefCache.put(ref); onOpenDetail(ref.pageUrl) }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = vm.selection.size,
                    onClose = vm::clearSelection,
                    onSelectAll = {
                        vm.selection = when (vm.tab) {
                            LibraryTab.LIKES -> selectAllOrNone(liked, vm.selection)
                            LibraryTab.CHANNELS -> selectAllOrNoneByKey(channels.map { it.listing.key }, vm.selection)
                            LibraryTab.PLAYLISTS, LibraryTab.HISTORY -> vm.selection
                        }
                    },
                    actions = {
                        when (vm.tab) {
                            LibraryTab.LIKES -> {
                                var menuOpen by remember { mutableStateOf(false) }
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Add to queue") },
                                        onClick = {
                                            menuOpen = false
                                            selectedInOrder(liked, vm.selection).forEach { PlaybackSession.enqueue(it) }
                                            vm.clearSelection()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Add to playlist") },
                                        onClick = { menuOpen = false; showAddToPlaylist = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Remove from likes") },
                                        onClick = {
                                            menuOpen = false
                                            val urls = vm.selection
                                            vm.clearSelection()
                                            scope.launch { urls.forEach { vm.likes.unlike(it) } }
                                        },
                                    )
                                }
                            }
                            LibraryTab.CHANNELS -> IconButton(onClick = { showUnsubscribeConfirm = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Unsubscribe")
                            }
                            LibraryTab.PLAYLISTS -> IconButton(onClick = { showUnfollowConfirm = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                            LibraryTab.HISTORY -> {}
                        }
                    },
                )
            } else {
                TopAppBar(title = { Text("Library") })
            }
        },
        floatingActionButton = {
            if (selecting && vm.tab == LibraryTab.LIKES) {
                PlaySelectionFab(vm.selection.size) {
                    val refs = selectedInOrder(liked, vm.selection)
                    if (refs.isNotEmpty()) PlaybackSession.play(refs, 0)
                    vm.clearSelection()
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = vm.tab.ordinal) {
                Tab(selected = vm.tab == LibraryTab.LIKES, onClick = { vm.selectTab(LibraryTab.LIKES) }, text = { Text("Likes") })
                Tab(selected = vm.tab == LibraryTab.PLAYLISTS, onClick = { vm.selectTab(LibraryTab.PLAYLISTS) }, text = { Text("Playlists") })
                Tab(selected = vm.tab == LibraryTab.HISTORY, onClick = { vm.selectTab(LibraryTab.HISTORY) }, text = { Text("History") })
                Tab(selected = vm.tab == LibraryTab.CHANNELS, onClick = { vm.selectTab(LibraryTab.CHANNELS) }, text = { Text("Channels") })
            }
            when (vm.tab) {
                LibraryTab.LIKES -> LikesTab(
                    videos = liked,
                    positions = positions,
                    selection = vm.selection,
                    selecting = selecting,
                    onToggle = vm::toggle,
                    onOpen = openDetail,
                    onUnlike = { url -> scope.launch { vm.likes.unlike(url) } },
                )
                LibraryTab.PLAYLISTS -> PlaylistsTab(
                    vm = vm,
                    onOpenPlaylist = onOpenPlaylist,
                    onOpenListing = onOpenListing,
                    selection = vm.selection,
                    selecting = selecting,
                    onToggle = vm::toggleKey,
                )
                LibraryTab.HISTORY -> HistoryTab(vm, onOpen = openDetail)
                LibraryTab.CHANNELS -> ChannelsTab(
                    channels = channels,
                    selection = vm.selection,
                    selecting = selecting,
                    onToggle = vm::toggleKey,
                    onOpen = onOpenListing,
                    onToggleShowInFeed = { row ->
                        scope.launch { vm.subscriptions.setShowInFeed(row.listing.key, !row.showInFeed) }
                    },
                )
            }
        }
    }

    if (showAddToPlaylist) {
        SavePlaylistSheet(
            refs = selectedInOrder(liked, vm.selection),
            playlists = vm.playlists,
            onDismiss = { showAddToPlaylist = false; vm.clearSelection() },
        )
    }
    if (showUnsubscribeConfirm) {
        val count = vm.selection.size
        ConfirmDialog(
            title = "Unsubscribe from $count channel${if (count == 1) "" else "s"}?",
            confirmLabel = "Unsubscribe",
            onConfirm = {
                val urls = vm.selection
                vm.clearSelection()
                showUnsubscribeConfirm = false
                scope.launch { urls.forEach { vm.subscriptions.unsubscribe(it) } }
            },
            onDismiss = { showUnsubscribeConfirm = false },
        )
    }
    if (showUnfollowConfirm) {
        val count = vm.selection.size
        ConfirmDialog(
            title = "Remove $count followed playlist${if (count == 1) "" else "s"}?",
            confirmLabel = "Remove",
            onConfirm = {
                val urls = vm.selection
                vm.clearSelection()
                showUnfollowConfirm = false
                scope.launch { urls.forEach { vm.followedPlaylists.unfollow(it) } }
            },
            onDismiss = { showUnfollowConfirm = false },
        )
    }
}

/** One destructive yes/no prompt, shared by Unsubscribe and Remove-followed: the destructive
 *  choice is error-tinted text, not the filled/primary button, so it never reads as the default
 *  -- same non-default-destructive convention as DownloadsScreen's `RemoveConfirmDialog`. */
@Composable
private fun ConfirmDialog(title: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {},
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LikesTab(
    videos: List<VideoRef>,
    positions: Map<String, PlaybackPosition>,
    selection: Set<String>,
    selecting: Boolean,
    onToggle: (VideoRef) -> Unit,
    onOpen: (VideoRef) -> Unit,
    onUnlike: (String) -> Unit,
) {
    if (videos.isEmpty()) {
        LibraryEmptyState("No likes yet", "")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (selecting) 88.dp else 0.dp)) {
        items(videos, key = { it.pageUrl }) { ref ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (ref.pageUrl in selection) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                ) {
                    Column {
                        ResultRow(ref, onClick = { if (selecting) onToggle(ref) else onOpen(ref) }, onLongPress = { onToggle(ref) })
                        ResumeBar(positions[ref.pageUrl])
                    }
                }
                // Single-row unlike yields while selecting -- the top bar owns bulk remove.
                if (!selecting) {
                    IconButton(onClick = { onUnlike(ref.pageUrl) }) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Unlike", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** Thin resume indicator under a row, drawn from the one [com.fyiplayer.app.data.repo.PositionsRepository.observeAll]
 *  map the screen already collected -- never a per-row position query (rule 6). Hidden near 0% and
 *  near 100%: those aren't "in progress", they're "not started" and "finished". */
@Composable
private fun ResumeBar(position: PlaybackPosition?) {
    val fraction = position?.takeIf { it.durationMs > 0 }?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
    if (fraction != null && fraction in 0.02f..0.96f) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp),
        )
    }
}

@Composable
private fun PlaylistsTab(
    vm: LibraryViewModel,
    onOpenPlaylist: (String) -> Unit,
    onOpenListing: (Listing) -> Unit,
    selection: Set<String>,
    selecting: Boolean,
    onToggle: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rows by vm.playlistRows.collectAsStateWithLifecycle(initialValue = null)
    var showCreate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().clickable { showCreate = true }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("New playlist", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 14.dp))
        }
        HorizontalDivider()
        val list = rows
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> LibraryEmptyState("No playlists yet", "Create one above, or follow one from a playlist page.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.key }) { row ->
                    when (row) {
                        is PlaylistRow.Local -> PlaylistCardRow(
                            card = row.card,
                            onClick = { onOpenPlaylist(row.card.id.toString()) },
                            onRename = { name ->
                                scope.launch {
                                    runCatching { vm.playlists.rename(row.card.id, name) }
                                        .onFailure { showToast(context, "Couldn't rename -- that name may already be used") }
                                }
                            },
                            onDelete = { scope.launch { vm.playlists.delete(row.card.id) } },
                        )
                        is PlaylistRow.Followed -> FollowedPlaylistRow(
                            listing = row.listing,
                            selected = row.listing.key in selection,
                            onClick = { if (selecting) onToggle(row.listing.key) else onOpenListing(row.listing) },
                            onLongPress = { onToggle(row.listing.key) },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        NamePromptDialog(
            title = "New playlist",
            onConfirm = { name ->
                scope.launch {
                    runCatching { vm.playlists.create(name) }
                        .onFailure { showToast(context, "Couldn't create -- that name may already be used") }
                }
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun PlaylistCardRow(card: PlaylistCard, onClick: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (card.coverThumbnailUrl != null) {
                AsyncImage(model = card.coverThumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${card.itemCount} videos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renaming = true })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
    if (renaming) {
        NamePromptDialog(title = "Rename playlist", initial = card.name, onConfirm = { onRename(it); renaming = false }, onDismiss = { renaming = false })
    }
}

/** A followed remote playlist: same row rhythm as [PlaylistCardRow] but no cover (a listing's
 *  thumbnail is a signed URL -- rule 2 forbids keeping one on hand to draw from), and a "followed"
 *  label instead of a video count since that would mean re-fetching the listing per row (rule 6). */
@Composable
private fun FollowedPlaylistRow(listing: Listing, selected: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonogramDisc(listing.title, size = 44.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(listing.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Followed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.fyiExtras.ink3,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun ChannelsTab(
    channels: List<SubscriptionRow>,
    selection: Set<String>,
    selecting: Boolean,
    onToggle: (String) -> Unit,
    onOpen: (Listing) -> Unit,
    onToggleShowInFeed: (SubscriptionRow) -> Unit,
) {
    if (channels.isEmpty()) {
        LibraryEmptyState("No subscriptions yet", "Subscribe from a channel page and it lands here.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(channels, key = { it.listing.key }) { row ->
            val channel = row.listing
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (channel.key in selection) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .combinedClickable(
                        onClick = { if (selecting) onToggle(channel.key) else onOpen(channel) },
                        onLongClick = { onToggle(channel.key) },
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mockup: no banner, no toast, no subtitle -- a dim row + the eye glyph itself say
                // whether a subscribed channel is feeding Home/Shorts.
                Row(
                    Modifier.weight(1f).alpha(if (row.showInFeed) 1f else 0.4f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonogramDisc(channel.title, size = 44.dp)
                    Text(
                        channel.title.ifBlank { channel.key },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                }
                // The glyph itself stays full-opacity even when the row dims -- it's the tap target
                // that un-hides the channel, so it needs to read clearly either way.
                IconButton(onClick = { onToggleShowInFeed(row) }) {
                    EyeGlyph(slashed = !row.showInFeed, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** `material-icons-core` has no Visibility/VisibilityOff glyph (CLAUDE.md's own gotcha, checked
 *  against this build's actual jar -- only ~50 icons ship in core, eye glyphs aren't among them).
 *  Drawn by hand the same way [DetailScreen.kt]'s SaveGlyph/DownloadTrayGlyph solve the same gap. */
@Composable
private fun EyeGlyph(tint: Color, slashed: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f
        val outline = Path().apply {
            moveTo(w * 0.04f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.06f, w * 0.96f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.94f, w * 0.04f, h * 0.5f)
            close()
        }
        drawPath(outline, tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(tint, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.5f))
        if (slashed) {
            drawLine(tint, Offset(w * 0.08f, h * 0.14f), Offset(w * 0.92f, h * 0.86f), stroke, cap = StrokeCap.Round)
        }
    }
}

/** [Listing.thumbnailUrl] for a subscribed channel would be a signed avatar URL -- never something
 *  to persist or hold in the subscriptions table (rule: canonical page URLs only), so every row
 *  falls back to a plain initial disc rather than fetching one live per row (rule 6, no I/O per
 *  visible list item). */
@Composable
private fun MonogramDisc(title: String, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.trim().take(1).uppercase().ifBlank { "?" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryTab(vm: LibraryViewModel, onOpen: (VideoRef) -> Unit) {
    val scope = rememberCoroutineScope()
    val recording by vm.prefs.recordWatchHistory.collectAsStateWithLifecycle(initialValue = true)
    val entries by vm.history.observe().collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize()) {
        if (!recording) {
            Text(
                "History recording is off in Settings. These are past entries -- new views won't be added.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        val list = entries
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> LibraryEmptyState(
                "No watch history",
                if (recording) "Videos you open land here." else "History recording is off in Settings.",
            )
            else -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { scope.launch { vm.history.clear() } }) { Text("Clear history") }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.pageUrl }) { ref -> ResultRow(ref, onClick = { onOpen(ref) }, onLongPress = {}) }
                }
            }
        }
    }
}

@Composable
internal fun LibraryEmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body.isNotEmpty()) Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** One inline name prompt, reused by "New playlist" and "Rename" here and by [PlaylistDetailScreen]. */
@Composable
internal fun NamePromptDialog(title: String, initial: String = "", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Always hops to the main looper instead of trusting the call site. Several callers report the
 * result of a write launched on the process-wide scope, which runs on [kotlinx.coroutines
 * .Dispatchers.Default] — and `Toast` throws on a thread with no Looper.
 */
internal fun showToast(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
