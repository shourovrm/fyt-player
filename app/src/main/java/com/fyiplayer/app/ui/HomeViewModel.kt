package com.fyiplayer.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fyiplayer.app.FyiApp
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.core.VideoSource
import com.fyiplayer.app.data.repo.HistoryRepository
import com.fyiplayer.app.data.repo.SearchHistoryRepository
import com.fyiplayer.app.data.repo.SubscriptionRepository
import com.fyiplayer.app.source.newpipe.SearchSuggestions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Home's paging/search state, hoisted into an [AndroidViewModel] so it survives both navigating
 * away to Detail and back (Navigation-Compose keeps a back-stack entry's ViewModelStore alive as
 * long as the entry is never popped -- see AppShell's no-`popUpTo` rule) and a configuration
 * change. `mutableStateMapOf` (Compose runtime, not Compose UI) reads fine from a ViewModel.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FyiApp
    val prefs = app.prefs
    private val searchHistoryRepo = SearchHistoryRepository(app.database.searchHistoryDao())
    private val historyRepo = HistoryRepository(app.database.watchHistoryDao())
    private val subscriptionRepo = SubscriptionRepository(app.database.subscriptionDao())

    var query: String by mutableStateOf("")
    var selectedTab: String by mutableStateOf(ALL_TAB_ID)

    internal val searchResults = mutableStateMapOf<String, TabResult>()

    /** Autocomplete rows for the search field. Empty until [requestSuggestions] lands, or once
     *  [clearSuggestions] runs -- never stale text from a since-abandoned query. */
    internal var suggestions: List<String> by mutableStateOf(emptyList())
        private set
    private var suggestionsJob: Job? = null

    /** Home's default (blank-query) feed: newest uploads from subscribed channels. Cached
     *  for the ViewModel's lifetime -- [loadFeedIfNeeded] never refetches once [FeedState.loaded]
     *  is true; [refreshFeed] is the explicit way back in (a refresh action in HomeScreen). */
    internal var feed: FeedState by mutableStateOf(FeedState())
        private set
    private var feedJob: Job? = null

    // one in-flight job per "search:<id>" key -- refuses a duplicate load and lets a superseded
    // query's jobs be cancelled by prefix.
    private val activeJobs = mutableMapOf<String, Job>()

    fun searchHistory() = searchHistoryRepo.observe()

    private fun cancelJobsWithPrefix(prefix: String) {
        val keys = activeJobs.keys.filter { it.startsWith(prefix) }
        keys.forEach { activeJobs[it]?.cancel() }
        keys.forEach { activeJobs.remove(it) }
    }

    /** First entry into Home (or a source list that changed) only -- a no-op once cached. */
    fun loadFeedIfNeeded(sources: List<VideoSource>) {
        // The enabled-source set is read from DataStore, so the FIRST composition always sees an
        // empty list. Loading then yields an empty feed and latches loaded=true, and the real set
        // arriving a moment later is ignored — which is how the feed silently stayed empty.
        if (sources.isEmpty()) return
        if (feedJob?.isActive == true) return
        val ids = sources.mapTo(HashSet()) { it.id }
        if (feed.loaded && loadedForSourceIds == ids) return
        loadedForSourceIds = ids
        refreshFeed(sources)
    }

    /** Source ids the cached feed was built for, so enabling/disabling a source rebuilds it. */
    private var loadedForSourceIds: Set<String>? = null

    /** Rebuilds the feed from scratch: re-reads subscriptions, re-fetches every channel. */
    fun refreshFeed(sources: List<VideoSource>) {
        feedJob?.cancel()
        val byId = sources.associateBy { it.id }
        feed = FeedState(loading = true)
        feedJob = viewModelScope.launch {
            val channels = capChannels(subscriptionRepo.observeFeedChannels().first())
            if (channels.isEmpty()) {
                feed = FeedState(loaded = true, hasSubscriptions = false)
                return@launch
            }
            val watched = historyRepo.observe().first().mapTo(HashSet()) { it.pageUrl }
            // Mutated only from this coroutine's children, all on the Main dispatcher (viewModelScope's
            // default) -- each child only suspends inside source.listing(), never races another child's
            // write to its own index.
            val outcomes = MutableList<ChannelFetchOutcome>(channels.size) {
                ChannelFetchOutcome.Ok(emptyList())
            }
            val jobs = channels.mapIndexed { i, channel ->
                launch {
                    val src = byId[channel.sourceId]
                    if (src == null) {
                        // Our own lookup failing is a defect, not "this channel has nothing new".
                        runCatching {
                            android.util.Log.d("HomeFeed", "no source for sourceId='${channel.sourceId}'")
                        }
                        outcomes[i] = ChannelFetchOutcome.Failed
                        feed = feed.copy(items = interleave(outcomes.map(::itemsOf)))
                        return@launch
                    }
                    val page = try {
                        src.listing(channel)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        runCatching {
                            android.util.Log.d("HomeFeed", "channel fetch failed: ${e::class.simpleName}")
                        }
                        null
                    }
                    outcomes[i] = if (page == null) {
                        ChannelFetchOutcome.Failed
                    } else {
                        ChannelFetchOutcome.Ok(
                            excludeWatched(page.items, watched).take(FEED_ITEMS_PER_CHANNEL),
                        )
                    }
                    // Append as each channel returns rather than waiting for the slowest one.
                    feed = feed.copy(items = interleave(outcomes.map(::itemsOf)))
                }
            }
            jobs.joinAll()
            feed = feed.copy(
                loading = false,
                loaded = true,
                hasSubscriptions = true,
                failedChannels = outcomes.count { it is ChannelFetchOutcome.Failed },
            )
        }
    }

    fun loadSearch(source: VideoSource, q: String, page: String?) {
        val key = "search:${source.id}"
        if (activeJobs[key]?.isActive == true) return
        val before = searchResults[source.id] ?: TabResult(source.displayName)
        searchResults[source.id] = before.copy(loading = true, error = null)
        activeJobs[key] = viewModelScope.launch {
            val cur = searchResults[source.id] ?: before
            val outcome = runCatching { source.search(q, page) }
                .fold(
                    onSuccess = { applySuccess(cur, page, it) },
                    onFailure = { e -> if (e is CancellationException) throw e else outcomeFor(cur, page, e) },
                )
            // Query-staleness guard: only a write that still belongs to the query on screen lands.
            if (query == q) searchResults[source.id] = outcome
        }
    }

    fun runSearch(q: String, sources: List<VideoSource>) {
        if (q.isBlank()) return
        query = q
        cancelJobsWithPrefix("search:")
        viewModelScope.launch {
            if (prefs.recordSearchHistory.first()) searchHistoryRepo.record(q, sourceId = null)
        }
        searchResults.clear()
        sources.forEach { loadSearch(it, q, null) }
    }

    fun clearSearch() {
        query = ""
        cancelJobsWithPrefix("search:")
        searchResults.clear()
    }

    fun retryTab(source: VideoSource) {
        val page = searchResults[source.id]?.retryPage
        loadSearch(source, query, page)
    }

    fun continueTab(sources: List<VideoSource>) {
        sources.forEach { src ->
            val cur = searchResults[src.id] ?: return@forEach
            if (cur.loading) return@forEach
            val page = cur.nextPage ?: return@forEach
            loadSearch(src, query, page)
        }
    }

    fun deleteSearchHistoryEntry(q: String) {
        viewModelScope.launch { searchHistoryRepo.remove(q) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryRepo.clear() }
    }

    /** Debounced (~300ms): a cancel-and-relaunch Job, same pattern as [refreshFeed]'s [feedJob] --
     *  only the latest query's result is ever applied. */
    fun requestSuggestions(q: String) {
        suggestionsJob?.cancel()
        if (q.isBlank()) { suggestions = emptyList(); return }
        suggestionsJob = viewModelScope.launch {
            delay(300)
            suggestions = SearchSuggestions.fetch(q)
        }
    }

    fun clearSuggestions() {
        suggestionsJob?.cancel()
        suggestions = emptyList()
    }

    private fun outcomeFor(cur: TabResult, page: String?, error: Throwable): TabResult = when (error) {
        is ExtractionError.Unsupported -> applyUnsupported(cur)
        is ExtractionError -> applyError(cur, page, error)
        else -> cur.copy(loading = false, error = "Something went wrong", retryPage = page, nextPage = null, loaded = true)
    }
}
