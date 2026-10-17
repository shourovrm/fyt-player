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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fyiplayer.app.R
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.SubscriptionRow
import com.fyiplayer.app.player.PlaybackSession
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import kotlinx.coroutines.flow.first
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
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_add_to_queue)) },
                                        onClick = {
                                            menuOpen = false
                                            selectedInOrder(liked, vm.selection).forEach { PlaybackSession.enqueue(it) }
                                            vm.clearSelection()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_add_to_playlist)) },
                                        onClick = { menuOpen = false; showAddToPlaylist = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_unlike)) },
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
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_unsubscribe))
                            }
                            LibraryTab.PLAYLISTS -> IconButton(onClick = { showUnfollowConfirm = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove))
                            }
                            LibraryTab.HISTORY -> {}
                        }
                    },
                )
            } else {
                TopAppBar(title = { Text(stringResource(R.string.library_title)) })
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
                Tab(selected = vm.tab == LibraryTab.LIKES, onClick = { vm.selectTab(LibraryTab.LIKES) }, text = { Text(stringResource(R.string.library_tab_likes)) })
                Tab(selected = vm.tab == LibraryTab.PLAYLISTS, onClick = { vm.selectTab(LibraryTab.PLAYLISTS) }, text = { Text(stringResource(R.string.library_tab_playlists)) })
                Tab(selected = vm.tab == LibraryTab.HISTORY, onClick = { vm.selectTab(LibraryTab.HISTORY) }, text = { Text(stringResource(R.string.library_tab_history)) })
                Tab(selected = vm.tab == LibraryTab.CHANNELS, onClick = { vm.selectTab(LibraryTab.CHANNELS) }, text = { Text(stringResource(R.string.library_tab_channels)) })
            }
            when (vm.tab) {
                LibraryTab.LIKES -> LikesTab(
                    videos = liked,
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
            title = pluralStringResource(R.plurals.library_unsubscribe_title, count, count),
            body = pluralStringResource(R.plurals.library_unsubscribe_body, count),
            confirmLabel = stringResource(R.string.action_unsubscribe),
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
            title = pluralStringResource(R.plurals.library_unfollow_title, count, count),
            body = pluralStringResource(R.plurals.library_unfollow_body, count),
            confirmLabel = stringResource(R.string.library_unfollow),
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

/** One destructive yes/no prompt, shared by Unsubscribe, Remove-followed, playlist delete and the
 *  history clears: the destructive
 *  choice is error-tinted text, not the filled/primary button, so it never reads as the default
 *  -- same non-default-destructive convention as DownloadsScreen's `RemoveConfirmDialog`. */
@Composable
internal fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String = "",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { if (body.isNotEmpty()) Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun LikesTab(
    videos: List<VideoRef>,
    selection: Set<String>,
    selecting: Boolean,
    onToggle: (VideoRef) -> Unit,
    onOpen: (VideoRef) -> Unit,
    onUnlike: (String) -> Unit,
) {
    if (videos.isEmpty()) {
        LibraryEmptyState(stringResource(R.string.library_likes_empty), "")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (selecting) Sizes.fab + Spacing.s32 else 0.dp)) {
        items(videos, key = { it.pageUrl }) { ref ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    // Resume bar now draws inside ResultRow itself (LocalPlaybackPositions,
                    // provided once in AppShell) -- every list gets it, not just Likes. The
                    // selection highlight is ResultRow's too, shared with Channel/Listing.
                    ResultRow(
                        ref,
                        onClick = { if (selecting) onToggle(ref) else onOpen(ref) },
                        onLongPress = { onToggle(ref) },
                        selected = ref.pageUrl in selection,
                        selecting = selecting,
                    )
                }
                // Single-row unlike yields while selecting -- the top bar owns bulk remove.
                if (!selecting) {
                    IconButton(onClick = { onUnlike(ref.pageUrl) }) {
                        Icon(Icons.Filled.Favorite, contentDescription = stringResource(R.string.action_unlike), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
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
    val renameFailedMessage = stringResource(R.string.library_rename_failed)
    val createFailedMessage = stringResource(R.string.playlist_create_failed)
    val defaultPlaylistTitle = stringResource(R.string.library_playlist_default_title)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().clickable { showCreate = true }
                .heightIn(min = Sizes.listOneLine).padding(horizontal = Spacing.pageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.playlist_new), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Spacing.s16))
        }
        HorizontalDivider()
        val list = rows
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> LibraryEmptyState(stringResource(R.string.library_playlists_empty), stringResource(R.string.library_playlists_empty_hint))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.key }) { row ->
                    when (row) {
                        is PlaylistRow.Local -> LocalPlaylistRow(
                            card = row.card,
                            onClick = { onOpenPlaylist(row.card.id.toString()) },
                            onRename = { name ->
                                scope.launch {
                                    runCatching { vm.playlists.rename(row.card.id, name) }
                                        .onFailure { showToast(context, renameFailedMessage) }
                                }
                            },
                            onDelete = { scope.launch { vm.playlists.delete(row.card.id) } },
                            onShare = {
                                scope.launch {
                                    val urls = vm.playlists.observeItems(row.card.id).first().map { it.pageUrl }
                                    sharePlaylist(context, row.card.name, pageUrl = null, videoUrls = urls)
                                }
                            },
                        )
                        is PlaylistRow.Followed -> FollowedPlaylistRow(
                            listing = row.listing,
                            selected = row.listing.key in selection,
                            onClick = { if (selecting) onToggle(row.listing.key) else onOpenListing(row.listing) },
                            onLongPress = { onToggle(row.listing.key) },
                            onShare = { sharePlaylist(context, row.listing.title.ifBlank { defaultPlaylistTitle }, row.listing.key) },
                            onUnfollow = { scope.launch { vm.followedPlaylists.unfollow(row.listing.key) } },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        NamePromptDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = { name ->
                scope.launch {
                    runCatching { vm.playlists.create(name) }
                        .onFailure { showToast(context, createFailedMessage) }
                }
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
}

/** One row shape for both local and followed playlists (report #9: they used to render
 *  differently -- a bare cover box vs. a monogram disc, count vs. no count). Same thumbnail/title/
 *  subtitle layout either way; [trailing] is each caller's own per-row ⋮ menu, the only part that
 *  legitimately differs (Share+Rename+Delete vs. Share+Delete -- a followed playlist isn't the
 *  user's own to rename). */
@Composable
private fun PlaylistRowItem(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .heightIn(min = Sizes.listTwoLine)
            .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Sizes.thumbWidth, Sizes.thumbHeight).clip(RoundedCornerShape(Radius.s)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            // A followed row's thumbnail is the first video's, stored at follow time (or backfilled
            // lazily by LibraryViewModel) -- never a per-row fetch (rule 6). Null until either lands,
            // and the box stays a plain surface tint, same as an empty local cover.
            if (thumbnailUrl != null) {
                AsyncImage(model = thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(start = Spacing.s16).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        trailing()
    }
}

@Composable
private fun LocalPlaylistRow(card: PlaylistCard, onClick: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit, onShare: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    PlaylistRowItem(
        thumbnailUrl = card.coverThumbnailUrl,
        title = card.name,
        subtitle = pluralStringResource(R.plurals.library_video_count, card.itemCount, card.itemCount),
        selected = false, // local playlists have no multi-select here -- delete is per-row (menu below)
        onClick = onClick,
        onLongPress = {},
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_share)) }, onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.library_rename)) }, onClick = { menuOpen = false; renaming = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuOpen = false; confirmingDelete = true })
                }
            }
        },
    )
    if (confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.library_delete_playlist_title),
            body = stringResource(R.string.library_delete_playlist_body, card.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirmingDelete = false; onDelete() },
            onDismiss = { confirmingDelete = false },
        )
    }
    if (renaming) {
        NamePromptDialog(title = stringResource(R.string.library_rename_title), initial = card.name, onConfirm = { onRename(it); renaming = false }, onDismiss = { renaming = false })
    }
}

