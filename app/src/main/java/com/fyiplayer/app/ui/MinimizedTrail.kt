package com.fyiplayer.app.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.player.PlaybackSession

/**
 * The Detail pages the minimise chevron took off the back stack, bottom first, so tapping the mini
 * player puts them back and Back walks the same videos again. Memory only.
 *
 * While it is [active] the user is on a static page with the video minimised. Detail and Shorts
 * player entries that Back reaches below that page are skipped: their entry guard would replace the
 * minimised video with their own. A Short the user opens from that page is the exception, see
 * [parkForShorts].
 */
object MinimizedTrail {
    private var pages by mutableStateOf(emptyList<String>())

    /** Pages are held and the mini player still has a video; closing it ends the skipping. */
    val active: Boolean
        get() = pages.isNotEmpty() && PlaybackSession.state.value.queue.isNotEmpty() && !PlaybackSession.hasParked

    /** A Short is being opened: the minimised video steps aside for it and returns, paused, when
     *  Shorts close. Without this the Shorts page would be skipped as a leftover entry. */
    fun parkForShorts() {
        if (active) PlaybackSession.park()
    }

    fun clear() {
        pages = emptyList()
    }

    /** Pops Detail and Shorts player entries down to the nearest static page, keeping the Detail pages. */
    fun minimize(nav: NavController) {
        val popped = mutableListOf<String>()
        while (true) {
            val entry = nav.currentBackStackEntry ?: break
            val route = entry.destination.route
            if (route != Routes.DETAIL && route != Routes.SHORTS_PLAYER) break
            if (!nav.popBackStack()) break
            entry.arguments?.getString("pageUrl")?.let { popped.add(0, Uri.decode(it)) }
        }
        pages = popped
    }

    /** Mini player tap: the minimised chain comes back if [ref] still tops it, else [ref] opens alone. */
    fun reopen(nav: NavController, ref: VideoRef) {
        val chain = pages
        if (chain.lastOrNull() != ref.pageUrl) {
            nav.openDetail(ref)
            return
        }
        clear()
        RefCache.put(ref)
        // Marked as returning, so Back to a lower page resumes its video instead of offering a fresh start.
        for (pageUrl in chain) nav.navigate("detail/${Uri.encode(pageUrl)}?returning=true")
    }
}
