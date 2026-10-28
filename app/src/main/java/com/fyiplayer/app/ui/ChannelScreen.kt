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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.core.ChannelTab
import com.fyiplayer.app.R
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.player.PlaybackSession

/**
 * Tabbed channel page (replaces the old flat channel listing -- a channel has five independent
 * feeds plus in-channel search, a playlist has one). Each tab loads on first selection and is
 * then cached in [ChannelViewModel] for the screen's lifetime; switching tabs never refetches.
 * Courses/Live start visible and silently drop out of the tab row the first time they come back
 * tab-unavailable -- probing all five up front would be five slow calls before anything
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
    // The Shorts tab is a grid, so it cannot share the list states above. Hoisted for the same
    // reason: a grid state made inside the tab body would reset its scroll on each tab switch.
    val shortsGridState: LazyGridState = rememberLazyGridState()

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
                        }) { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.channel_play_all)) }
                        IconButton(onClick = {
                            selectedInOrder(currentVideos, selection).forEach { PlaybackSession.enqueue(it) }
                            selection = emptySet()
                        }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_to_queue)) }
                    },
                )
            } else {
                ChannelTopBar(listing.title, currentVideos, onBack)
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
            ChannelHeader(listing, subscribed) { vm.toggleSubscription(listing) }
            ChannelTabRow(vm.availableTabs, vm.selected) { vm.selectTab(listing, it) }
            when (val sel = vm.selected) {
                is ChannelUiTab.Content -> when {
                    sel.tab in CONTAINER_TABS -> ContainerTabBody(
                        vm.containerTab(sel.tab), listState = listStateByTab.getValue(sel), onOpen = onOpenListing,
                        onLoadMore = { vm.loadMoreTab(listing, sel.tab) }, onRetry = { vm.retryTab(listing, sel.tab) },
                    )
                    sel.tab == ChannelTab.SHORTS -> ChannelShortsGrid(
                        state = vm.videoTab(sel.tab), selection = selection, gridState = shortsGridState,
                        onTap = ::playAndOpen, onToggle = { selection = selection.toggled(it.pageUrl) },
                        onLoadMore = { vm.loadMoreTab(listing, sel.tab) }, onRetry = { vm.retryTab(listing, sel.tab) },
                    )
                    else -> VideoTabBody(
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
    videos: List<VideoRef>,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(title.ifBlank { stringResource(R.string.channel_title_fallback) }, maxLines = 1) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
        actions = {
            if (videos.isNotEmpty()) {
                IconButton(onClick = { PlaybackSession.play(videos, 0) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.channel_play_all))
                }
            }
        },
    )
}

/**
 * Channel name, avatar and a heart that toggles the subscription, under the app bar (the bar keeps
 * only back, title and play-all). The avatar is drawn only when the listing already carried one;
 * no lookup is made for it. A tap shows "Subscribed" or "Unsubscribed" in a pill under the heart,
 * the same overlay the video page uses, so the pill takes no layout space and the tabs stay put.
 * The Box has zIndex so the pill is drawn over the tab row below it.
 */
@Composable
private fun ChannelHeader(listing: Listing, subscribed: Boolean, onToggleSubscribe: () -> Unit) {
    val subscribedMessage = stringResource(R.string.channel_subscribed)
    val unsubscribedMessage = stringResource(R.string.channel_unsubscribed)
    val heartTint = if (subscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    var message by remember { mutableStateOf<ActionMessage?>(null) }
    var messageVisible by remember { mutableStateOf(false) }
    var heartBottomPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        messageVisible = true
        delay(ACTION_MESSAGE_MILLIS)
        messageVisible = false
    }

    Box(Modifier.fillMaxWidth().zIndex(1f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listing.thumbnailUrl?.let { avatarUrl ->
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(Sizes.channelAvatar).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(Spacing.s16))
            }
            Text(
                listing.title.ifBlank { stringResource(R.string.channel_title_fallback) },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(Spacing.s16))
            IconButton(
                onClick = {
                    message = ActionMessage(if (subscribed) unsubscribedMessage else subscribedMessage)
                    onToggleSubscribe()
                },
                modifier = Modifier.onGloballyPositioned { heartBottomPx = it.boundsInParent().bottom },
            ) {
                Icon(
                    if (subscribed) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(if (subscribed) R.string.action_unsubscribe else R.string.action_subscribe),
                    tint = heartTint,
                    modifier = Modifier.size(Sizes.icon),
                )
            }
        }
        // Takes no layout space: it is placed at the heart's bottom edge and does not size the Box.
        AnimatedVisibility(
            visible = messageVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = Spacing.pageMargin)
                .offset { IntOffset(0, heartBottomPx.roundToInt()) },
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ActionMessagePill(message?.text.orEmpty())
        }
    }
}