/** A followed remote playlist: no stored count (unlike a local playlist's item count) -- "Followed"
 *  is the subtitle, and it's also the only tell a tap needs to route on (open the remote listing,
 *  not a local playlist id). Same ⋮ menu shape as [LocalPlaylistRow], minus Rename: it isn't the
 *  user's own playlist to rename, so the menu offers Unfollow instead of Delete. */
@Composable
private fun FollowedPlaylistRow(
    listing: Listing,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onShare: () -> Unit,
    onUnfollow: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    PlaylistRowItem(
        thumbnailUrl = listing.thumbnailUrl,
        title = listing.title,
        subtitle = stringResource(R.string.state_followed),
        selected = selected,
        onClick = onClick,
        onLongPress = onLongPress,
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_share)) }, onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_unfollow_playlist)) }, onClick = { menuOpen = false; onUnfollow() })
                }
            }
        },
    )
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
        LibraryEmptyState(stringResource(R.string.library_channels_empty), stringResource(R.string.library_channels_empty_hint))
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
                    .heightIn(min = Sizes.listOneLine)
                    .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mockup: no banner, no toast, no subtitle -- a dim row + the eye glyph itself say
                // whether a subscribed channel is feeding Home/Shorts.
                Row(
                    Modifier.weight(1f).alpha(if (row.showInFeed) 1f else 0.4f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonogramDisc(channel.title, size = Spacing.s40)
                    Text(
                        channel.title.ifBlank { channel.key },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Spacing.s16).weight(1f),
                    )
                }
                // The glyph itself stays full-opacity even when the row dims -- it's the tap target
                // that un-hides the channel, so it needs to read clearly either way.
                val channelName = channel.title.ifBlank { stringResource(R.string.library_channel_word) }
                val feedToggleDescription = if (row.showInFeed) {
                    stringResource(R.string.library_hide_from_feed, channelName)
                } else {
                    stringResource(R.string.library_show_in_feed, channelName)
                }
                IconButton(
                    onClick = { onToggleShowInFeed(row) },
                    modifier = Modifier.semantics { contentDescription = feedToggleDescription },
                ) {
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
private fun EyeGlyph(tint: Color, slashed: Boolean, modifier: Modifier = Modifier, size: Dp = Sizes.icon) {
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
    var confirmingClear by remember { mutableStateOf(false) }
    var actionSheetRef by remember { mutableStateOf<VideoRef?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (!recording) {
            Text(
                stringResource(R.string.library_history_off_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.pageMargin),
            )
        }
        val list = entries
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> LibraryEmptyState(
                stringResource(R.string.library_history_empty),
                stringResource(if (recording) R.string.library_history_empty_hint else R.string.library_history_off_hint),
            )
            else -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { confirmingClear = true }) { Text(stringResource(R.string.library_clear_history)) }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.pageUrl }) { ref -> ResultRow(ref, onClick = { onOpen(ref) }, onLongPress = { actionSheetRef = ref }) }
                }
            }
        }
    }

    if (confirmingClear) {
        ConfirmDialog(
            title = stringResource(R.string.library_clear_history_title),
            body = stringResource(R.string.library_clear_history_body),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = { confirmingClear = false; scope.launch { vm.history.clear() } },
            onDismiss = { confirmingClear = false },
        )
    }
    actionSheetRef?.let { ref -> VideoActionSheet(ref, onDismiss = { actionSheetRef = null }) }
}

@Composable
internal fun LibraryEmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.s40),
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
internal fun NamePromptDialog(
    title: String,
    initial: String = "",
    confirmLabel: String = stringResource(R.string.action_save),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
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
