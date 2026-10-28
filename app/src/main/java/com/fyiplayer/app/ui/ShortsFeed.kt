package com.fyiplayer.app.ui

import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.SearchPage
import com.fyiplayer.app.core.TAB_UNAVAILABLE_PREFIX
import com.fyiplayer.app.core.VideoDetail
import com.fyiplayer.app.core.VideoRef
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.random.Random

/**
 * The Shorts feed is the union of the shorts tabs of the channels a user subscribes to -- YouTube
 * publishes no global shorts feed (source/youtube/YoutubeSource.kt), so this composes one here at
 * the UI layer exactly like [HomeFeed.kt] composes Home's feed from watch history.
 * Shorts does NOT use excludeWatched: seen clips are ordered last instead (see [orderShortsFeed]).
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
 * (a tab page is ~48 clips; the feed serves [SHORTS_ITEMS_PER_CHANNEL] per round so every channel
 * keeps its place in the feed). [nextPage] null with [exhausted] false = first page not fetched yet.
 * [newestDateMillis] is the upload instant of the channel's newest short, carried so load-more
 * rounds land in the same recency slot as the first page.
 */
internal data class ChannelShortsCursor(
    val sourceId: String,
    val key: String,
    val buffer: List<VideoRef> = emptyList(),
    val nextPage: String? = null,
    val exhausted: Boolean = false,
    val newestDateMillis: Long? = null,
) {
    val hasMore: Boolean get() = buffer.isNotEmpty() || !exhausted
}

/** Clips per channel per round. The Shorts tab carries no upload date per clip, so a channel's
 *  date comes from one detail lookup of its newest short (see [lookupUploadMillis]). */
internal const val SHORTS_ITEMS_PER_CHANNEL = 4

/** Width of one recency slot: three days. Channels whose newest short is at most three days old
 *  share slot 0; each further three days is the next slot. */
internal const val SHORTS_SLOT_MILLIS = 3L * 24 * 60 * 60 * 1000

/** Slot of a channel whose newest short has no known date. Larger than every dated slot, so such
 *  channels come last: a missing date must never promote a channel. */
internal const val UNKNOWN_DATE_SLOT = Int.MAX_VALUE

/**
 * Slot index of a channel from the age of its newest short. Slot 0 is ages up to three days; slot k
 * is ages in (3k, 3k+3] days, so exactly six days old is slot 1. Written as ceiling(age / slot) - 1
 * in integers to avoid floating-point edge cases at the boundaries. Negative ages (a clock behind
 * the server) fall into slot 0.
 */
internal fun shortsSlotFor(newestDateMillis: Long?, nowMillis: Long): Int {
    if (newestDateMillis == null) return UNKNOWN_DATE_SLOT
    val ageMillis = nowMillis - newestDateMillis
    if (ageMillis <= SHORTS_SLOT_MILLIS) return 0
    return ((ageMillis + SHORTS_SLOT_MILLIS - 1) / SHORTS_SLOT_MILLIS - 1).toInt()
}

/** One channel's clips for the feed: [shorts] are its first-page clips in tab order, and
 *  [newestDateMillis] is the upload instant of its newest short (null when unknown). */
internal data class ChannelShorts(val newestDateMillis: Long?, val shorts: List<VideoRef>)

/**
 * The Shorts feed order. Channels are grouped by the recency slot of their newest short, and slots
 * are emitted from newest to oldest. Within a slot, unseen clips come first and seen clips last,
 * because a clip already watched should not block a fresh one, but it stays in the feed rather
 * than vanishing. Each part is shuffled by the per-session keys in [keys]: the order is random
 * per session, and a repeat call never reorders clips whose keys were already drawn. A clip listed
 * by two channels keeps the first channel's slot.
 */
internal fun orderShortsFeed(
    channels: List<ChannelShorts>,
    nowMillis: Long,
    seenPageUrls: Set<String>,
    keys: MutableMap<String, Double>,
    random: Random = Random.Default,
): List<VideoRef> {
    val clipsBySlot = sortedMapOf<Int, MutableList<VideoRef>>()
    val placed = HashSet<String>()
    for (channel in channels) {
        val clips = clipsBySlot.getOrPut(shortsSlotFor(channel.newestDateMillis, nowMillis)) { mutableListOf() }
        for (clip in channel.shorts) {
            if (placed.add(clip.pageUrl)) clips.add(clip)
        }
    }
    return clipsBySlot.values.flatMap { clips -> orderWithinSlot(clips, seenPageUrls, keys, random) }
}

