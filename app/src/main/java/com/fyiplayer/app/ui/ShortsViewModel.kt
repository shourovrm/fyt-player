package com.fyiplayer.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fyiplayer.app.FyiApp
import com.fyiplayer.app.core.ChannelTab
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.core.VideoSource
import com.fyiplayer.app.data.repo.HistoryRepository
import com.fyiplayer.app.data.repo.SubscriptionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Shorts feed state, hoisted the same way [HomeViewModel] hoists Home's -- survives navigating to
 * Detail and back as long as the Shorts back-stack entry isn't popped (AppShell's no-`popUpTo`
 * rule), so returning to the tab doesn't restart the feed from empty.
 */
class ShortsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FyiApp
    private val historyRepo = HistoryRepository(app.database.watchHistoryDao())
    private val subscriptionRepo = SubscriptionRepository(app.database.subscriptionDao())

    internal var feed: ShortsFeedState by mutableStateOf(ShortsFeedState())
        private set
    private var feedJob: Job? = null
    private var moreJob: Job? = null

    // Paging state behind [feed]: one cursor per subscribed channel, the seen set read once at
    // refresh, and the source map the cursors resolve against. All rebuilt by refreshFeed. The seen
    // set is NOT re-read mid-session: a clip watched now keeps its place until the next refresh, so
    // nothing moves under the pager.
    private var cursors: List<ChannelShortsCursor> = emptyList()
    private var seenPageUrls: Set<String> = emptySet()
    private var sourcesById: Map<String, VideoSource> = emptyMap()
    private val shuffleKeys = HashMap<String, Double>()

    /** Newest-short page URL -> its upload instant (null = lookup failed). Kept across refreshes so
     *  a channel whose newest short is unchanged is not looked up again. Failed (null) entries are
     *  dropped at each refresh, so a manual refresh retries them once. */
    private val newestDateByPageUrl = HashMap<String, Long?>()

    /** Clips already written to watch history this session, so the pager settling on one clip
     *  twice writes it once. */
    private val recordedThisSession = HashSet<String>()

    /** Which page the pager last settled on -- restored as the pager's `initialPage` so leaving
     *  and returning to the tab doesn't snap back to the first clip, and also what a grid tap
     *  sets before switching [showPlayer] on. */
    var pagerPage: Int by mutableStateOf(0)

    /** Grid (false) or full-screen pager (true). Hoisted here rather than local composable state
     *  so it -- like [pagerPage] -- survives leaving and returning to the tab. */
    var showPlayer: Boolean by mutableStateOf(false)

    /** Source ids the cached feed was built for, so enabling/disabling a source rebuilds it. */
    private var loadedForSourceIds: Set<String>? = null

    /** First entry into Shorts, or a changed source list -- a no-op once cached for that set. */
    fun loadFeedIfNeeded(sources: List<VideoSource>) {
        // The enabled-source set is read from DataStore, so the FIRST composition always sees an
        // empty list. Loading then yields an empty feed and latches loaded=true, and the real set
        // arriving a moment later is ignored — which is exactly how the feed silently stayed empty.
        if (sources.isEmpty()) return
        if (feedJob?.isActive == true) return
        val ids = sources.mapTo(HashSet()) { it.id }
        if (feed.loaded && loadedForSourceIds == ids) return
        loadedForSourceIds = ids
        refreshFeed(sources)
    }

    /** Rebuilds the feed from scratch: re-reads subscriptions, re-fetches every channel's shorts tab. */
    fun refreshFeed(sources: List<VideoSource>) {
        feedJob?.cancel()
        moreJob?.cancel()
        val byId = sources.associateBy { it.id }
        sourcesById = byId
        cursors = emptyList()
        shuffleKeys.clear() // new random order per refresh (app start / pull-to-refresh only)
        // A failed lookup may have been a transient network error, so each refresh retries it once.
        newestDateByPageUrl.values.removeAll { it == null }
        feed = ShortsFeedState(loading = true)
        feedJob = viewModelScope.launch {
            // Every subscription, same as Home: the old newest-8 cap silently left older
            // subscriptions out of the feed entirely (user-reported). The gate bounds the burst.
            val channels = subscriptionRepo.observeFeedChannels().first()
            if (channels.isEmpty()) {
                feed = ShortsFeedState(loaded = true, hasSubscriptions = false)
                return@launch
            }
            seenPageUrls = historyRepo.observe().first().mapTo(HashSet()) { it.pageUrl }
            // Mutated only from this coroutine's children, all on the Main dispatcher
            // (viewModelScope's default) -- same shape as HomeViewModel.refreshFeed.
            val outcomes = MutableList<ChannelFetchOutcome>(channels.size) {
                ChannelFetchOutcome.Ok(emptyList())
            }
            // A channel lands here only once its newest-short date is settled, so no clip is shown
            // in the unknown slot and then moved to its real slot.
            val settled = MutableList<ChannelShorts?>(channels.size) { null }
            val gate = Semaphore(FEED_CONCURRENCY)
            val jobs = channels.mapIndexed { i, channel ->
                launch {
                    val src = byId[channel.sourceId]
                    outcomes[i] = if (src == null) {
                        // A subscription whose source id resolves to nothing is a defect on our
                        // side, not a statement about the channel — never report it as "no shorts".
                        runCatching {
                            android.util.Log.d("ShortsFeed", "no source for sourceId='${channel.sourceId}'")
                        }
                        ChannelFetchOutcome.Failed
                    } else {
                        gate.withPermit {
                            fetchChannelShorts { src.channelTab(channel.key, ChannelTab.SHORTS) }
                        }
                    }
                    val ok = outcomes[i] as? ChannelFetchOutcome.Ok
                    val newest = ok?.items?.firstOrNull()
                    val newestDate = if (src != null && newest != null) {
                        gate.withPermit { newestShortDateMillis(src, newest) }
                    } else {
                        null
                    }
                    if (ok != null) {
                        settled[i] = ChannelShorts(newestDate, ok.items.take(SHORTS_ITEMS_PER_CHANNEL))
                    }
                    val reordered = orderShortsFeed(settled.filterNotNull(), System.currentTimeMillis(), seenPageUrls, shuffleKeys)
                    feed = feed.copy(items = feedAfterLanding(feed.items, reordered, pagerOpen = showPlayer))
                }
            }
            jobs.joinAll()
            // First-page leftovers become each channel's buffer; NoContent/Failed channels start exhausted.
            cursors = channels.mapIndexed { i, channel ->
                val ok = outcomes[i] as? ChannelFetchOutcome.Ok
                ChannelShortsCursor(
                    sourceId = channel.sourceId, key = channel.key,
                    buffer = ok?.items?.drop(SHORTS_ITEMS_PER_CHANNEL).orEmpty(),
                    nextPage = ok?.nextPage, exhausted = ok?.nextPage == null,
                    newestDateMillis = settled[i]?.newestDateMillis,
                )
            }
            feed = feed.copy(
                loading = false,
                loaded = true,
                hasSubscriptions = true,
                channelsWithoutShorts = outcomes.count { it is ChannelFetchOutcome.NoContent },
                failedChannels = outcomes.count { it is ChannelFetchOutcome.Failed },
                hasMore = cursors.any { it.hasMore },
            )
        }
    }

    /** Upload instant of a channel's newest short, from the session cache when that short was
     *  already looked up. One detail call per newest short, never per clip. */
    private suspend fun newestShortDateMillis(source: VideoSource, newest: VideoRef): Long? {
        if (newest.pageUrl in newestDateByPageUrl) return newestDateByPageUrl[newest.pageUrl]
        val dateMillis = lookupUploadMillis { source.detail(newest) }
        newestDateByPageUrl[newest.pageUrl] = dateMillis
        return dateMillis
    }

    /** One more round: [SHORTS_ITEMS_PER_CHANNEL] per channel that still has any, ordered like the
     *  first page and APPENDED -- never re-merged over what's already shown, a reshuffle under the
     *  pager would move the clip the user is watching. */
    fun loadMore() {
        if (!feed.hasMore || feed.loading || feed.loadingMore) return
        if (feedJob?.isActive == true || moreJob?.isActive == true) return
        feed = feed.copy(loadingMore = true)
        moreJob = viewModelScope.launch {
            val gate = Semaphore(FEED_CONCURRENCY)
            val rounds = cursors.map { cursor ->
                async {
                    val src = sourcesById[cursor.sourceId]
                    if (src == null || !cursor.hasMore) emptyList<VideoRef>() to cursor.copy(exhausted = true)
                    else gate.withPermit {
                        cursor.next(SHORTS_ITEMS_PER_CHANNEL) { page -> src.channelTab(cursor.key, ChannelTab.SHORTS, page) }
                    }
                }
            }.awaitAll()
            cursors = rounds.map { it.second }
            val seen = feed.items.mapTo(HashSet()) { it.pageUrl }
            val batches = rounds.map { (served, after) -> ChannelShorts(after.newestDateMillis, served) }
            val fresh = orderShortsFeed(batches, System.currentTimeMillis(), seenPageUrls, shuffleKeys)
                .filter { seen.add(it.pageUrl) }
            feed = feed.copy(items = feed.items + fresh, loadingMore = false, hasMore = cursors.any { it.hasMore })
        }
    }

    /** Writes a short to watch history once per session, when the pager settles on it. Honours the
     *  recordWatchHistory preference as DetailScreen does. A blank title means a bare share link,
     *  which would show as an empty Library row, so it is skipped. */
    fun recordShown(ref: VideoRef) {
        if (ref.title.isBlank() || !recordedThisSession.add(ref.pageUrl)) return
        viewModelScope.launch {
            if (app.prefs.recordWatchHistory.first()) historyRepo.record(ref.copy(isShort = true))
        }
    }
}
