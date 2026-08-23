package com.fyiplayer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * The pager reuses [FullscreenChrome] — the same seam `DetailScreen`'s fullscreen zoom uses — so
 * `AppScaffold` drops the nav bar and its system-bar inset padding for exactly as long as the
 * pager is showing, and hides the mini player/queue bar too (`AppScaffold.isFullPlayerRoute`).
 * The grid gets both back the moment `showPlayer` flips false, same as any other listing route.
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
    // Back out of the pager stops playback too: a vertical clip must not keep playing into the
    // mini player, which would reopen it in the landscape detail player with no swipe navigation.
    BackHandler(enabled = vm.showPlayer) { vm.showPlayer = false; PlaybackSession.clear() }

    val feed = vm.feed
    when {
        sources.isEmpty() -> EmptyStateScreen(
            title = "Shorts",
            message = "Enable a source in Settings to start browsing.",
        )
        // Items first: the pager is the ONLY branch that may reach ShortsPager, so it can never be
        // composed with an empty list (rememberPagerState would coerce into an empty range).
        feed.items.isNotEmpty() -> if (vm.showPlayer) {
            ShortsPager(
                items = feed.items,
                page = vm.pagerPage,
                onPageChange = { vm.pagerPage = it },
                onOpenDetail = onOpenDetail,
                onLoadMore = vm::loadMore,
            )
        } else {
            ShortsGrid(
                items = feed.items,
                gridState = gridState,
                onOpenPlayer = { index ->
                    vm.pagerPage = clampGridIndex(index, feed.items.size)
                    vm.showPlayer = true
                },
                onLongPress = { actionSheetRef = it },
                hasMore = feed.hasMore,
                loadingMore = feed.loadingMore,
                exhausted = feed.exhausted,
                onLoadMore = vm::loadMore,
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

/** [page]/[onPageChange] are hoisted (ShortsViewModel here, route-local state in
 *  [ShortsPlayerScreen]) so the same pager serves both the Shorts tab and any shorts listing. */
@Composable
internal fun ShortsPager(
    items: List<VideoRef>,
    page: Int,
    onPageChange: (Int) -> Unit,
    onOpenDetail: (String) -> Unit,
    /** Called when the swipe nears the end of [items]; no-op for finite listings (channel tab). */
    onLoadMore: () -> Unit = {},
) {
    val playerState by PlaybackSession.state.collectAsState()
    val pagerState = rememberPagerState(initialPage = page.coerceIn(0, items.size - 1)) { items.size }

    // This composable's own lifetime IS "the pager is showing" -- ShortsScreen only reaches this
    // branch while vm.showPlayer is true. Same DisposableEffect shape as DetailScreen's fullscreen
    // toggle: restored false on every exit path, including leaving the Shorts tab entirely.
    DisposableEffect(Unit) {
        FullscreenChrome.acquire()
        onDispose { FullscreenChrome.release() }
    }

    // Start (or resync) the shared session on this feed. A returning composition -- nav back from
    // Detail with nothing else having taken the player -- finds the same queue already loaded and
    // is a no-op; a fresh page loaded by paging is appended, never re-played from the top.
    LaunchedEffect(items) {
        if (items.isEmpty()) return@LaunchedEffect
        val sessionQueue = PlaybackSession.state.value.queue
        val sameFeed = sessionQueue.isNotEmpty() && sessionQueue.first().pageUrl == items.first().pageUrl
        when {
            !sameFeed -> PlaybackSession.play(items, page.coerceIn(items.indices))
            sessionQueue.size < items.size -> items.drop(sessionQueue.size).forEach { PlaybackSession.enqueue(it) }
        }
    }

    // Swipe -> playback. The pager is what the user's finger drives; skipNext/skipPrevious reuse
    // whatever PlaybackSession already resolved one ahead, playAt re-resolves for a farther fling.
    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            onPageChange(page)
            // Near the tail: ask for more. The pageCount lambda above reads items.size live, so
            // appended clips extend the pager in place; LaunchedEffect(items) enqueues them.
            if (page >= items.size - 3) onLoadMore()
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
    // Read the LIVE session index, never the `playerState` snapshot this effect is keyed on: on
    // first composition that snapshot still holds the previous queue's index (Detail's 0, or a
    // prior pager's last page) while play()/playAt() above already moved the session to the
    // tapped page -- following the stale value scrolled the pager to the wrong short, whose
    // settle then JUMPed playback there too. Queue check: never follow a queue that isn't this feed.
    LaunchedEffect(playerState.index) {
        val live = PlaybackSession.state.value
        if (live.queue.firstOrNull()?.pageUrl != items.firstOrNull()?.pageUrl) return@LaunchedEffect
        val idx = live.index
        if (idx in items.indices && pagerState.currentPage != idx) pagerState.animateScrollToPage(idx)
    }

    // FullscreenChrome.active (set above) is what makes AppScaffold stop consuming system-bar
    // insets for this frame, so fillMaxSize here really does reach every edge -- no manual inset
    // math needed on this end.
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