/** Unseen clips shuffled, then seen clips shuffled. Keys are drawn for every clip before sorting,
 *  so the draw order does not depend on the sort algorithm. */
private fun orderWithinSlot(
    clips: List<VideoRef>,
    seenPageUrls: Set<String>,
    keys: MutableMap<String, Double>,
    random: Random,
): List<VideoRef> {
    val keyed = clips.map { clip -> clip to keys.getOrPut(clip.pageUrl) { random.nextDouble() } }
    val (seen, unseen) = keyed.partition { (clip, _) -> clip.pageUrl in seenPageUrls }
    val unseenInOrder = unseen.sortedBy { (_, key) -> key }.map { (clip, _) -> clip }
    val seenInOrder = seen.sortedBy { (_, key) -> key }.map { (clip, _) -> clip }
    return unseenInOrder + seenInOrder
}

/**
 * Upload instant in epoch millis from the date text a [VideoDetail] carries, or null when the text
 * is absent or in a shape not listed here. yt-dlp gives `YYYYMMDD`, read as UTC midnight, so an
 * upload near a day boundary can land up to a day off. NewPipe gives the microformat date, ISO-8601
 * with the uploader's offset (or `...Z`). Other shapes, such as "3 days ago" or "Oct 5, 2026", are null: a guessed
 * date would put a channel in the wrong slot silently.
 */
internal fun parseUploadEpochMs(uploadDate: String?): Long? {
    val text = uploadDate?.trim().orEmpty()
    return try {
        when {
            text.length == 8 -> LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            text.contains('T') -> OffsetDateTime.parse(text).toInstant().toEpochMilli()
            else -> null
        }
    } catch (e: DateTimeParseException) {
        null
    }
}

/**
 * Upload instant of one short from its own detail page, or null when the lookup fails. A failure is
 * "date unknown" and is never retried here: the caller caches the null. Logged by error class only,
 * because an engine message can echo the page URL. Cancellation passes through.
 */
internal suspend fun lookupUploadMillis(fetchDetail: suspend () -> VideoDetail): Long? =
    try {
        parseUploadEpochMs(fetchDetail().uploadDate)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        runCatching { android.util.Log.d("ShortsFeed", "newest-short date lookup failed: ${e::class.simpleName}") }
        null
    }

/** Refills per round: a page whose clips are all already buffered adds nothing, so one retry -- never a storm. */
internal const val MAX_REFILLS_PER_ROUND = 2

/**
 * One load-more round for one channel: serve up to [n] from the buffer, refilling from the tab's
 * continuation when it runs short. A fetch failure ends this channel's paging for the session
 * (already-buffered items still serve out); a continuation that hands back its own token is
 * treated as the end, or it would loop forever. Pure: [fetch] is injected.
 */
internal suspend fun ChannelShortsCursor.next(
    n: Int,
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
        val fresh = page.items.filter { seen.add(it.pageUrl) }
        val token = page.nextPage?.takeIf { it != cur.nextPage }
        cur = cur.copy(buffer = cur.buffer + fresh, nextPage = token, exhausted = token == null)
    }
    return cur.buffer.take(n) to cur.copy(buffer = cur.buffer.drop(n))
}

/**
 * One channel's shorts tab first page, in tab order -- NOT capped and NOT filtered: the caller shows
 * [SHORTS_ITEMS_PER_CHANNEL], buffers the rest in a [ChannelShortsCursor], and uses the first clip as
 * the channel's newest short. Neither failure mode fails the whole feed, but they are reported apart:
 * only a genuine tab-unavailable means "this channel posts no shorts". [fetch] is injected so this
 * stays a pure, network-free unit under test.
 */
internal suspend fun fetchChannelShorts(
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
    return ChannelFetchOutcome.Ok(page.items, page.nextPage)
}
