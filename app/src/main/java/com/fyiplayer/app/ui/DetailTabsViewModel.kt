package com.fyiplayer.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fyiplayer.app.core.Comment
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.core.VideoSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Which section is showing below the video header. DESCRIPTION needs no loaded/error state of
 *  its own -- it reads straight off the already-fetched [com.fyiplayer.app.core.VideoDetail]. */
enum class DetailTab { SIMILAR, DESCRIPTION, COMMENTS }

/**
 * Similar-videos and comments state for one video's detail screen. A plain [AndroidViewModel],
 * same shape as [ListingViewModel]: `detail/{pageUrl}` gives every video its own nav back-stack
 * entry, so a bare `viewModel()` call in [DetailScreen] already scopes one instance per video
 * with no manual key -- and it survives both the fullscreen toggle (a bool flip inside the same
 * composable, DetailScreen never leaves composition) and a configuration change.
 *
 * Each tab fetches at most once per video: [similarLoadedFor]/[commentsLoadedFor] gate a refetch
 * the same idempotent way [ListingViewModel.ensureLoaded] does, and are cleared on error so a
 * user-initiated retry actually retries instead of silently no-op'ing.
 */
class DetailTabsViewModel(application: Application) : AndroidViewModel(application) {
    var selectedTab: DetailTab by mutableStateOf(DetailTab.SIMILAR)

    var similarItems: List<VideoRef> by mutableStateOf(emptyList())
        private set
    var similarLoading: Boolean by mutableStateOf(false)
        private set
    var similarError: ExtractionError? by mutableStateOf(null)
        private set

    /** True only for [ExtractionError.AccessChallenge] -- an honest wall, never a retry prompt. */
    var similarBlocked: Boolean by mutableStateOf(false)
        private set
    private var similarLoadedFor: String? = null
    private var similarJob: Job? = null

    var comments: List<Comment> by mutableStateOf(emptyList())
        private set

    /** Top-level comment count once known, for the tab label. Null until a load succeeds. */
    var commentsCount: Int? by mutableStateOf(null)
        private set
    var commentsLoading: Boolean by mutableStateOf(false)
        private set
    var commentsError: ExtractionError? by mutableStateOf(null)
        private set
    var commentsBlocked: Boolean by mutableStateOf(false)
        private set
    private var commentsLoadedFor: String? = null
    private var commentsJob: Job? = null

    /** [related] is [com.fyiplayer.app.core.VideoDetail.related] -- already fetched alongside the
     *  video's own detail, so a non-empty list here needs no network call of its own. */
    fun ensureSimilarLoaded(source: VideoSource?, ref: VideoRef, related: List<VideoRef>) {
        if (similarLoading || similarLoadedFor == ref.pageUrl) return
        loadSimilar(source, ref, related)
    }

    fun retrySimilar(source: VideoSource?, ref: VideoRef, related: List<VideoRef>) = loadSimilar(source, ref, related)

    private fun loadSimilar(source: VideoSource?, ref: VideoRef, related: List<VideoRef>) {
        similarJob?.cancel()
        if (source == null) {
            similarError = ExtractionError.Unsupported("No source for this video")
            similarLoadedFor = null
            return
        }
        similarLoadedFor = ref.pageUrl
        similarLoading = true
        similarError = null
        similarBlocked = false

        // Real recommendations come free with the detail fetch (VideoDetail.related): the NewPipe
        // YouTube extractor maps info.relatedItems into it (NewPipeYoutubeSource.kt). Title search
        // is the fallback only, for sources whose detail() carries no related list (or a rare empty
        // one) -- no longer the primary path now that the engine does expose recommendations.
        val fromDetail = excludeCurrent(related.filter(::isVideoLike), ref)
        if (fromDetail.isNotEmpty()) {
            similarItems = fromDetail
            similarLoading = false
            return
        }

        similarJob = viewModelScope.launch {
            try {
                val query = buildSimilarQuery(ref.title)
                val page = source.search(query)
                val collected = excludeCurrent(page.items.filter(::isVideoLike), ref).toMutableList()
                // Niche queries return mostly channels; a search page filtered down to 2-3 videos
                // reads as broken. Top up from continuations, capped so a sparse query can't spin.
                var nextPage = page.nextPage
                var extraPages = 0
                while (collected.size < 8 && nextPage != null && extraPages < 2) {
                    extraPages++
                    try {
                        val more = source.search(query, nextPage)
                        val seen = collected.mapTo(HashSet()) { it.pageUrl }
                        excludeCurrent(more.items.filter(::isVideoLike), ref).forEach {
                            if (seen.add(it.pageUrl)) collected += it
                        }
                        nextPage = more.nextPage
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        break // partial Similar list beats an error banner over a working one
                    }
                }
                similarItems = collected
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExtractionError) {
                similarError = e
                similarBlocked = e is ExtractionError.AccessChallenge
                similarLoadedFor = null // retry must actually refetch
            } catch (e: Exception) {
                similarError = ExtractionError.Unsupported("Search failed")
                similarLoadedFor = null
            }
            similarLoading = false
        }
    }

    fun ensureCommentsLoaded(source: VideoSource?, ref: VideoRef) {
        if (commentsLoading || commentsLoadedFor == ref.pageUrl) return
        loadComments(source, ref)
    }

    fun retryComments(source: VideoSource?, ref: VideoRef) = loadComments(source, ref)

    private fun loadComments(source: VideoSource?, ref: VideoRef) {
        commentsJob?.cancel()
        if (source == null || !source.providesComments) {
            commentsError = ExtractionError.Unsupported("No comments for this source")
            commentsLoadedFor = null
            return
        }
        commentsLoadedFor = ref.pageUrl
        commentsLoading = true
        commentsError = null
        commentsBlocked = false
        commentsJob = viewModelScope.launch {
            try {
                val result = source.comments(ref)
                comments = result
                commentsCount = result.count { it.parentId == null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExtractionError) {
                commentsError = e
                commentsBlocked = e is ExtractionError.AccessChallenge
                commentsLoadedFor = null
            } catch (e: Exception) {
                commentsError = ExtractionError.Unsupported("comment loading failed")
                commentsLoadedFor = null
            }
            commentsLoading = false
        }
    }
}
