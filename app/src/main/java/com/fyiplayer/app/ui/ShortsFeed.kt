package com.fyiplayer.app.ui

import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.SearchPage
import com.fyiplayer.app.core.TAB_UNAVAILABLE_PREFIX
import com.fyiplayer.app.core.VideoRef
import kotlinx.coroutines.CancellationException

/**
 * The Shorts feed is the union of the shorts tabs of the channels a user subscribes to -- YouTube
 * publishes no global shorts feed (source/youtube/YoutubeSource.kt), so this composes one here at
 * the UI layer exactly like [HomeFeed.kt] composes Home's feed from watch history.
 * interleave/excludeWatched/capChannels/FEED_ITEMS_PER_CHANNEL already live in HomeFeed.kt (same
 * package) and are reused as-is rather than duplicated.
 */

/** [loading] true while at least one channel's fetch is still in flight -- items fill in
 *  progressively, never a full-screen blocker after the first channel returns.
 *  [hasSubscriptions] separates "nothing subscribed yet" from "subscribed, but none of them post
 *  shorts" so [ShortsScreen] can render the right empty state for each. */
internal data class ShortsFeedState(
    val items: List<VideoRef> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val hasSubscriptions: Boolean = false,
    /** Channels whose shorts tab genuinely does not exist. */
    val channelsWithoutShorts: Int = 0,
    /** Channels whose fetch FAILED. Kept apart from the above because "this channel posts no
     *  shorts" and "we could not reach it" are different facts, and telling the user the first
     *  when the second happened is a lie. */
    val failedChannels: Int = 0,
)



/**
 * One channel's shorts tab, already-watched excluded and capped. Neither failure mode fails the
 * whole feed, but they are reported apart: only a genuine tab-unavailable means "this channel
 * posts no shorts". [fetch] is injected so this stays a pure, network-free unit under test.
 */
internal suspend fun fetchChannelShorts(
    watched: Set<String>,
    fetch: suspend () -> SearchPage,
): ChannelFetchOutcome {
    val page = try {
        fetch()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ExtractionError.Unsupported) {
        // The source marks a missing tab with this prefix; anything else Unsupported is a failure.
        return if (e.message?.startsWith(TAB_UNAVAILABLE_PREFIX) == true) {
            ChannelFetchOutcome.NoContent
        } else {
            ChannelFetchOutcome.Failed
        }
    } catch (e: Exception) {
        // Error CLASS only, never the message — an engine message can echo the channel URL.
        runCatching { android.util.Log.d("ShortsFeed", "channel fetch failed: ${e::class.simpleName}") }
        return ChannelFetchOutcome.Failed
    }
    return ChannelFetchOutcome.Ok(excludeWatched(page.items, watched).take(FEED_ITEMS_PER_CHANNEL))
}
