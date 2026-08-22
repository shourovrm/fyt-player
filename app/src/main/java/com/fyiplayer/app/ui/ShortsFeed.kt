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
    /** A load-more round is in flight (distinct from [loading], the first-page fan-out). */
    val loadingMore: Boolean = false,
    /** At least one channel still has a continuation or buffered items -- false = honest end. */
    val hasMore: Boolean = false,
) {
    val exhausted: Boolean get() = loaded && !loading && !loadingMore && !hasMore && items.isNotEmpty()
}

/**
 * Where one channel's shorts tab paging stands. [buffer] holds fetched-but-not-yet-shown items
 * (a tab page is ~48 clips; the feed serves [FEED_ITEMS_PER_CHANNEL] per round so every channel
 * keeps its round-robin slot). [nextPage] null with [exhausted] false = first page not fetched yet.
 */
internal data class ChannelShortsCursor(
    val sourceId: String,
    val key: String,
    val buffer: List<VideoRef> = emptyList(),
    val nextPage: String? = null,
    val exhausted: Boolean = false,
) {
    val hasMore: Boolean get() = buffer.isNotEmpty() || !exhausted
}

/** Refills per round: watched-exclusion can empty a whole page, so one retry -- never a storm. */
internal const val MAX_REFILLS_PER_ROUND = 2

/**
 * One load-more round for one channel: serve up to [n] from the buffer, refilling from the tab's
 * continuation when it runs short. A fetch failure ends this channel's paging for the session
 * (already-buffered items still serve out); a continuation that hands back its own token is
 * treated as the end, or it would loop forever. Pure: [fetch] is injected.
 */
internal suspend fun ChannelShortsCursor.next(
    n: Int,
    watched: Set<String>,
    fetch: suspend (String?) -> SearchPage,
): Pair<List<VideoRef>, ChannelShortsCursor> {
    var cur = this
    var refills = 0
    while (cur.buffer.size < n && !cur.exhausted && refills < MAX_REFILLS_PER_ROUND) {
        refills++
        val page = try {
            fetch(cur.nextPage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { android.util.Log.d("ShortsFeed", "channel page failed: ${e::class.simpleName}") }
            cur = cur.copy(exhausted = true)
            break
        }
        val seen = cur.buffer.mapTo(HashSet()) { it.pageUrl }
        val fresh = excludeWatched(page.items, watched).filter { seen.add(it.pageUrl) }
        val token = page.nextPage?.takeIf { it != cur.nextPage }
        cur = cur.copy(buffer = cur.buffer + fresh, nextPage = token, exhausted = token == null)
    }
    return cur.buffer.take(n) to cur.copy(buffer = cur.buffer.drop(n))
}

/**
 * One channel's shorts tab first page, already-watched excluded -- NOT capped: the caller shows
 * [FEED_ITEMS_PER_CHANNEL] and buffers the rest in a [ChannelShortsCursor]. Neither failure mode
 * fails the whole feed, but they are reported apart: only a genuine tab-unavailable means "this
 * channel posts no shorts". [fetch] is injected so this stays a pure, network-free unit under test.
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
    return ChannelFetchOutcome.Ok(excludeWatched(page.items, watched), page.nextPage)
}
