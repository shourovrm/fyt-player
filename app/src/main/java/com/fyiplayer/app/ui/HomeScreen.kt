package com.fyiplayer.app.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.R
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.Topic
import com.fyiplayer.app.settings.OnboardingSheet
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.ui.theme.ControlShape
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

/**
 * Home (browse) and Search share this screen: a blank query shows [HomeFeedSection] (newest
 * uploads from watched channels), a non-blank query searches every enabled source instead. With
 * exactly one source enabled today the search tab row is skipped entirely (DESIGN.md §5) rather
 * than showing a lone, pointless "All" pill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDetail: (VideoRef) -> Unit,
    onOpenListing: (Listing) -> Unit,
    onOpenShorts: (List<VideoRef>, Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val app = rememberFyiApp()
    val vm: HomeViewModel = viewModel()
    val gridState = rememberLazyGridState()
    val enabledIds by app.prefs.enabledSources.collectAsStateWithLifecycle(initialValue = emptySet())
    val browseSources = remember(enabledIds) { SourceRegistry.browseSourcesFor(enabledIds) }
    val searchHistory by vm.searchHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    var searchOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var actionSheetRef by remember { mutableStateOf<VideoRef?>(null) }
    val onboardingDone by app.prefs.onboardingDone.collectAsStateWithLifecycle(initialValue = true)

    val isSearching = vm.query.isNotBlank()
    val searchShown = searchOpen || isSearching
    val focusManager = LocalFocusManager.current
    fun closeSearch() {
        vm.clearSearch()
        searchOpen = false
        focusManager.clearFocus()
    }
    // Every other screen exits on back; Home has no screen "above" search to pop to, so back must
    // leave search mode instead of falling through to the Activity's default (closing the app).
    BackHandler(enabled = searchShown) { closeSearch() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) focusRequester.requestFocus()
    }
    // Suggestion fetch tracks the query while the field has focus -- requestSuggestions debounces
    // internally, and a blank query (or losing focus) drops any suggestions on screen.
    LaunchedEffect(vm.query, searchFieldFocused) {
        if (searchFieldFocused && vm.query.isNotBlank()) vm.requestSuggestions(vm.query) else vm.clearSuggestions()
    }

    val tabIds = remember(browseSources) { listOf(ALL_TAB_ID) + browseSources.map { it.id } }
    LaunchedEffect(tabIds) { vm.selectedTab = resolveSelectedTab(vm.selectedTab, tabIds) }
    // Channels land one by one, each sorted above the last; the grid keeps its position by key,
    // so without this the viewport rides the first channel's rows down as newer ones stack on top.
    // That list state is shared with search results and topic lists, so the pin applies only while
    // the feed is what is on screen -- otherwise a subscription landing mid-refresh would snap
    // whatever else is showing back to row 0.
    val feedShowing = !isSearching && vm.selectedTopic == null
    LaunchedEffect(vm.feed.items, feedShowing) {
        if (shouldPinFeedToTop(feedShowing, vm.feed.loading)) gridState.requestScrollToItem(0)
    }
    // Home's feed builds from watch history, not a per-source fetch -- load it once, the first
    // time a blank query is on screen.
    LaunchedEffect(browseSources, isSearching) {
        if (!isSearching) vm.loadFeedIfNeeded(browseSources)
    }

    val feeding = if (vm.selectedTab == ALL_TAB_ID) browseSources else browseSources.filter { it.id == vm.selectedTab }
    val feedingResults = feeding.map { vm.searchResults[it.id] ?: TabResult(it.displayName) }
    val searchItems = if (vm.selectedTab == ALL_TAB_ID) {
        interleave(feeding.map { vm.searchResults[it.id]?.items ?: emptyList() })
    } else {
        feedingResults.firstOrNull()?.items ?: emptyList()
    }
    // Shelf above, regular rows below -- "All" tab's union already carries encounter order, so
    // partitioning after interleave keeps the shelf in that same order for free.
    val (shortsItems, longformItems) = partitionShorts(searchItems)
    val initialLoading = searchItems.isEmpty() && feedingResults.any { it.loading }
    val isLoadingMore = !initialLoading && feedingResults.any { it.loading }
    val hasMore = feedingResults.any { it.canContinue }
    // Page 1 rarely carries more than a handful of shorts (no shorts-only search filter in the
    // extractor), so keep pulling pages until the shelf is worth swiping -- bounded, so a
    // shorts-poor query costs at most a few extra pages, not a crawl to exhaustion.
    var autoGrowLeft by remember(vm.query, vm.selectedTab) { mutableStateOf(MAX_SHELF_AUTO_FETCHES) }
    LaunchedEffect(isSearching, searchItems.size, hasMore, isLoadingMore) {
        if (isSearching && shortsItems.size < MIN_SHELF_SHORTS && hasMore && !isLoadingMore &&
            searchItems.isNotEmpty() && autoGrowLeft > 0
        ) {
            autoGrowLeft--
            vm.continueTab(feeding)
        }
    }
    val unavailableText = stringResource(R.string.state_unavailable)
    val allExhausted = feeding.isNotEmpty() && feedingResults.all { it.exhausted }
    val errorRows = feeding.zip(feedingResults).mapNotNull { (src, res) ->
        when {
            res.blocked -> ErrorRow(src.displayName, res.error ?: unavailableText, onRetry = null)
            res.error != null -> ErrorRow(src.displayName, res.error, onRetry = { vm.retryTab(src) })
            else -> null
        }
    }

    // Drop focus on submit so the keyboard does not stay over the results it just produced.
    fun submitSearch(query: String) {
        focusManager.clearFocus()
        vm.runSearch(query, browseSources)
    }

    fun openResult(ref: VideoRef) {
        // A YouTube search can return channel rows shaped as a VideoRef (NewPipeYoutubeSource's
        // toChannelRef): no duration, pageUrl is the channel page, not a watch URL. detail() has
        // no idea what to do with that -- route to the channel screen instead of a dead-end resolve.
        if (isChannelPageUrl(ref.pageUrl)) {
            onOpenListing(Listing(sourceId = ref.sourceId, kind = Listing.Kind.CHANNEL, key = ref.pageUrl, title = ref.title, thumbnailUrl = ref.thumbnailUrl))
            return
        }
        // Stopgap YouTube search hit for a playlist (Agent B): pageUrl is the playlist page, not a
        // watch URL -- same dead-end-resolve problem as the channel case above.
        if (ref.pageUrl.contains("playlist?list=")) {
            onOpenListing(Listing(sourceId = ref.sourceId, kind = Listing.Kind.PLAYLIST, key = ref.pageUrl, title = ref.title))
            return
        }
        // Tapping a row opens Detail only -- Detail autoplays this single ref itself (DECISIONS
        // 2026-08-07). The queue stays whatever was explicitly enqueued, never silently replaced.
        onOpenDetail(ref)
    }
    fun openShort(ref: VideoRef) {
        val index = shortsItems.indexOfFirst { it.pageUrl == ref.pageUrl }.coerceAtLeast(0)
        // ponytail: pager seeded with this loaded shelf list only -- no extra shorts paging on
        // swipe-past-end. Add real paging if the shelf ever needs to grow beyond one page's worth.
        onOpenShorts(shortsItems, index)
    }

    Column(Modifier.fillMaxSize()) {
        if (searchShown) {
            SearchPill(
                query = vm.query,
                onQueryChange = { vm.query = it },
                onFocusChanged = { searchFieldFocused = it },
                onClose = { closeSearch() },
                onClear = { vm.clearSearch() },
                onSearch = { submitSearch(vm.query) },
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
            )
        } else {
            HomeAppBar(onOpenSearch = { searchOpen = true }, onOpenSettings = onOpenSettings)
        }
        when {
            browseSources.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.home_no_sources), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            vm.query.isBlank() && searchFieldFocused && searchHistory.isNotEmpty() -> {
                SearchHistorySuggestions(
                    entries = searchHistory.map { it.query },
                    onPick = { submitSearch(it) },
                    onDelete = { vm.deleteSearchHistoryEntry(it) },
                    onClearAll = { vm.clearSearchHistory() },
                )
            }
            // Typed but not yet submitted: searchResults is still the empty map runSearch hasn't
            // populated yet, so show live suggestions here instead of a "No results" flash.
            searchFieldFocused && vm.query.isNotBlank() && vm.searchResults.isEmpty() -> {
                SearchSuggestionsDropdown(suggestions = vm.suggestions, onPick = { submitSearch(it) })
            }
            isSearching -> {
                // tabIds always carries a synthetic "All" entry on top of browseSources, so
                // tabIds.size is never 1 even with a single source -- gate on the real count.
                if (browseSources.size > 1) {
                    ScrollableTabRow(selectedTabIndex = tabIds.indexOf(vm.selectedTab).coerceAtLeast(0), edgePadding = Spacing.s16) {
                        tabIds.forEach { id ->
                            val label = if (id == ALL_TAB_ID) stringResource(R.string.home_tab_all) else browseSources.find { it.id == id }?.displayName ?: id
                            Tab(selected = id == vm.selectedTab, onClick = { vm.selectedTab = id }, text = { Text(label) })
                        }
                    }
                }
                if (browseSources.any { it.providesSearchFilters }) {
                    SearchFilterRow(vm.searchFilter, onChange = { vm.applySearchFilter(it, browseSources) })
                }
                when {
                    initialLoading -> {
                        HomeGrid(
                            items = emptyList(),
                            errors = errorRows,
                            hasMore = false,
                            onLoadMore = {},
                            onClick = ::openResult,
                            onLongPress = { actionSheetRef = it },
                            gridState = gridState,
                            modifier = Modifier.weight(1f),
                            skeletonCards = 6,
                        )
                    }
                    searchItems.isEmpty() && errorRows.isEmpty() -> {
                        Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.home_no_results, vm.query), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.s24))
                        }
                    }
                    else -> {
                        HomeGrid(
                            items = longformItems,
                            errors = errorRows,
                            hasMore = hasMore,
                            onLoadMore = { vm.continueTab(feeding) },
                            onClick = ::openResult,
                            onLongPress = { actionSheetRef = it },
                            gridState = gridState,
                            modifier = Modifier.weight(1f),
                            isLoadingMore = isLoadingMore,
                            endOfResults = allExhausted,
                            // Inside the grid, not pinned above it: a pinned shelf would hold
                            // ~270dp hostage for the whole scroll.
                            topContent = if (shortsItems.isEmpty()) null else {
                                { ShortsShelf(shortsItems, onClick = ::openShort) }
                            },
                        )
                    }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    val exploreTopics by app.prefs.exploreTopics.collectAsStateWithLifecycle(initialValue = emptySet())
                    val topics = Topic.entries.filter { it.name in exploreTopics }
                    if (topics.isNotEmpty()) TopicTabRow(topics, selected = vm.selectedTopic, onSelect = { vm.selectTopic(it) })
                    val topic = vm.selectedTopic
                    if (topic == null) {
                        PullToRefreshBox(
                            isRefreshing = vm.feed.loading,
                            onRefresh = { vm.refreshFeed(browseSources) },
                            modifier = Modifier.weight(1f),
                        ) {
                            HomeFeedSection(
                                feed = vm.feed,
                                onClick = ::openResult,
                                onLongPress = { actionSheetRef = it },
                                onRefresh = { vm.refreshFeed(browseSources) },
                                gridState = gridState,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        val result = vm.topicResults[topic] ?: TabResult(topic.label, loading = true)
                        val errors = if (result.error != null) {
                            val retry: (() -> Unit)? = if (result.blocked) null else ({ vm.selectTopic(topic) })
                            listOf(ErrorRow(result.displayName, result.error, onRetry = retry))
                        } else {
                            emptyList()
                        }
                        // Same shelf idiom as search: shorts pulled up top, long-form below.
                        val (topicShorts, topicLongform) = partitionShorts(result.items)
                        HomeGrid(
                            items = topicLongform,
                            errors = errors,
                            hasMore = false,
                            onLoadMore = {},
                            onClick = ::openResult,
                            onLongPress = { actionSheetRef = it },
                            gridState = gridState,
                            modifier = Modifier.weight(1f),
                            skeletonCards = if (result.loading && result.items.isEmpty()) 6 else 0,
                            topContent = if (topicShorts.isEmpty()) null else {
                                { ShortsShelf(topicShorts) { ref -> onOpenShorts(topicShorts, topicShorts.indexOf(ref).coerceAtLeast(0)) } }
                            },
                        )
                    }
                }
            }
        }
    }

    actionSheetRef?.let { ref -> VideoActionSheet(ref, onDismiss = { actionSheetRef = null }) }
    if (!onboardingDone) OnboardingSheet(app.prefs)
}

/** Home's topic tabs: "For you" (the subscriptions feed, [selected] null) then each [Topic] in
 *  declaration order. Underline style; scrolls sideways once the row is wider than the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicTabRow(topics: List<Topic>, selected: Topic?, onSelect: (Topic?) -> Unit) {
    val tabs: List<Topic?> = listOf(null) + topics
    PrimaryScrollableTabRow(
        // A topic switched off in Settings can stay selected; -1 would crash the indicator.
        selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.background,
        edgePadding = Spacing.pageMargin,
    ) {
        tabs.forEach { topic ->
            Tab(
                selected = topic == selected,
                onClick = { onSelect(topic) },
                text = { Text(topic?.label ?: stringResource(R.string.home_for_you), style = MaterialTheme.typography.labelLarge) },
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Home's default view: newest uploads from subscribed channels, filling in per-channel as each
 * fetch returns (never one spinner blocked on the slowest channel). Three honest states beyond
 * the list itself: first load, no subscriptions yet, and "subscribed, but nothing new right now"
 * -- neither empty case reads as an error.
 */
