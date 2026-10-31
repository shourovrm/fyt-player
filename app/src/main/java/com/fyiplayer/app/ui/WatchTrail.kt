package com.fyiplayer.app.ui

import com.fyiplayer.app.core.VideoRef

/**
 * Browser-style forward history for videos opened one after another through Detail pages, so the
 * single-video player's Previous and Next can walk them. Immutable: each change returns a new value,
 * so AppShell keeps it in Compose state and the transport buttons recompose when it changes.
 *
 * [forward] holds videos that Previous stepped away from, oldest first; Next takes the newest.
 */
data class WatchTrail(
    val forward: List<VideoRef> = emptyList(),
    /** Back stack depth at the last observed change; a rise in depth is a push. */
    val knownDepth: Int = 0,
    /** Set by [popForward] so the push it causes keeps the rest of [forward]. */
    val awaitingTrailPush: Boolean = false,
) {
    /** The video Next opens from the forward list, or null when there is nothing to go forward to. */
    fun nextForward(): VideoRef? = forward.lastOrNull()

    /**
     * What Next opens on a single-video page: the forward trail first, then the first Similar video
     * but only while autoplay is on. With autoplay off and no trail, Next is hidden, not guessed.
     */
    fun nextTarget(similar: VideoRef?, autoplayNext: Boolean): VideoRef? =
        nextForward() ?: similar.takeIf { autoplayNext }

    /** Previous leaves [ref] behind, so it becomes the newest forward entry. */
    fun recordLeft(ref: VideoRef): WatchTrail = copy(forward = forward + ref)

    /** Next removes the newest forward entry; the push it causes must not clear the rest. */
    fun popForward(): WatchTrail = copy(forward = forward.dropLast(1), awaitingTrailPush = true)

    /** X and the minimise chevron end the trail, because they leave the video the trail was built on. */
    fun cleared(): WatchTrail = copy(forward = emptyList(), awaitingTrailPush = false)

    /**
     * A rise in depth is a push. A push the trail started keeps the forward list; any other push is a
     * video opened by other means, which ends it. A pop keeps it, so Previous can be undone by Next.
     */
    fun onBackStackDepthChanged(newDepth: Int): WatchTrail {
        val isPush = newDepth > knownDepth
        val keepForward = !isPush || awaitingTrailPush
        return copy(
            forward = if (keepForward) forward else emptyList(),
            knownDepth = newDepth,
            awaitingTrailPush = false,
        )
    }

    companion object {
        /** Previous is offered only when the entry directly below the current one is a Detail page. */
        fun canGoPrevious(routeBelowCurrent: String?): Boolean = routeBelowCurrent == Routes.DETAIL
    }
}
