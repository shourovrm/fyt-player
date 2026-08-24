package com.fyiplayer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.Topic
import com.fyiplayer.app.settings.OnboardingSheet
import com.fyiplayer.app.core.VideoRef

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
) {
    val app = rememberFyiApp()
    val vm: HomeViewModel = viewModel()
    val listState = rememberLazyListState()
    val enabledIds by app.prefs.enabledSources.collectAsStateWithLifecycle(initialValue = emptySet())
    val browseSources = remember(enabledIds) { SourceRegistry.browseSourcesFor(enabledIds) }
    val searchHistory by vm.searchHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    var searchFieldFocused by remember { mutableStateOf(false) }
    var actionSheetRef by remember { mutableStateOf<VideoRef?>(null) }
    val onboardingDone by app.prefs.onboardingDone.collectAsStateWithLifecycle(initialValue = true)

    val isSearching = vm.query.isNotBlank()
    val focusManager = LocalFocusManager.current
    // Every other screen exits on back; Home has no screen "above" search to pop to, so back must
    // leave search mode instead of falling through to the Activity's default (closing the app).
    BackHandler(enabled = isSearching) {
        vm.clearSearch()
        focusManager.clearFocus()
    }
    // Suggestion fetch tracks the query while the field has focus -- requestSuggestions debounces
    // internally, and a blank query (or losing focus) drops any suggestions on screen.
    LaunchedEffect(vm.query, searchFieldFocused) {
        if (searchFieldFocused && vm.query.isNotBlank()) vm.requestSuggestions(vm.query) else vm.clearSuggestions()
    }

    val tabIds = remember(browseSources) { listOf(ALL_TAB_ID) + browseSources.map { it.id } }
    LaunchedEffect(tabIds) { vm.selectedTab = resolveSelectedTab(vm.selectedTab, tabIds) }
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
    val allExhausted = feeding.isNotEmpty() && feedingResults.all { it.exhausted }
    val errorRows = feeding.zip(feedingResults).mapNotNull { (src, res) ->
        when {
            res.blocked -> ErrorRow(src.displayName, res.error ?: "Unavailable", onRetry = null)
            res.error != null -> ErrorRow(src.displayName, res.error, onRetry = { vm.retryTab(src) })
            else -> null
        }
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
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchPill(
                query = vm.query,
                onQueryChange = { vm.query = it },
                onFocusChanged = { searchFieldFocused = it },
                onClear = { vm.clearSearch() },
                onSearch = { vm.runSearch(vm.query, browseSources) },
                modifier = Modifier.weight(1f),
            )
            if (!isSearching) {
                IconButton(onClick = { vm.refreshFeed(browseSources) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh feed", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        when {
            browseSources.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Enable a source in Settings to start browsing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            vm.query.isBlank() && searchFieldFocused && searchHistory.isNotEmpty() -> {
                SearchHistorySuggestions(
                    entries = searchHistory.map { it.query },
                    onPick = { vm.runSearch(it, browseSources) },
                    onDelete = { vm.deleteSearchHistoryEntry(it) },
                    onClearAll = { vm.clearSearchHistory() },
                )
            }
            // Typed but not yet submitted: searchResults is still the empty map runSearch hasn't
            // populated yet, so show live suggestions here instead of a "No results" flash.
            searchFieldFocused && vm.query.isNotBlank() && vm.searchResults.isEmpty() -> {
                SearchSuggestionsDropdown(suggestions = vm.suggestions, onPick = { vm.runSearch(it, browseSources) })
            }
            isSearching -> {
                // tabIds always carries a synthetic "All" entry on top of browseSources, so
                // tabIds.size is never 1 even with a single source -- gate on the real count.
                if (browseSources.size > 1) {
                    ScrollableTabRow(selectedTabIndex = tabIds.indexOf(vm.selectedTab).coerceAtLeast(0), edgePadding = 12.dp) {
                        tabIds.forEach { id ->
                            val label = if (id == ALL_TAB_ID) "All" else browseSources.find { it.id == id }?.displayName ?: id
                            Tab(selected = id == vm.selectedTab, onClick = { vm.selectedTab = id }, text = { Text(label) })
                        }
                    }
                }
                when {
                    initialLoading -> {
                        ResultsListColumn(
                            items = emptyList(),
                            errors = errorRows,
                            hasMore = false,
                            onLoadMore = {},
                            onClick = ::openResult,
                            onLongPress = { actionSheetRef = it },
                            listState = listState,
                            modifier = Modifier.weight(1f),
                            skeletonRows = 6,
                        )
                    }
                    searchItems.isEmpty() && errorRows.isEmpty() -> {
                        Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No results for “${vm.query}”.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                        }
                    }
                    else -> {
                        ResultsListColumn(
                            items = longformItems,
                            errors = errorRows,
                            hasMore = hasMore,
                            onLoadMore = { vm.continueTab(feeding) },
                            onClick = ::openResult,
                            onLongPress = { actionSheetRef = it },
                            listState = listState,
                            modifier = Modifier.weight(1f),
                            isLoadingMore = isLoadingMore,
                            endOfResults = allExhausted,
                            // Inside the list, not pinned above it: a pinned shelf would hold
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
                    if (topics.isNotEmpty()) TopicChipsRow(topics, selected = vm.selectedTopic, onSelect = { vm.selectTopic(it) })
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
                                listState = listState,
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
                        ResultsListColumn(
                            items = result.items,
                            errors = errors,
                            hasMore = false,
                            onLoadMore = {},
                            onClick = ::openResult,
                            onLongPress = { actionSheetRef = it },
                            listState = listState,
                            modifier = Modifier.weight(1f),
                            skeletonRows = if (result.loading && result.items.isEmpty()) 6 else 0,
                        )
                    }
                }
            }
        }
    }

    actionSheetRef?.let { ref -> VideoActionSheet(ref, onDismiss = { actionSheetRef = null }) }
    if (!onboardingDone) OnboardingSheet(app.prefs)
}

/** Home's "Explore" chips: "For you" (the subscriptions feed, [selected] null) then each [Topic]
 *  in declaration order. Horizontally scrollable -- four topics already crowd a phone width once
 *  "For you" is counted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicChipsRow(topics: List<Topic>, selected: Topic?, onSelect: (Topic?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("For you") })
        topics.forEach { topic ->
            FilterChip(selected = selected == topic, onClick = { onSelect(topic) }, label = { Text(topic.label) })
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
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    when {
        feed.loading && feed.items.isEmpty() -> {
            ResultsListColumn(
                items = emptyList(),
                hasMore = false,
                onLoadMore = {},
                onClick = onClick,
                onLongPress = onLongPress,
                listState = listState,
                modifier = modifier,
                skeletonRows = 6,
            )
        }
        feed.items.isEmpty() -> {
            val message = when {
                !feed.hasSubscriptions ->
                    "Your feed is empty. Search for a channel, open it, and subscribe to build it."
                // A failed fetch is not evidence that there is nothing new — say which happened.
                feed.failedChannels > 0 ->
                    "Couldn't load uploads from ${feed.failedChannels} of your subscriptions. " +
                        "Check your connection and pull refresh."
                else -> "No new uploads from your subscriptions right now."
            }
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        else -> {
            ResultsListColumn(
                items = feed.items,
                hasMore = false,
                onLoadMore = {},
                onClick = onClick,
                onLongPress = onLongPress,
                listState = listState,
                modifier = modifier,
                isLoadingMore = feed.loading,
            )
        }
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
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent searches", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            TextButton(onClick = onClearAll) { Text("Clear all") }
        }
        entries.take(10).forEach { q ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(q) }.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(q, modifier = Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
                IconButton(onClick = { onDelete(q) }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Remove \"$q\" from history")
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
                Modifier.fillMaxWidth().clickable { onPick(q) }.padding(start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(q, modifier = Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
            }
        }
    }
}

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

/** Search-first top bar: one 48dp filled search pill. */
@Composable
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (query.isEmpty()) {
                Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChanged(it.isFocused) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }, onDone = { onSearch() }),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
