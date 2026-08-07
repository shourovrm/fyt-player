package com.fyiplayer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.player.PlaybackSession

/** Handoff for [Routes.SHORTS_PLAYER]: a nav route carries only primitives, so the tapped
 *  listing's shorts ride here, [RefCache]-style — written by [openShortsPlayer] immediately
 *  before navigating, read by the route composable. */
internal object ShortsPlayerRequest {
    var items: List<VideoRef> = emptyList()
    var index: Int = 0
}

/**
 * Full-screen shorts pager over any shorts listing (channel Shorts tab today) — the same
 * swipe-up/down [ShortsPager] the Shorts tab uses, as its own nav route because these lists
 * belong to another screen, not to [ShortsViewModel]'s subscription feed.
 *
 * Back stops playback (matching the Shorts tab pager): a vertical clip leaking into the mini
 * player would reopen in the landscape detail player with no swipe navigation.
 */
@Composable
fun ShortsPlayerScreen(onOpenDetail: (String) -> Unit, onClose: () -> Unit) {
    val items = ShortsPlayerRequest.items
    var page by rememberSaveable { mutableStateOf(ShortsPlayerRequest.index) }
    BackHandler { PlaybackSession.clear(); onClose() }
    if (items.isEmpty()) {
        // Handoff lost (process death restored this route without its list): nothing to page.
        LaunchedEffect(Unit) { onClose() }
        return
    }
    ShortsPager(items = items, page = page, onPageChange = { page = it }, onOpenDetail = onOpenDetail)
}