@Composable
private fun HomeFeedSection(
    feed: FeedState,
    onClick: (VideoRef) -> Unit,
    onLongPress: (VideoRef) -> Unit,
    onRefresh: () -> Unit,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    when {
        feed.loading && feed.items.isEmpty() -> {
            HomeGrid(
                items = emptyList(),
                hasMore = false,
                onLoadMore = {},
                onClick = onClick,
                onLongPress = onLongPress,
                gridState = gridState,
                modifier = modifier,
                skeletonCards = 6,
            )
        }
        feed.items.isEmpty() -> {
            val message = when {
                !feed.hasSubscriptions -> stringResource(R.string.home_feed_no_subscriptions)
                // A failed fetch is not evidence that there is nothing new, so say which happened.
                feed.failedChannels > 0 -> pluralStringResource(R.plurals.home_feed_failed_channels, feed.failedChannels, feed.failedChannels)
                else -> stringResource(R.string.home_feed_nothing_new)
            }
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(Spacing.s24),
                )
            }
        }
        else -> {
            HomeGrid(
                items = feed.items,
                hasMore = false,
                onLoadMore = {},
                onClick = onClick,
                onLongPress = onLongPress,
                gridState = gridState,
                modifier = modifier,
                isLoadingMore = feed.loading,
                topContent = { FeedSectionHeader(onRefresh) },
            )
        }
    }
}

