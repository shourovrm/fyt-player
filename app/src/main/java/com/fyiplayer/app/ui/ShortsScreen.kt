package com.fyiplayer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.player.PlaybackSession
import kotlinx.coroutines.flow.collect

/**
 * The Shorts tab: a thumbnail grid first, not an immediately-playing pager. Tapping a tile opens
 * the full-screen vertical pager (DESIGN.md §5) at that tile, swipe still moves between clips,
 * and back returns to the grid at the same scroll position. Grid vs. pager is local state on
 * [ShortsViewModel] rather than a second nav route -- the NavHost lives in `AppShell.kt`, out of
 * this task's allowed files -- so [BackHandler] below intercepts the system back button only
 * while the pager is showing.
 *
 * Playback goes through the one process-scoped [PlaybackSession] -- this file owns no player and
 * no resolver of its own, only the grid/pager <-> session bookkeeping.
 *
 * The feed itself is composed here, not by any [com.fyiplayer.app.core.VideoSource]: YouTube
 * publishes no global shorts feed (source/youtube/YoutubeSource.kt), so [ShortsViewModel] unions
 * the shorts tab of every subscribed channel instead -- see [ShortsFeed.kt], same shape as
 * [HomeFeed.kt]'s watch-history feed.
 *
 * See [FullBleedBox] for how the full-screen pager copes with `AppScaffold` consuming system-bar
 * insets for the whole app. `AppScaffold.isFullPlayerRoute` already keeps the mini player and
 * queue bar off the whole Shorts route (grid included), so the pager's active page is never
 * fighting anything else for the one shared video surface.
 */
@Composable
fun ShortsScreen(onOpenDetail: (String) -> Unit) {
    val app = rememberFyiApp()
    val vm: ShortsViewModel = viewModel()
    val enabledIds by app.prefs.enabledSources.collectAsStateWithLifecycle(initialValue = emptySet())
    // Every enabled, browsable source -- not shortsSourcesFor: no source needs to opt into a global
    // shorts feed anymore, the feed is composed per subscribed channel regardless.
    val sources = remember(enabledIds) { SourceRegistry.browseSourcesFor(enabledIds) }

    LaunchedEffect(sources) { vm.loadFeedIfNeeded(sources) }

    // Hoisted above the grid/pager branch below, not inside it: a `remember` inside a
    // conditionally-composed branch is torn down every time that branch stops composing, which
    // would reset the grid's scroll position on every trip into the pager and back.
    val gridState = rememberLazyGridState()
    var actionSheetRef by remember { mutableStateOf<VideoRef?>(null) }
    BackHandler(enabled = vm.showPlayer) { vm.showPlayer = false }

    val feed = vm.feed
    when {
        sources.isEmpty() -> EmptyStateScreen(
            title = "Shorts",
            message = "Enable a source in Settings to start browsing.",
        )
        // Items first: the pager is the ONLY branch that may reach ShortsPager, so it can never be
        // composed with an empty list (rememberPagerState would coerce into an empty range).
        feed.items.isNotEmpty() -> if (vm.showPlayer) {
            ShortsPager(vm = vm, onOpenDetail = onOpenDetail)
        } else {
            ShortsGrid(
                items = feed.items,
                gridState = gridState,
                onOpenPlayer = { index ->
                    vm.pagerPage = clampGridIndex(index, feed.items.size)
                    vm.showPlayer = true
                },
                onLongPress = { actionSheetRef = it },
            )
        }
        !feed.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        // Onboarding, not an error: nothing has gone wrong, there's just nothing to build a feed from yet.
        feed.loaded && !feed.hasSubscriptions -> EmptyStateScreen(
            title = "Shorts",
            message = "Your Shorts feed is built from the channels you subscribe to. Subscribe to a channel to see its shorts here.",
        )
        // A fetch failure is NOT evidence that a channel posts no shorts — say which happened.
        feed.loaded && feed.items.isEmpty() && feed.failedChannels > 0 -> EmptyStateScreen(
            title = "Shorts",
            message = if (feed.channelsWithoutShorts == 0) {
                "Couldn't load shorts from your subscriptions. Check your connection and try again."
            } else {
                "Couldn't load shorts from ${feed.failedChannels} of your subscriptions. " +
                    "The rest post no shorts."
            },
        )
        else -> EmptyStateScreen(
            title = "Shorts",
            message = "None of your subscribed channels post Shorts.",
        )
    }

    // Grid long-press -> the same shared action sheet every other result row in the app opens.
    actionSheetRef?.let { ref -> VideoActionSheet(ref, onDismiss = { actionSheetRef = null }) }
}

