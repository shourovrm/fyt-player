package com.fyiplayer.app.ui

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