/** "New from channels" label with the refresh button on the same line, full width above the grid. */
@Composable
private fun FeedSectionHeader(onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.home_feed_section_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.home_refresh_feed), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Span for header, shelf, error and footer items: one item across every column. */
private val fullRow: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/**
 * Home's result grid: [VideoGridCard]s in 2 columns (4 in landscape), 8 dp gutters. The header,
 * shelf, error rows and footer span every column. Endless scroll and the first-load skeleton work
 * as the list did before.
 */
@Composable
private fun HomeGrid(
    items: List<VideoRef>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onClick: (VideoRef) -> Unit,
    onLongPress: (VideoRef) -> Unit,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    errors: List<ErrorRow> = emptyList(),
    isLoadingMore: Boolean = false,
    endOfResults: Boolean = false,
    skeletonCards: Int = 0,
    topContent: (@Composable () -> Unit)? = null,
) {
    // Fire onLoadMore a few cells before the true bottom so scrolling never stalls.
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val layout = gridState.layoutInfo
            (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, hasMore, isLoadingMore) {
        if (nearEnd && hasMore && !isLoadingMore) onLoadMore()
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (landscape) 4 else 2),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(start = Spacing.pageMargin, end = Spacing.pageMargin, bottom = Spacing.s24),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        topContent?.let { content -> item(key = "topContent", span = fullRow) { content() } }
        errors.forEach { row -> item(span = fullRow) { ErrorRowView(row) } }
        if (items.isEmpty()) items(skeletonCards) { VideoGridSkeletonCard() }
        // Deduped by page URL: the grid's `key` throws on a duplicate, and one video can turn up twice.
        items(items.distinctBy { it.pageUrl }, key = { it.pageUrl }) { ref ->
            VideoGridCard(ref, onClick = { onClick(ref) }, onLongPress = { onLongPress(ref) })
        }
        when {
            isLoadingMore -> item(span = fullRow) { GridLoadingTail() }
            endOfResults && items.isNotEmpty() -> item(span = fullRow) {
                Text(
                    stringResource(R.string.results_end),
                    modifier = Modifier.fillMaxWidth().padding(Spacing.s16),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            hasMore -> item(span = fullRow) {
                TextButton(onClick = onLoadMore, shape = ControlShape, modifier = Modifier.fillMaxWidth().padding(Spacing.s16)) {
                    Text(stringResource(R.string.results_load_more))
                }
            }
        }
    }
}

@Composable
private fun GridLoadingTail() {
    Box(Modifier.fillMaxWidth().padding(Spacing.s16), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(Sizes.icon), strokeWidth = 2.dp)
    }
}