@StringRes
private fun ChannelTab.labelRes(): Int = when (this) {
    ChannelTab.VIDEOS -> R.string.channel_tab_videos
    ChannelTab.SHORTS -> R.string.channel_tab_shorts
    ChannelTab.PLAYLISTS -> R.string.channel_tab_playlists
    ChannelTab.COURSES -> R.string.channel_tab_courses
    ChannelTab.LIVE -> R.string.state_live
}

@Composable
private fun ChannelTabRow(available: List<ChannelTab>, selected: ChannelUiTab, onSelect: (ChannelUiTab) -> Unit) {
    val uiTabs: List<ChannelUiTab> = available.map { ChannelUiTab.Content(it) } + ChannelUiTab.Search
    val index = uiTabs.indexOf(selected).coerceAtLeast(0)
    ScrollableTabRow(selectedTabIndex = index, edgePadding = Spacing.s16) {
        uiTabs.forEach { tab ->
            val label = stringResource(if (tab is ChannelUiTab.Content) tab.tab.labelRes() else R.string.channel_tab_search)
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
            Text(stringResource(R.string.state_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    // A finished load with nothing in it and no error is a real empty tab, not a blank screen.
    if (state.loaded && state.items.isEmpty() && state.error == null) {
        EmptyStateScreen(stringResource(R.string.channel_empty_videos_title))
        return
    }
    val channelErrorName = stringResource(R.string.channel_error_source)
    val errors = state.error?.let { listOf(ErrorRow(channelErrorName, it, onRetry = if (state.blocked) null else onRetry)) } ?: emptyList()
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
    val channelErrorName = stringResource(R.string.channel_error_source)
    when {
        state.items.isEmpty() && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        // A failed first page: same error row (with Retry, unless an access wall stopped it) as
        // every other list, instead of bare text with no way forward.
        state.error != null && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorRowView(ErrorRow(channelErrorName, state.error, onRetry = if (state.blocked) null else onRetry))
        }
        state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.channel_empty_container), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(state.items, key = { it.key }) { item ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Sizes.listTwoLine).clickable { onOpen(item) }.padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Same idiom as ResultsList.kt's ResultRow: grey placeholder box when the
                    // source didn't publish a thumbnail, never a blank gap.
                    Box(
                        Modifier.size(Sizes.thumbWidth, Sizes.thumbHeight).clip(RoundedCornerShape(Radius.s))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (item.thumbnailUrl != null) {
                            AsyncImage(model = item.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Text(
                        item.title.ifBlank { stringResource(R.string.state_untitled) },
                        modifier = Modifier.padding(start = Spacing.s16),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (state.error != null) {
                // A later page failed: no auto-reload, Retry continues from that page.
                item { ErrorRowView(ErrorRow(channelErrorName, state.error, onRetry = if (state.blocked) null else onRetry)) }
            } else if (state.nextPage != null) {
                item { TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(Spacing.s16)) { Text(stringResource(R.string.results_load_more)) } }
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
            label = { Text(stringResource(R.string.channel_search_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.runChannelSearch(listing, input) }),
            modifier = Modifier.fillMaxWidth().padding(Spacing.pageMargin),
        )
        val searchErrorName = stringResource(R.string.channel_tab_search)
        when {
            state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !state.loaded -> Unit
            state.items.isEmpty() && state.error == null -> EmptyStateScreen(stringResource(R.string.channel_search_empty_title, vm.searchQuery))
            else -> {
                val errors = state.error?.let {
                    listOf(ErrorRow(searchErrorName, it, onRetry = if (state.blocked) null else { { vm.retrySearch(listing) } }))
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
