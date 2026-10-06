package com.fyiplayer.app.ui

import com.fyiplayer.app.core.VideoRef

/**
 * Pure decisions behind the shorts pager, kept out of the composable file so they're
 * JVM-testable without a real player, a real pager, or a device.
 */

/** What settling on [page] should do to [com.fyiplayer.app.player.PlaybackSession], given the
 *  session's own current queue [index]. NEXT/PREVIOUS reuse the session's own skip methods (NEXT
 *  reuses the already-resolved prefetch); JUMP is any farther fling and always re-resolves. */
internal enum class ShortsNavAction { NONE, NEXT, PREVIOUS, JUMP }

/** [index] < 0 means the session hasn't started playing this feed yet -- nothing to react to
 *  (avoids racing the initial [com.fyiplayer.app.player.PlaybackSession.play] call on mount). */
internal fun shortsNavAction(page: Int, index: Int): ShortsNavAction = when {
    index < 0 || page == index -> ShortsNavAction.NONE
    page == index + 1 -> ShortsNavAction.NEXT
    page == index - 1 -> ShortsNavAction.PREVIOUS
    else -> ShortsNavAction.JUMP
}

/** A grid-tile tap becomes the pager's `initialPage`. Clamped so a stale index -- the feed
 *  shrank between the tap and this composing, e.g. a refresh landed mid-gesture -- starts the
 *  pager somewhere valid instead of `rememberPagerState` coercing into an empty range. */
internal fun clampGridIndex(index: Int, itemCount: Int): Int =
    if (itemCount <= 0) 0 else index.coerceIn(0, itemCount - 1)

/** position/duration -> the full-screen progress bar's 0..1 fill. A short publishes no duration
 *  and the session may not have measured one from the player yet, so an unknown/zero duration
 *  must never divide -- guard it to an empty bar instead of a crash or a garbage fraction. */
internal fun shortsProgressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

/** Pager page -> session queue index, matched by page URL. The feed re-interleaves as channels
 *  land and the session silently skips duplicates, so page N is not reliably session index N.
 *  Null when the session does not hold that page's clip (yet): the caller does nothing. */
internal fun sessionIndexForPage(sessionQueue: List<VideoRef>, items: List<VideoRef>, page: Int): Int? {
    val pageUrl = items.getOrNull(page)?.pageUrl ?: return null
    return sessionQueue.indexOfFirst { it.pageUrl == pageUrl }.takeIf { it >= 0 }
}

/** Session queue index -> pager page, the inverse of [sessionIndexForPage]. Null when the
 *  session's current clip is not in this feed (a different queue is loaded). */
internal fun pageForSessionIndex(sessionQueue: List<VideoRef>, sessionIndex: Int, items: List<VideoRef>): Int? {
    val pageUrl = sessionQueue.getOrNull(sessionIndex)?.pageUrl ?: return null
    return items.indexOfFirst { it.pageUrl == pageUrl }.takeIf { it >= 0 }
}

/** True when the session already holds this feed: every queued clip is one of [items], in any
 *  order. A first-clip match alone also matched a queue that merely started with the same clip. */
internal fun sessionHoldsFeed(sessionQueue: List<VideoRef>, items: List<VideoRef>): Boolean {
    if (sessionQueue.isEmpty()) return false
    val feedUrls = items.mapTo(HashSet()) { it.pageUrl }
    return sessionQueue.all { it.pageUrl in feedUrls }
}

/** Feed clips the session does not hold yet, in feed order -- what to enqueue as the feed grows. */
internal fun feedItemsMissingFromSession(sessionQueue: List<VideoRef>, items: List<VideoRef>): List<VideoRef> {
    val queuedUrls = sessionQueue.mapTo(HashSet()) { it.pageUrl }
    return items.filter { it.pageUrl !in queuedUrls }
}