@Composable
private fun SearchHistorySuggestions(
    entries: List<String>,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.s16), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_recent_searches), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(vertical = Spacing.s16))
            IconButton(onClick = onClearAll) { Icon(painterResource(R.drawable.ic_delete_sweep), contentDescription = stringResource(R.string.home_clear_recent_searches), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        entries.take(10).forEach { q ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Sizes.listOneLine).clickable { onPick(q) }.padding(start = Spacing.s16, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(q, modifier = Modifier.weight(1f).padding(start = Spacing.s16, top = 10.dp, bottom = 10.dp))
                IconButton(onClick = { onDelete(q) }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.home_remove_search, q))
                }
            }
        }
    }
}

/** Live autocomplete rows shown while the query is non-blank and not yet submitted -- Search icon
 *  distinguishes these from [SearchHistorySuggestions]'s History-icon rows. No delete affordance:
 *  these aren't stored, there's nothing to remove. */
@Composable
private fun SearchSuggestionsDropdown(suggestions: List<String>, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        suggestions.forEach { q ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Sizes.listOneLine).clickable { onPick(q) }.padding(start = Spacing.s16, end = Spacing.s16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(q, modifier = Modifier.weight(1f).padding(start = Spacing.s16, top = 10.dp, bottom = 10.dp))
            }
        }
    }
}

