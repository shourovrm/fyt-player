package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.player.PlaybackSession

/**
 * Home (browse) and Search share this screen: a blank query shows [HomeFeedSection] (newest
 * uploads from watched channels), a non-blank query searches every enabled source instead. With
 * exactly one source enabled today the search tab row is skipped entirely (DESIGN.md §5) rather
 * than showing a lone, pointless "All" pill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenDetail: (VideoRef) -> Unit) {
    val app = rememberFyiApp()
    val vm: HomeViewModel = viewModel()
    val listState = rememberLazyListState()
    val enabledIds by app.prefs.enabledSources.collectAsStateWithLifecycle(initialValue = emptySet())
    val browseSources = remember(enabledIds) { SourceRegistry.browseSourcesFor(enabledIds) }
    val searchHistory by vm.searchHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    var searchFieldFocused by remember { mutableStateOf(false) }
    var actionSheetRef by remember { mutableStateOf<VideoRef?>(null) }

    val isSearching = vm.query.isNotBlank()

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
    val initialLoading = searchItems.isEmpty() && feedingResults.any { it.loading }
    val isLoadingMore = !initialLoading && feedingResults.any { it.loading }
    val hasMore = feedingResults.any { it.canContinue }
    val allExhausted = feeding.isNotEmpty() && feedingResults.all { it.exhausted }
    val errorRows = feeding.zip(feedingResults).mapNotNull { (src, res) ->
        when {
            res.blocked -> ErrorRow(src.displayName, res.error ?: "Unavailable", onRetry = null)
            res.error != null -> ErrorRow(src.displayName, res.error, onRetry = { vm.retryTab(src) })
            else -> null
        }
    }

    // Playback queue is whichever list is actually on screen -- the search results, or Home's feed.
    val displayedItems = if (isSearching) searchItems else vm.feed.items
    fun playAndOpen(ref: VideoRef) {
        val index = displayedItems.indexOfFirst { it.pageUrl == ref.pageUrl }.coerceAtLeast(0)
        PlaybackSession.play(displayedItems, index)
        onOpenDetail(ref)
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
                )
            }
            isSearching -> {
                if (tabIds.size > 1) {
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
                            onClick = ::playAndOpen,
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
                            items = searchItems,
                            errors = errorRows,
                            hasMore = hasMore,
                            onLoadMore = { vm.continueTab(feeding) },
                            onClick = ::playAndOpen,
                            onLongPress = { actionSheetRef = it },
                            listState = listState,
                            modifier = Modifier.weight(1f),
                            isLoadingMore = isLoadingMore,
                            endOfResults = allExhausted,
                        )
                    }
                }
            }
            else -> HomeFeedSection(
                feed = vm.feed,
                onClick = ::playAndOpen,
                onLongPress = { actionSheetRef = it },
                listState = listState,
                modifier = Modifier.weight(1f),
            )
        }
    }

    actionSheetRef?.let { ref -> VideoActionSheet(ref, onDismiss = { actionSheetRef = null }) }
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
            val message = if (feed.hasSubscriptions) {
                "No new uploads from your subscriptions right now."
            } else {
                "Your feed is empty. Search for a channel, open it, and subscribe to build it."
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
private fun SearchHistorySuggestions(entries: List<String>, onPick: (String) -> Unit, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text("Recent searches", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp))
        entries.take(10).forEach { q ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(q) }.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(q, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                IconButton(onClick = { onDelete(q) }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Remove \"$q\" from history")
                }
            }
        }
    }
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
