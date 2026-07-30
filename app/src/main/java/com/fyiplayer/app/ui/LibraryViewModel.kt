package com.fyiplayer.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.fyiplayer.app.FyiApp
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.HistoryRepository
import com.fyiplayer.app.data.repo.LikesRepository
import com.fyiplayer.app.data.repo.PlaylistRepository
import com.fyiplayer.app.data.repo.PositionsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

enum class LibraryTab { LIKES, PLAYLISTS, HISTORY }

/** Playlist card shape for the Playlists tab: name + count + first item's thumbnail, already
 *  joined so the row draws from one map lookup instead of a per-row query (rule 6). */
data class PlaylistCard(val id: Long, val name: String, val itemCount: Int, val coverThumbnailUrl: String?)

/**
 * Hoisted so tab + selection survive tab switches, navigating to a playlist and back, and
 * rotation -- same shape as [HomeViewModel]'s query/selectedTab fields (ViewModel outlives
 * configuration change by construction, no Saver needed).
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FyiApp
    val prefs = app.prefs
    val likes = LikesRepository(app.database.likeDao())
    val playlists = PlaylistRepository(app.database.playlistDao(), app.database.playlistItemDao())
    val history = HistoryRepository(app.database.watchHistoryDao())
    val positions = PositionsRepository(app.database.playbackPositionDao())

    var tab: LibraryTab by mutableStateOf(LibraryTab.LIKES)
        private set

    // Empty set == not selecting. A selection only ever means something on the tab it was made on.
    var selection: Set<String> by mutableStateOf(emptySet())

    fun selectTab(next: LibraryTab) {
        tab = next
        selection = emptySet()
    }

    fun toggle(ref: VideoRef) {
        selection = selection.toggled(ref.pageUrl)
    }

    fun clearSelection() {
        selection = emptySet()
    }

    /**
     * One combined stream instead of a query per visible playlist row: [PlaylistRepository] has
     * no single joined query, so this composes its existing per-playlist [PlaylistRepository.observeItems]
     * once per playlist-list change -- bounded by playlist count, not by scroll position. A `val`,
     * not a function: building the flow does no work until collected, but a fresh Flow instance
     * per call would make `collectAsStateWithLifecycle` restart the whole pipeline on every
     * recomposition instead of once.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistCards: Flow<List<PlaylistCard>> =
        playlists.observePlaylists().flatMapLatest { metas ->
            if (metas.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(metas.map { p -> playlists.observeItems(p.id).map { p to it } }) { pairs ->
                    pairs.map { (p, items) -> PlaylistCard(p.id, p.name, items.size, items.firstOrNull()?.thumbnailUrl) }
                }
            }
        }
}
