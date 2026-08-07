package com.fyiplayer.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.VideoDetail
import com.fyiplayer.app.core.VideoRef
import kotlinx.coroutines.CancellationException

/**
 * Details as a sheet over the still-mounted shorts pager, instead of the old full-page nav
 * (world-switch). Reuses exactly what [DetailScreen] uses for these two tabs -- same
 * [DetailTabsViewModel] (comments fetch-once gate), same [descriptionTabSection]/[CommentsSection]
 * -- so this file adds no new fetch/threading logic, only the sheet chrome and a local
 * [VideoDetail] fetch (the pager has no detail loaded anywhere; the watch page's own [detail]
 * lookup is copied verbatim from `DetailScreen.kt`'s `LaunchedEffect(pageUrl)`).
 *
 * No Similar tab here (out of scope for this sheet) -- [DetailTab.SIMILAR] is simply never
 * selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShortsDetailsSheet(ref: VideoRef, onDismiss: () -> Unit) {
    val source = remember(ref.sourceId) { SourceRegistry.bySourceId(ref.sourceId) }
    val showCommentsTab = source?.providesComments == true
    var detail by remember(ref.pageUrl) { mutableStateOf(VideoDetail(ref)) }
    var selectedTab by remember(ref.pageUrl) { mutableStateOf(DetailTab.DESCRIPTION) }
    val tabsVm: DetailTabsViewModel = viewModel()

    LaunchedEffect(ref.pageUrl) {
        detail = try {
            source?.detail(ref) ?: VideoDetail(ref)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VideoDetail(ref)
        }
    }
    LaunchedEffect(selectedTab, ref.pageUrl) {
        if (selectedTab == DetailTab.COMMENTS && showCommentsTab) tabsVm.ensureCommentsLoaded(source, ref)
    }

    // ponytail: description links to ANOTHER video/channel/playlist no-op here. ShortsPage's own
    // onOpenDetail is `() -> Unit`, already curried to THIS short by its caller (ShortsPager) --
    // there is no callback here that can navigate to an arbitrary linked target, and adding one
    // means threading a nav callback through files this task doesn't own. Same-video timestamp
    // links still work (handleDescriptionLink seeks directly, no callback needed). Add real
    // routing only if cross-video description links turn out to matter from inside this sheet.
    val openDetail: (VideoRef) -> Unit = {}
    val openListing: (Listing) -> Unit = {}

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // ~50% screen height (mockup's 45-55%): the fraction is of the sheet's own max height,
        // which the host already caps at the screen, so the short stays visible above it.
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.5f)) {
            // Index by hand, not DetailTab.ordinal: this sheet skips SIMILAR (ordinal 0), so
            // DESCRIPTION/COMMENTS would otherwise land on tab row positions 1/2 instead of 0/1.
            PrimaryTabRow(selectedTabIndex = if (selectedTab == DetailTab.COMMENTS) 1 else 0) {
                Tab(
                    selected = selectedTab == DetailTab.DESCRIPTION,
                    onClick = { selectedTab = DetailTab.DESCRIPTION },
                    text = { Text("Description") },
                )
                if (showCommentsTab) {
                    Tab(
                        selected = selectedTab == DetailTab.COMMENTS,
                        onClick = { selectedTab = DetailTab.COMMENTS },
                        text = { Text(tabsVm.commentsCount?.let { "Comments ($it)" } ?: "Comments") },
                    )
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                when (selectedTab) {
                    DetailTab.DESCRIPTION -> descriptionTabSection(detail, openDetail, openListing)
                    DetailTab.COMMENTS -> item {
                        CommentsSection(
                            comments = tabsVm.comments,
                            loading = tabsVm.commentsLoading,
                            error = tabsVm.commentsError,
                            retryEnabled = !tabsVm.commentsBlocked,
                            onRetry = { tabsVm.retryComments(source, ref) },
                        )
                    }
                    DetailTab.SIMILAR -> {} // never selected in this sheet
                }
            }
        }
    }
}
