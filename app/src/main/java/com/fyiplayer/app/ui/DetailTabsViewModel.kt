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

    fun ensureSimilarLoaded(source: VideoSource?, ref: VideoRef) {
        if (similarLoading || similarLoadedFor == ref.pageUrl) return
        loadSimilar(source, ref)
    }

    fun retrySimilar(source: VideoSource?, ref: VideoRef) = loadSimilar(source, ref)

    private fun loadSimilar(source: VideoSource?, ref: VideoRef) {
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
        similarJob = viewModelScope.launch {
            try {
                // The engine exposes no related/recommended list (DECISIONS.md); this is a search
                // on the video's own topic, honestly labelled as such in the UI.
                val page = source.search(buildSimilarQuery(ref.title))
                similarItems = excludeCurrent(page.items, ref)
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
