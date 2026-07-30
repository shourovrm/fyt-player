package com.fyiplayer.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fyiplayer.app.FyiApp
import com.fyiplayer.app.core.ChannelTab
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.TAB_UNAVAILABLE_PREFIX
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.core.VideoSource
import com.fyiplayer.app.data.repo.SubscriptionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** UI-only tab identity: the five real [ChannelTab]s plus in-channel search, which has no
 *  engine-side counterpart -- it is driven by a query, not a page token off a fixed feed. */
sealed class ChannelUiTab {
    data class Content(val tab: ChannelTab) : ChannelUiTab()
    data object Search : ChannelUiTab()
}

/** One page-able list of videos: Videos/Shorts/Live tabs and in-channel search share this shape. */
internal data class VideoTabState(
    val items: List<VideoRef> = emptyList(),
    val nextPage: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val blocked: Boolean = false,
    val loaded: Boolean = false,
)

/** One page-able list of containers: Playlists/Courses tabs hold [Listing]s, not videos. */
internal data class ContainerTabState(
    val items: List<Listing> = emptyList(),
    val nextPage: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val blocked: Boolean = false,
    val loaded: Boolean = false,
)

/** [ChannelTab.PLAYLISTS]/[ChannelTab.COURSES] hold containers; the rest hold video entries. */
internal val CONTAINER_TABS: Set<ChannelTab> = setOf(ChannelTab.PLAYLISTS, ChannelTab.COURSES)

/** True when [error] is the engine's "this channel has no such tab" signal (Contracts.kt's
 *  [TAB_UNAVAILABLE_PREFIX]) -- the tab must disappear, never render as an error row. */
internal fun isTabUnavailable(error: Throwable): Boolean =
    error is ExtractionError.Unsupported && error.message?.startsWith(TAB_UNAVAILABLE_PREFIX) == true

/** Drops [tab] from [current] only on the tab-unavailable signal; any other error (network,
 *  access wall) leaves the tab in place so a transient failure never deletes a real tab. */
internal fun availableTabsAfter(current: List<ChannelTab>, tab: ChannelTab, error: Throwable): List<ChannelTab> =
    if (isTabUnavailable(error)) current - tab else current

/** Appends only genuinely-new items (by key). The same video/container can legitimately repeat
 *  across pages -- a feed shifting underneath paging -- so a straight concat would duplicate rows. */
internal fun <T> dedupeAppend(existing: List<T>, incoming: List<T>, keyOf: (T) -> String): List<T> {
    val seen = existing.mapTo(HashSet(), keyOf)
    return existing + incoming.filter { seen.add(keyOf(it)) }
}

/** A source that keeps returning a next-page token while handing back zero new items would page
 *  forever; treat zero fresh items as the real end regardless of what the token claims. */
internal fun nextPageToken(serverNextPage: String?, freshCount: Int): String? =
    if (freshCount == 0) null else serverNextPage

/** A blank query is not a "browse everything in this channel" request -- never sent. */
internal fun shouldSearchChannel(query: String): Boolean = query.isNotBlank()

/**
 * Per-channel tab state: each [ChannelTab] (plus in-channel search) loads on first selection and
 * is cached in these maps for the ViewModel's lifetime -- switching tabs never refetches. A tab
 * that comes back tab-unavailable is dropped from [availableTabs] and remembered dropped for the
 * rest of this screen's life (see [availableTabsAfter]); it is never re-probed.
 */
class ChannelViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FyiApp
    private val subscriptionRepo = SubscriptionRepository(app.database.subscriptionDao())

    var availableTabs: List<ChannelTab> by mutableStateOf(ChannelTab.entries)
        private set
    var selected: ChannelUiTab by mutableStateOf(ChannelUiTab.Content(ChannelTab.VIDEOS))

    private val videoTabs = mutableStateMapOf<ChannelTab, VideoTabState>()
    private val containerTabs = mutableStateMapOf<ChannelTab, ContainerTabState>()

    var searchQuery: String by mutableStateOf("")
    internal var searchState: VideoTabState by mutableStateOf(VideoTabState())
        private set

    private var loadedFor: Listing? = null
    private val jobs = mutableMapOf<String, Job>()

    fun isSubscribed(channelUrl: String): Flow<Boolean> = subscriptionRepo.isSubscribed(channelUrl)

    fun toggleSubscription(listing: Listing) {
        viewModelScope.launch { subscriptionRepo.toggle(listing.key, listing.sourceId, listing.title) }
    }

    internal fun videoTab(tab: ChannelTab): VideoTabState = videoTabs[tab] ?: VideoTabState()
    internal fun containerTab(tab: ChannelTab): ContainerTabState = containerTabs[tab] ?: ContainerTabState()

    /** Called once per screen entry (LaunchedEffect keyed on the listing). Resets everything only
     *  when the listing actually changed -- a recomposition must not blow away cached tabs. */
    fun ensureChannel(listing: Listing) {
        if (loadedFor == listing) return
        loadedFor = listing
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        availableTabs = ChannelTab.entries
        selected = ChannelUiTab.Content(ChannelTab.VIDEOS)
        videoTabs.clear()
        containerTabs.clear()
        searchQuery = ""
        searchState = VideoTabState()
        selectTab(listing, ChannelUiTab.Content(ChannelTab.VIDEOS))
    }

    fun selectTab(listing: Listing, tab: ChannelUiTab) {
        selected = tab
        if (tab is ChannelUiTab.Content) ensureTabLoaded(listing, tab.tab)
    }

    private fun ensureTabLoaded(listing: Listing, tab: ChannelTab) {
        val loaded = if (tab in CONTAINER_TABS) containerTabs[tab]?.loaded == true else videoTabs[tab]?.loaded == true
        if (loaded) return
        loadTab(listing, tab, page = null)
    }

    fun retryTab(listing: Listing, tab: ChannelTab) = loadTab(listing, tab, page = null)

    fun loadMoreTab(listing: Listing, tab: ChannelTab) {
        val loading = if (tab in CONTAINER_TABS) containerTabs[tab]?.loading else videoTabs[tab]?.loading
        if (loading == true) return
        val page = if (tab in CONTAINER_TABS) containerTabs[tab]?.nextPage else videoTabs[tab]?.nextPage
        page ?: return
        loadTab(listing, tab, page)
    }

    private fun loadTab(listing: Listing, tab: ChannelTab, page: String?) {
        val source = SourceRegistry.bySourceId(listing.sourceId) ?: return
        val key = "tab:${tab.name}"
        jobs[key]?.cancel()
        jobs[key] = viewModelScope.launch {
            if (tab in CONTAINER_TABS) loadContainerTab(source, listing.key, tab, page)
            else loadVideoTab(source, listing.key, tab, page)
        }
    }

    private suspend fun loadVideoTab(source: VideoSource, channelUrl: String, tab: ChannelTab, page: String?) {
        val before = videoTabs[tab] ?: VideoTabState()
        videoTabs[tab] = before.copy(loading = true, error = null)
        try {
            val result = source.channelTab(channelUrl, tab, page)
            val base = if (page == null) emptyList() else before.items
            val merged = dedupeAppend(base, result.items) { it.pageUrl }
            val fresh = merged.size - base.size
            videoTabs[tab] = VideoTabState(
                items = merged, nextPage = nextPageToken(result.nextPage, fresh), loading = false, loaded = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExtractionError) {
            availableTabs = availableTabsAfter(availableTabs, tab, e)
            if (isTabUnavailable(e)) {
                videoTabs.remove(tab)
                fallBackIfSelected(tab)
            } else {
                videoTabs[tab] = before.copy(
                    loading = false, error = e.userMessage(), blocked = e is ExtractionError.AccessChallenge,
                )
            }
        } catch (e: Exception) {
            videoTabs[tab] = before.copy(loading = false, error = "Something went wrong")
        }
    }

    private suspend fun loadContainerTab(source: VideoSource, channelUrl: String, tab: ChannelTab, page: String?) {
        val before = containerTabs[tab] ?: ContainerTabState()
        containerTabs[tab] = before.copy(loading = true, error = null)
        try {
            val result = source.channelContainers(channelUrl, tab, page)
            val base = if (page == null) emptyList() else before.items
            val merged = dedupeAppend(base, result.items) { it.key }
            val fresh = merged.size - base.size
            containerTabs[tab] = ContainerTabState(
                items = merged, nextPage = nextPageToken(result.nextPage, fresh), loading = false, loaded = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ExtractionError) {
            availableTabs = availableTabsAfter(availableTabs, tab, e)
            if (isTabUnavailable(e)) {
                containerTabs.remove(tab)
                fallBackIfSelected(tab)
            } else {
                containerTabs[tab] = before.copy(
                    loading = false, error = e.userMessage(), blocked = e is ExtractionError.AccessChallenge,
                )
            }
        } catch (e: Exception) {
            containerTabs[tab] = before.copy(loading = false, error = "Something went wrong")
        }
    }

    /** The tab just hidden out from under the user -- land on the first tab still standing. */
    private fun fallBackIfSelected(droppedTab: ChannelTab) {
        val cur = selected
        if (cur is ChannelUiTab.Content && cur.tab == droppedTab) {
            selected = availableTabs.firstOrNull()?.let { ChannelUiTab.Content(it) } ?: ChannelUiTab.Search
        }
    }

    fun runChannelSearch(listing: Listing, query: String) {
        searchQuery = query
        jobs["search"]?.cancel()
        if (!shouldSearchChannel(query)) {
            searchState = VideoTabState()
            return
        }
        val source = SourceRegistry.bySourceId(listing.sourceId) ?: return
        searchState = VideoTabState(loading = true)
        jobs["search"] = viewModelScope.launch {
            try {
                val result = source.searchChannel(listing.key, query)
                searchState = VideoTabState(
                    items = dedupeAppend(emptyList(), result.items) { it.pageUrl },
                    nextPage = result.nextPage, loaded = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExtractionError) {
                searchState = VideoTabState(error = e.userMessage(), blocked = e is ExtractionError.AccessChallenge, loaded = true)
            } catch (e: Exception) {
                searchState = VideoTabState(error = "Something went wrong", loaded = true)
            }
        }
    }

    fun loadMoreSearch(listing: Listing) {
        val before = searchState
        val page = before.nextPage ?: return
        if (before.loading) return
        val source = SourceRegistry.bySourceId(listing.sourceId) ?: return
        searchState = before.copy(loading = true)
        jobs["search"]?.cancel()
        jobs["search"] = viewModelScope.launch {
            try {
                val result = source.searchChannel(listing.key, searchQuery, page)
                val merged = dedupeAppend(before.items, result.items) { it.pageUrl }
                val fresh = merged.size - before.items.size
                searchState = VideoTabState(items = merged, nextPage = nextPageToken(result.nextPage, fresh), loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExtractionError) {
                searchState = before.copy(loading = false, error = e.userMessage())
            } catch (e: Exception) {
                searchState = before.copy(loading = false, error = "Something went wrong")
            }
        }
    }
}
