package com.fyiplayer.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.core.VideoSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shorts feed paging state, hoisted the same way [HomeViewModel] hoists Home's -- survives
 * navigating to Detail and back as long as the Shorts back-stack entry isn't popped (AppShell's
 * no-`popUpTo` rule), so returning to the tab doesn't restart the feed from empty.
 */
class ShortsViewModel(application: Application) : AndroidViewModel(application) {
    var items: List<VideoRef> by mutableStateOf(emptyList())
        private set
    internal var feedState: ShortsFeedState by mutableStateOf(ShortsFeedState())
        private set
    var loading: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set

    /** Which page the pager last settled on -- restored as the pager's `initialPage` so leaving
     *  and returning to the tab doesn't snap back to the first clip. */
    var pagerPage: Int by mutableStateOf(0)

    private var loadJob: Job? = null

    /** Drops everything and starts over -- called when the enabled-source set changes underneath
     *  an already-loaded feed. */
    fun reset() {
        loadJob?.cancel()
        items = emptyList()
        feedState = ShortsFeedState()
        loading = false
        error = null
        pagerPage = 0
    }

    fun loadMore(sources: List<VideoSource>) {
        if (loading) return
        if (sources.isEmpty()) {
            error = "No enabled source provides short-form clips."
            return
        }
        if (!feedState.hasMore(sources)) return
        loading = true
        error = null
        loadJob = viewModelScope.launch {
            val round = loadShortsRound(sources, feedState)
            items = appendDeduped(items, round.items)
            feedState = round.state
            loading = false
            if (items.isEmpty() && !feedState.hasMore(sources)) {
                error = "No short-form clips available right now."
            }
        }
    }
}
