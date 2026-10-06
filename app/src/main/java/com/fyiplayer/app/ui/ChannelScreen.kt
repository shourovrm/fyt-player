package com.fyiplayer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.core.ChannelTab
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.player.PlaybackSession

/**
 * Tabbed channel page (replaces the old flat channel listing -- a channel has five independent
 * feeds plus in-channel search, a playlist has one). Each tab loads on first selection and is
 * then cached in [ChannelViewModel] for the screen's lifetime; switching tabs never refetches.
 * Courses/Live start visible and silently drop out of the tab row the first time they come back
 * tab-unavailable -- probing all five up front would be five slow engine calls before anything
 * renders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    listing: Listing,
    onOpenDetail: (VideoRef) -> Unit,
    onOpenListing: (Listing) -> Unit,
    onOpenShorts: (List<VideoRef>, Int) -> Unit,
    onBack: () -> Unit,
) {
    val vm: ChannelViewModel = viewModel()
    LaunchedEffect(listing) { vm.ensureChannel(listing) }

    val subscribed by vm.isSubscribed(listing.key).collectAsStateWithLifecycle(initialValue = false)
    var selection by remember { mutableStateOf(emptySet<String>()) }
    val selecting = selection.isNotEmpty()
    // A fresh selection never survives a tab switch -- it was made against a different list.
    LaunchedEffect(vm.selected) { selection = emptySet() }
    BackHandler(enabled = selecting) { selection = emptySet() }

    // One list state per tab, created up front: a state made inside each tab's body is dropped when
    // the tab leaves composition, so Videos -> Shorts -> Videos used to restart at the top.
    // rememberLazyListState is saveable, so these also survive a trip to Detail and back.
    val listStateByTab: Map<ChannelUiTab, LazyListState> =
        (ChannelTab.entries.map { ChannelUiTab.Content(it) } + ChannelUiTab.Search)
            .associateWith { tab -> key(tab) { rememberLazyListState() } }

    val currentVideos: List<VideoRef> = when (val sel = vm.selected) {
        is ChannelUiTab.Content -> if (sel.tab in CONTAINER_TABS) emptyList() else vm.videoTab(sel.tab).items
        ChannelUiTab.Search -> vm.searchState.items
    }

    fun playAndOpen(ref: VideoRef) {
        // Shorts are vertical clips: open the swipe pager, which needs the whole tab's list. A
        // regular row tap just opens Detail now -- Detail autoplays the single video (PipePipe
        // queue model, CLAUDE.md); the header's "Play all" keeps the whole-tab queue.
        val sel = vm.selected
        if (sel is ChannelUiTab.Content && sel.tab == ChannelTab.SHORTS) {
            val index = currentVideos.indexOfFirst { it.pageUrl == ref.pageUrl }.coerceAtLeast(0)
            onOpenShorts(currentVideos, index)
        } else if (ref.isShort) {
            // Search-shelf short: pager seeded with the shelf's shorts only, same as Home search.
            val shorts = partitionShorts(currentVideos).first
            onOpenShorts(shorts, shorts.indexOfFirst { it.pageUrl == ref.pageUrl }.coerceAtLeast(0))
        } else {
            onOpenDetail(ref)
        }
    }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selection.size,
                    onClose = { selection = emptySet() },
                    onSelectAll = { selection = selectAllOrNone(currentVideos, selection) },
                    actions = {
                        // Whole loaded tab, ignoring selection -- distinct from the FAB's "Play"
                        // (selected only). Same icon/semantics as the non-selecting top bar's own
                        // "Play all" button further down this file.
                        IconButton(onClick = {
                            if (currentVideos.isNotEmpty()) PlaybackSession.play(currentVideos, 0)
                            selection = emptySet()
                        }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play all") }
                        IconButton(onClick = {
                            selectedInOrder(currentVideos, selection).forEach { PlaybackSession.enqueue(it) }
                            selection = emptySet()
                        }) { Icon(Icons.Filled.Add, contentDescription = "Add selected to queue") }
                    },
                )
            } else {
                ChannelTopBar(listing.title, subscribed, currentVideos, onBack) { vm.toggleSubscription(listing) }
            }
        },
        floatingActionButton = {
            if (selecting) {
                PlaySelectionFab(selection.size) {
                    val refs = selectedInOrder(currentVideos, selection)
                    if (refs.isNotEmpty()) PlaybackSession.play(refs, 0)
                    selection = emptySet()
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ChannelTabRow(vm.availableTabs, vm.selected) { vm.selectTab(listing, it) }
            when (val sel = vm.selected) {
                is ChannelUiTab.Content -> if (sel.tab in CONTAINER_TABS) {
                    ContainerTabBody(
                        vm.containerTab(sel.tab), listState = listStateByTab.getValue(sel), onOpen = onOpenListing,
                        onLoadMore = { vm.loadMoreTab(listing, sel.tab) }, onRetry = { vm.retryTab(listing, sel.tab) },
                    )
                } else {
                    VideoTabBody(
                        state = vm.videoTab(sel.tab), selection = selection, listState = listStateByTab.getValue(sel),
                        onTap = ::playAndOpen, onToggle = { selection = selection.toggled(it.pageUrl) },
                        onLoadMore = { vm.loadMoreTab(listing, sel.tab) }, onRetry = { vm.retryTab(listing, sel.tab) },
                    )
                }
                ChannelUiTab.Search -> ChannelSearchBody(
                    listing = listing, vm = vm, state = vm.searchState, selection = selection,
                    listState = listStateByTab.getValue(ChannelUiTab.Search),
                    onTap = ::playAndOpen, onToggle = { selection = selection.toggled(it.pageUrl) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelTopBar(
    title: String,
    subscribed: Boolean,
    videos: List<VideoRef>,
    onBack: () -> Unit,
    onToggleSubscribe: () -> Unit,
) {
    TopAppBar(
        title = { Text(title.ifBlank { "Channel" }, maxLines = 1) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
        actions = {
            if (videos.isNotEmpty()) {
                IconButton(onClick = { PlaybackSession.play(videos, 0) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play all")
                }
            }
            // Filled vs outlined makes the two states unmistakable without a second icon.
            if (subscribed) {
                OutlinedButton(onClick = onToggleSubscribe) { Text("Subscribed") }
            } else {
                Button(onClick = onToggleSubscribe) { Text("Subscribe") }
            }
        },
    )
}

private fun ChannelTab.label(): String = when (this) {
    ChannelTab.VIDEOS -> "Videos"
    ChannelTab.SHORTS -> "Shorts"
    ChannelTab.PLAYLISTS -> "Playlists"
    ChannelTab.COURSES -> "Courses"
    ChannelTab.LIVE -> "Live"
}

@Composable
private fun ChannelTabRow(available: List<ChannelTab>, selected: ChannelUiTab, onSelect: (ChannelUiTab) -> Unit) {
    val uiTabs: List<ChannelUiTab> = available.map { ChannelUiTab.Content(it) } + ChannelUiTab.Search
    val index = uiTabs.indexOf(selected).coerceAtLeast(0)
    ScrollableTabRow(selectedTabIndex = index, edgePadding = 12.dp) {
        uiTabs.forEach { tab ->
            val label = if (tab is ChannelUiTab.Content) tab.tab.label() else "Search"
            Tab(selected = tab == selected, onClick = { onSelect(tab) }, text = { Text(label) })
        }
    }
}

/** Shared by [ChannelScreen] and [ListingScreen]: routes a row tap to selection-toggle or
 *  play-from-here depending on whether [selection] is non-empty, long-press always enters selection
 *  (same convention as Likes/Playlist detail). Selected rows are highlighted by [ResultRow]. */