/** Whether the shared Home list state is held at row 0: only while the feed itself is on screen
 *  and still refreshing (search results and topic lists must keep their own scroll position). */
internal fun shouldPinFeedToTop(feedShowing: Boolean, feedLoading: Boolean): Boolean = feedShowing && feedLoading

/** True only for a URL shape YouTube actually uses for a channel page (/channel/UC…, /@handle,
 *  /c/Name, /user/Name). Conservative on purpose: anything else -- including a parse failure --
 *  stays on the video path, since a wrongly-guessed channel would send a real video into a listing
 *  screen instead of playing it, which is the worse failure of the two. */
internal fun isChannelPageUrl(pageUrl: String): Boolean {
    val uri = runCatching { java.net.URI(pageUrl) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    if (!host.endsWith("youtube.com")) return false
    val path = uri.path ?: return false
    return path.startsWith("/channel/") || path.startsWith("/@") || path.startsWith("/c/") || path.startsWith("/user/")
}

/** Home's app bar: app name, then search (opens the field) and settings. */
@Composable
private fun HomeAppBar(onOpenSearch: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(Spacing.s56).padding(start = Spacing.pageMargin, end = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.home_open_search), tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.shell_tab_settings), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Search field, shown in place of the app bar while search is open. Back arrow closes search;
 * the trailing Clear empties the query and keeps the field open.
 */
@Composable
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(Sizes.searchBar)
            .clip(ControlShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(1f).padding(start = Spacing.s8)) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.home_search_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { onFocusChanged(it.isFocused) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }, onDone = { onSearch() }),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.home_clear_search), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