@Composable
private fun ShortsPager(vm: ShortsViewModel, onOpenDetail: (String) -> Unit) {
    val items = vm.feed.items
    val playerState by PlaybackSession.state.collectAsState()
    val pagerState = rememberPagerState(initialPage = vm.pagerPage.coerceIn(0, items.size - 1)) { items.size }

    // Start (or resync) the shared session on this feed. A returning composition -- nav back from
    // Detail with nothing else having taken the player -- finds the same queue already loaded and
    // is a no-op; a fresh page loaded by paging is appended, never re-played from the top.
    LaunchedEffect(items) {
        if (items.isEmpty()) return@LaunchedEffect
        val sessionQueue = PlaybackSession.state.value.queue
        val sameFeed = sessionQueue.isNotEmpty() && sessionQueue.first().pageUrl == items.first().pageUrl
        when {
            !sameFeed -> PlaybackSession.play(items, vm.pagerPage.coerceIn(items.indices))
            sessionQueue.size < items.size -> items.drop(sessionQueue.size).forEach { PlaybackSession.enqueue(it) }
        }
    }

    // Swipe -> playback. The pager is what the user's finger drives; skipNext/skipPrevious reuse
    // whatever PlaybackSession already resolved one ahead, playAt re-resolves for a farther fling.
    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            vm.pagerPage = page
            if (page !in items.indices) return@collect
            when (shortsNavAction(page, PlaybackSession.state.value.index)) {
                ShortsNavAction.NEXT -> PlaybackSession.skipNext()
                ShortsNavAction.PREVIOUS -> PlaybackSession.skipPrevious()
                ShortsNavAction.JUMP -> PlaybackSession.playAt(page)
                ShortsNavAction.NONE -> {}
            }
        }
    }

    // Playback -> pager. A clip ending auto-advances PlaybackSession's own index (its
    // onPlaybackStateChanged) with nothing driving the pager -- follow it back, per DESIGN.md §8's
    // "a nav route does not track a queue that advances underneath it" (this reads the state
    // directly via collectAsState above; only the *reaction* -- animating the pager -- runs here).
    LaunchedEffect(playerState.index) {
        val idx = playerState.index
        if (idx in items.indices && pagerState.currentPage != idx) pagerState.animateScrollToPage(idx)
    }

    // No endless paging: the feed is one round per subscribed channel (HomeFeed.kt's shape), not
    // a continuation token per source -- refreshFeed()/loadFeedIfNeeded() cover Shorts' whole feed.

    FullBleedBox {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val ref = items[page]
            // Read state.index (collected above), not a mirrored copy: which page is "live" must
            // never lag a frame behind the session or the wrong clip's title flashes.
            ShortsPage(
                ref = ref,
                isActive = page == playerState.index,
                playerState = playerState,
                onOpenDetail = { RefCache.put(ref); onOpenDetail(ref.pageUrl) },
            )
        }
    }
}

/**
 * `AppScaffold` pads the *entire* Scaffold for system-bar insets once, at its own outermost
 * modifier -- by the time this composes, the parent Box has already excluded the status-bar strip
 * from its constraints. `Modifier.size()` would just get coerced straight back into those
 * (DESIGN.md §8); `requiredSize()` is what actually lets this child exceed the parent and paint
 * under the status bar instead. Only the top is compensated: the bottom edge is left at the
 * Scaffold's own content boundary, which sits above its bottom nav bar -- see the KDoc on
 * [ShortsScreen] above for why going further than that needs a change this task could not make.
 */
@Composable
private fun FullBleedBox(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
        Box(Modifier.requiredSize(maxWidth, maxHeight + topInset).offset(y = -topInset)) {
            content()
        }
    }
}