@Composable
internal fun SelectableVideoList(
    items: List<VideoRef>,
    errors: List<ErrorRow>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    selection: Set<String>,
    onTap: (VideoRef) -> Unit,
    onToggle: (VideoRef) -> Unit,
    modifier: Modifier = Modifier,
    topContent: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    val selecting = selection.isNotEmpty()
    ResultsListColumn(
        items = items,
        topContent = topContent,
        errors = errors,
        hasMore = hasMore,
        onLoadMore = onLoadMore,
        onClick = { if (selecting) onToggle(it) else onTap(it) },
        onLongPress = onToggle,
        listState = listState,
        modifier = modifier,
        isLoadingMore = isLoadingMore,
        // Every caller's errors belong to this one list, so any error means its last load failed.
        loadMoreFailed = errors.isNotEmpty(),
        selection = selection,
    )
}

@Composable
private fun VideoTabBody(
    state: VideoTabState,
    selection: Set<String>,
    listState: LazyListState,
    onTap: (VideoRef) -> Unit,
    onToggle: (VideoRef) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    if (state.items.isEmpty() && state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    // A finished load with nothing in it and no error is a real empty tab, not a blank screen.
    if (state.loaded && state.items.isEmpty() && state.error == null) {
        EmptyStateScreen("No videos", "This channel has no videos here.")
        return
    }
    val errors = state.error?.let { listOf(ErrorRow("Channel", it, onRetry = if (state.blocked) null else onRetry)) } ?: emptyList()
    SelectableVideoList(
        items = state.items, errors = errors, hasMore = state.nextPage != null, isLoadingMore = state.loading && state.items.isNotEmpty(),
        onLoadMore = onLoadMore, selection = selection, onTap = onTap, onToggle = onToggle,
        modifier = Modifier.fillMaxSize(), listState = listState,
    )
}

@Composable
private fun ContainerTabBody(
    state: ContainerTabState,
    listState: LazyListState,
    onOpen: (Listing) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.items.isEmpty() && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        // A failed first page: same error row (with Retry, unless an access wall stopped it) as
        // every other list, instead of bare text with no way forward.
        state.error != null && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorRowView(ErrorRow("Channel", state.error, onRetry = if (state.blocked) null else onRetry))
        }
        state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing here yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(state.items, key = { it.key }) { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Same idiom as ResultsList.kt's ResultRow: grey placeholder box when the
                    // source didn't publish a thumbnail, never a blank gap.
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (item.thumbnailUrl != null) {
                            AsyncImage(model = item.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Text(
                        item.title.ifBlank { "Untitled" },
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (state.error != null) {
                // A later page failed: no auto-reload, Retry continues from that page.
                item { ErrorRowView(ErrorRow("Channel", state.error, onRetry = if (state.blocked) null else onRetry)) }
            } else if (state.nextPage != null) {
                item { TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("Load more") } }
            }
        }
    }
}

/** Search within one channel -- the field itself makes the "scoped to this channel" fact explicit
 *  rather than looking like a second global search. */
@Composable
private fun ChannelSearchBody(
    listing: Listing,
    vm: ChannelViewModel,
    state: VideoTabState,
    selection: Set<String>,
    listState: LazyListState,
    onTap: (VideoRef) -> Unit,
    onToggle: (VideoRef) -> Unit,
) {
    var input by remember { mutableStateOf(vm.searchQuery) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Search in \"${listing.title.ifBlank { "this channel" }}\"") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.runChannelSearch(listing, input) }),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        when {
            state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Search this channel's videos", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.items.isEmpty() && state.error == null -> EmptyStateScreen(
                "No results for \"${vm.searchQuery}\"",
                "Nothing in this channel matches that search.",
            )
            else -> {
                val errors = state.error?.let {
                    listOf(ErrorRow("Search", it, onRetry = if (state.blocked) null else { { vm.retrySearch(listing) } }))
                } ?: emptyList()
                // Same shape as global search (HomeScreen): shorts pulled into a shelf above the rows.
                val (shortsItems, longformItems) = partitionShorts(state.items)
                SelectableVideoList(
                    items = longformItems, errors = errors, hasMore = state.nextPage != null,
                    isLoadingMore = state.loading && state.items.isNotEmpty(), onLoadMore = { vm.loadMoreSearch(listing) },
                    selection = selection, onTap = onTap, onToggle = onToggle, modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    topContent = if (shortsItems.isEmpty()) null else { { ShortsShelf(shortsItems, onClick = onTap) } },
                )
            }
        }
    }
}
