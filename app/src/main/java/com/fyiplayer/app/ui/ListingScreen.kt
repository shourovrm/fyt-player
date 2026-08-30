package com.fyiplayer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.player.PlaybackSession

/** A playlist listing (DESIGN.md §5), paged the same way as Home's single-source tab -- a
 *  playlist is genuinely one flat list, unlike a channel's five independent tabs (see
 *  [ChannelScreen], which is what a CHANNEL [Listing] routes to instead). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingScreen(listing: Listing, onOpenDetail: (VideoRef) -> Unit, onBack: () -> Unit) {
    val vm: ListingViewModel = viewModel()
    var selection by remember { mutableStateOf(emptySet<String>()) }
    val selecting = selection.isNotEmpty()
    BackHandler(enabled = selecting) { selection = emptySet() }

    LaunchedEffect(listing) { vm.ensureLoaded(listing) }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selection.size,
                    onClose = { selection = emptySet() },
                    onSelectAll = { selection = selectAllOrNone(vm.items, selection) },
                    actions = {
                        IconButton(onClick = {
                            selectedInOrder(vm.items, selection).forEach { PlaybackSession.enqueue(it) }
                            selection = emptySet()
                        }) { Icon(Icons.Filled.Add, contentDescription = "Add selected to queue") }
                    },
                )
            } else {
                val context = LocalContext.current
                TopAppBar(
                    title = { Text(listing.title.ifBlank { vm.resolvedTitle ?: "Listing" }, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        if (listing.kind == Listing.Kind.PLAYLIST) {
                            IconButton(onClick = { sharePlaylist(context, listing.title.ifBlank { vm.resolvedTitle ?: "Playlist" }, listing.key) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share playlist")
                            }
                            IconButton(onClick = { vm.follow(listing); showToast(context, "Added to playlists") }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add to playlists")
                            }
                        }
                        if (vm.items.isNotEmpty()) {
                            IconButton(onClick = { PlaybackSession.play(vm.items, 0) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play all")
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selecting) {
                PlaySelectionFab(selection.size) {
                    val refs = selectedInOrder(vm.items, selection)
                    if (refs.isNotEmpty()) PlaybackSession.play(refs, 0)
                    selection = emptySet()
                }
            }
        },
    ) { padding ->
        if (vm.items.isEmpty() && vm.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val errors = vm.error?.let {
                listOf(ErrorRow(listing.title, it, onRetry = if (vm.blocked) null else { { vm.retry(listing) } }))
            } ?: emptyList()
            SelectableVideoList(
                items = vm.items,
                errors = errors,
                hasMore = vm.nextPage != null,
                isLoadingMore = vm.loading && vm.items.isNotEmpty(),
                onLoadMore = { vm.loadMore(listing) },
                selecting = selecting,
                // Row tap opens Detail only -- Detail autoplays the single video (PipePipe queue
                // model, CLAUDE.md). "Play all" above is the one explicit whole-list play.
                onTap = onOpenDetail,
                onToggle = { selection = selection.toggled(it.pageUrl) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}
