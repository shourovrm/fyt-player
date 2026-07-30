package com.fyiplayer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.VideoSource
import com.fyiplayer.app.player.PlaybackSession
import kotlinx.coroutines.flow.collect

/**
 * Vertical full-screen pager (DESIGN.md §5): one short-form clip per page, swipe up to advance.
 * Playback goes through the one process-scoped [PlaybackSession] -- this file owns no player and
 * no resolver of its own, only the pager <-> session bookkeeping.
 *
 * See [FullBleedBox] for how this copes with `AppScaffold` consuming system-bar insets for the
 * whole app.
 *
 * NOT fixed here, reported instead (out of this task's allowed files): `AppScaffold`'s
 * `isFullPlayerRoute` only matches `detail/...`, so on the Shorts route it still mounts the mini
 * player + queue bar underneath this screen's bottom-nav slot. Both they and this screen's active
 * page call [com.fyiplayer.app.player.SharedVideoSurface] -- "last attacher wins" per that file's
 * own doc -- so the shared surface can get fought over between the mini bar and the pager exactly
 * the way the Gotcha already on file for the detail route describes. `isFullPlayerRoute` needs
 * `Routes.SHORTS` added alongside `detail/` to close this.
 */
@Composable
fun ShortsScreen(onOpenDetail: (String) -> Unit) {
    val app = rememberFyiApp()
    val vm: ShortsViewModel = viewModel()
    val enabledIds by app.prefs.enabledSources.collectAsStateWithLifecycle(initialValue = emptySet())
    val sources = remember(enabledIds) { SourceRegistry.shortsSourcesFor(enabledIds) }

    LaunchedEffect(sources) {
        vm.reset()
        if (sources.isNotEmpty()) vm.loadMore(sources)
    }

    when {
        // Honest gap, not a blank screen: today no registered source opts into shorts (see
        // source/youtube/YoutubeSource.kt's providesShorts comment) so this is the common case.
        sources.isEmpty() -> EmptyStateScreen(
            title = "Shorts",
            message = "No enabled source provides short-form clips yet.",
        )
        vm.items.isEmpty() && vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        vm.items.isEmpty() && vm.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    vm.error.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                TextButton(onClick = { vm.loadMore(sources) }) { Text("Retry") }
            }
        }
        vm.items.isEmpty() -> EmptyStateScreen(title = "Shorts", message = "No short-form clips available right now.")
        else -> ShortsPager(vm = vm, sources = sources, onOpenDetail = onOpenDetail)
    }
}

@Composable
private fun ShortsPager(vm: ShortsViewModel, sources: List<VideoSource>, onOpenDetail: (String) -> Unit) {
    val items = vm.items
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

    // Endless paging: request the next round as the user nears the loaded tail, same threshold
    // shape as ResultsListColumn's.
    val nearEnd by remember(items.size) { derivedStateOf { pagerState.currentPage >= items.size - 3 } }
    LaunchedEffect(nearEnd, vm.loading) {
        if (nearEnd && !vm.loading && vm.feedState.hasMore(sources)) vm.loadMore(sources)
    }

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
