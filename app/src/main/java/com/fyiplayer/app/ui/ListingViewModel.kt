package com.fyiplayer.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.VideoRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One channel/playlist listing, paged the same way as Home's single-source tab. Kept as its own
 *  small [AndroidViewModel] (not folded into [HomeViewModel]) so switching listings never touches
 *  Home's own paging state, and so it survives nav-back/config-change the same way Home does. */
class ListingViewModel(application: Application) : AndroidViewModel(application) {
    var items: List<VideoRef> by mutableStateOf(emptyList())
        private set
    var nextPage: String? by mutableStateOf(null)
        private set
    var loading: Boolean by mutableStateOf(true)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var blocked: Boolean by mutableStateOf(false)
        private set

    private var loadJob: Job? = null
    private var loadedFor: Listing? = null

    fun ensureLoaded(listing: Listing) {
        if (loadedFor == listing) return
        loadedFor = listing
        items = emptyList()
        nextPage = null
        load(listing, null)
    }

    fun retry(listing: Listing) = load(listing, nextPage)

    fun loadMore(listing: Listing) {
        val page = nextPage ?: return
        if (loading) return
        load(listing, page)
    }

    private fun load(listing: Listing, page: String?) {
        val source = SourceRegistry.bySourceId(listing.sourceId)
        if (source == null) {
            loading = false
            error = "This source isn't available"
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = true
            error = null
            blocked = false
            try {
                val result = source.listing(listing, page)
                val seen: HashSet<String> = if (page == null) HashSet() else items.mapTo(HashSet()) { it.pageUrl }
                val fresh = result.items.filter { seen.add(it.pageUrl) }
                items = if (page == null) fresh else items + fresh
                nextPage = result.nextPage
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExtractionError.AccessChallenge) {
                blocked = true
                error = e.userMessage()
                nextPage = null
            } catch (e: ExtractionError) {
                error = e.userMessage()
            } catch (e: Exception) {
                error = "Something went wrong"
            }
            loading = false
        }
    }
}
