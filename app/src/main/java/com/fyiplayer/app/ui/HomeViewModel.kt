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
import com.fyiplayer.app.core.VideoSource
import com.fyiplayer.app.data.repo.SearchHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
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

    var query: String by mutableStateOf("")
    var selectedTab: String by mutableStateOf(ALL_TAB_ID)

    /** Per-source homepage feed (blank query) and per-source active-search feed -- independent, so
     *  clearing the query back to blank never re-fetches the home feed. */
    internal val homeResults = mutableStateMapOf<String, TabResult>()
    internal val searchResults = mutableStateMapOf<String, TabResult>()

    // one in-flight job per "home:<id>" / "search:<id>" key -- refuses a duplicate load and lets a
    // superseded query's jobs be cancelled by prefix.
    private val activeJobs = mutableMapOf<String, Job>()

    fun searchHistory() = searchHistoryRepo.observe()

    private fun cancelJobsWithPrefix(prefix: String) {
        val keys = activeJobs.keys.filter { it.startsWith(prefix) }
        keys.forEach { activeJobs[it]?.cancel() }
        keys.forEach { activeJobs.remove(it) }
    }

    fun loadHome(source: VideoSource, page: String?) {
        val key = "home:${source.id}"
        if (activeJobs[key]?.isActive == true) return
        val before = homeResults[source.id] ?: TabResult(source.displayName)
        homeResults[source.id] = before.copy(loading = true, error = null)
        activeJobs[key] = viewModelScope.launch {
            val cur = homeResults[source.id] ?: before
            homeResults[source.id] = runCatching { source.homepage(page) }
                .fold(
                    onSuccess = { applySuccess(cur, page, it) },
                    onFailure = { e -> if (e is CancellationException) throw e else outcomeFor(cur, page, e) },
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

    fun retryTab(source: VideoSource, isSearching: Boolean) {
        val map = if (isSearching) searchResults else homeResults
        val page = map[source.id]?.retryPage
        if (isSearching) loadSearch(source, query, page) else loadHome(source, page)
    }

    fun continueTab(sources: List<VideoSource>, isSearching: Boolean) {
        sources.forEach { src ->
            val map = if (isSearching) searchResults else homeResults
            val cur = map[src.id] ?: return@forEach
            if (cur.loading) return@forEach
            val page = cur.nextPage ?: return@forEach
            if (isSearching) loadSearch(src, query, page) else loadHome(src, page)
        }
    }

    fun deleteSearchHistoryEntry(q: String) {
        viewModelScope.launch { searchHistoryRepo.remove(q) }
    }

    private fun outcomeFor(cur: TabResult, page: String?, error: Throwable): TabResult = when (error) {
        is ExtractionError.Unsupported -> applyUnsupported(cur)
        is ExtractionError -> applyError(cur, page, error)
        else -> cur.copy(loading = false, error = "Something went wrong", retryPage = page, nextPage = null, loaded = true)
    }
}
