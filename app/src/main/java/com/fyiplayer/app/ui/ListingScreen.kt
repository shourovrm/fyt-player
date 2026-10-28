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
import androidx.compose.ui.res.stringResource
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
import com.fyiplayer.app.R
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
    // Queue sheet title for anything started from this list; same resolution as the top bar.
    val listingQueueTitle = listing.title.ifBlank { vm.resolvedTitle ?: stringResource(R.string.listing_title_fallback) }
    // Detail opens on the first video; otherwise the list stays put and only the queue bar shows.
    fun playFromFirstAndOpen(refs: List<VideoRef>) {
        val first = refs.firstOrNull() ?: return
        PlaybackSession.play(refs, 0, queueTitle = listingQueueTitle)
        onOpenDetail(first)
    }
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
                            selectedInOrder(vm.items, selection).forEach { PlaybackSession.enqueue(it, listingQueueTitle) }
                            selection = emptySet()
                        }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_to_queue)) }
                    },
                )
            } else {
                val context = LocalContext.current
                val titleFallback = stringResource(R.string.listing_title_fallback)
                val followedMessage = stringResource(R.string.listing_followed)
                TopAppBar(
                    title = { Text(listing.title.ifBlank { vm.resolvedTitle ?: titleFallback }, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                    actions = {
                        if (listing.kind == Listing.Kind.PLAYLIST) {
                            IconButton(onClick = { sharePlaylist(context, listing.title.ifBlank { vm.resolvedTitle ?: titleFallback }, listing.key) }) {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.listing_share_playlist))
                            }
                            IconButton(onClick = { vm.follow(listing); showToast(context, followedMessage) }) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_follow_playlist))
                            }
                        }
                        if (vm.items.isNotEmpty()) {
                            IconButton(onClick = { playFromFirstAndOpen(vm.items) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.channel_play_all))
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
                    selection = emptySet()
                    playFromFirstAndOpen(refs)
                }
            }
        },
    ) { padding ->
        if (vm.items.isEmpty() && vm.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.state_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (vm.items.isEmpty() && vm.error == null) {
            // Loaded fine, nothing in it: say so instead of a blank screen.
            Box(Modifier.fillMaxSize().padding(padding)) { EmptyStateScreen(stringResource(R.string.channel_empty_videos_title)) }
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
                selection = selection,
                // Row tap opens Detail only -- Detail autoplays the single video (PipePipe queue
                // model, CLAUDE.md). "Play all" above is the one explicit whole-list play.
                onTap = onOpenDetail,
                onToggle = { selection = selection.toggled(it.pageUrl) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}
