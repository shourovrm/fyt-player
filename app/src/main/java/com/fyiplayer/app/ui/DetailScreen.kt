package com.fyiplayer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.VideoDetail
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.HistoryRepository
import com.fyiplayer.app.player.PlaybackSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.text.NumberFormat

/**
 * Pinned player header + metadata + related (DESIGN.md §5). [pageUrl] is the decoded canonical
 * page URL from the `detail/{pageUrl}` route; the full [VideoRef] a list row already had comes
 * from [RefCache] when available, so the header isn't blank while the network resolve is in
 * flight. [playerSurface] is a slot: the live playback surface is owned by a parallel unit of
 * work and is never imported here.
 *
 * Fullscreen is owned HERE, not by the player slot: this screen is the one that knows the LAYOUT
 * fullscreen implies (skip the whole page, render only the player at full size), and a player
 * composable one layer down can't make that call for its own parent. [FullscreenChrome] (same
 * package, `ui/AppScaffold.kt`) is told about it explicitly, and back exits fullscreen before it
 * ever reaches the nav back stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    pageUrl: String,
    onOpenDetail: (VideoRef) -> Unit,
    onOpenListing: (Listing) -> Unit,
    onBack: () -> Unit,
    playerSurface: @Composable (ref: VideoRef, fullscreen: Boolean, onToggleFullscreen: () -> Unit) -> Unit = { _, _, _ -> },
) {
    val app = rememberFyiApp()
    val ref = remember(pageUrl) {
        RefCache.get(pageUrl) ?: VideoRef(
            sourceId = SourceRegistry.forUrl(pageUrl)?.id ?: "",
            pageUrl = pageUrl,
            remoteId = pageUrl,
            title = "",
        )
    }
    val source = remember(pageUrl) { SourceRegistry.bySourceId(ref.sourceId) }
    val tabsVm: DetailTabsViewModel = viewModel()
    val showCommentsTab = source?.providesComments == true
    var detail by remember(pageUrl) { mutableStateOf(VideoDetail(ref)) }
    var descriptionExpanded by remember(pageUrl) { mutableStateOf(false) }
    var actionSheetRef by remember(pageUrl) { mutableStateOf<VideoRef?>(null) }
    var historyRecorded by remember(pageUrl) { mutableStateOf(false) }
    // NOT keyed on pageUrl: this screen's own lifetime is the right scope for it, not a per-video
    // reset. Toggled by the player slot's own fullscreen button, or by the BackHandler below.
    var fullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = fullscreen) { fullscreen = false }
    // AppScaffold reads this to drop the nav bar and its inset padding for exactly as long as
    // fullscreen lasts — restored on every exit path, including this composable leaving
    // composition outright (e.g. navigating away without explicitly toggling back first).
    DisposableEffect(fullscreen) {
        FullscreenChrome.active = fullscreen
        onDispose { FullscreenChrome.active = false }
    }

    LaunchedEffect(pageUrl) {
        detail = try {
            source?.detail(ref) ?: VideoDetail(ref)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VideoDetail(ref)
        }
    }

    // Recorded once the enriched ref has a real title, and only when the setting is on -- the
    // setting must actually do something, or it's a lying row in Settings.
    LaunchedEffect(detail) {
        if (!historyRecorded && detail.ref.title.isNotBlank() && app.prefs.recordWatchHistory.first()) {
            historyRecorded = true
            HistoryRepository(app.database.watchHistoryDao()).record(detail.ref)
        }
    }

    val shownRef = detail.ref.takeIf { it.title.isNotBlank() } ?: ref

    // Only the selected tab fetches, and only once per video -- ensureXLoaded is idempotent
    // (DetailTabsViewModel), so re-running this on every recomposition (e.g. re-entering from
    // fullscreen) never refetches. Similar needs a real title to build a query from, so it waits;
    // Comments doesn't. Keeps loading even while fullscreen so content is ready the moment the
    // user exits it.
    LaunchedEffect(tabsVm.selectedTab, shownRef.pageUrl, shownRef.title) {
        when (tabsVm.selectedTab) {
            DetailTab.SIMILAR -> if (shownRef.title.isNotBlank()) tabsVm.ensureSimilarLoaded(source, shownRef)
            DetailTab.COMMENTS -> if (showCommentsTab) tabsVm.ensureCommentsLoaded(source, shownRef)
        }
    }

    // Fullscreen: render ONLY the player, full size -- the top bar, metadata and related list are
    // not composed at all, so there is nothing left for AppScaffold's own chrome to compete with
    // for screen edges.
    if (fullscreen) {
        playerSurface(shownRef, true) { fullscreen = false }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(shownRef.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { actionSheetRef = shownRef }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // A fixed-height pinned header (not LazyColumn item 0): it never scrolls away and is
            // never recycled by scroll, so the player surface underneath keeps its identity.
            val headerHeight = LocalConfiguration.current.screenWidthDp.dp * 9 / 16
            Box(Modifier.fillMaxWidth().height(headerHeight).background(MaterialTheme.colorScheme.surfaceVariant)) {
                playerSurface(shownRef, false) { fullscreen = true }
            }

            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                item {
                    Text(
                        shownRef.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                }
                val uploaderListing = detail.uploader
                item {
                    when {
                        uploaderListing != null -> Text(
                            "${uploaderListing.title}  ›",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable { onOpenListing(uploaderListing) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        shownRef.uploader != null -> Text(
                            shownRef.uploader,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                item {
                    val meta = listOfNotNull(
                        detail.viewCount?.let { "${formatCount(it)} views" } ?: shownRef.viewCountText,
                        detail.likeCount?.let { "${formatCount(it)} likes" },
                        detail.uploadDate?.let(::formatUploadDate),
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                val description = detail.description
                if (!description.isNullOrBlank()) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { descriptionExpanded = !descriptionExpanded }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                description,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    if (descriptionExpanded) "Show less" else "Show more",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    if (descriptionExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                // Only one tab worth showing when this source has no comments (providesComments):
                // an honest gap, not a tab bar with a single option. Selection stays SIMILAR then.
                if (showCommentsTab) {
                    item {
                        PrimaryTabRow(selectedTabIndex = tabsVm.selectedTab.ordinal, modifier = Modifier.padding(top = 16.dp)) {
                            Tab(
                                selected = tabsVm.selectedTab == DetailTab.SIMILAR,
                                onClick = { tabsVm.selectedTab = DetailTab.SIMILAR },
                                text = { Text("Similar") },
                            )
                            Tab(
                                selected = tabsVm.selectedTab == DetailTab.COMMENTS,
                                onClick = { tabsVm.selectedTab = DetailTab.COMMENTS },
                                text = { Text(tabsVm.commentsCount?.let { "Comments ($it)" } ?: "Comments") },
                            )
                        }
                    }
                }
                if (!showCommentsTab || tabsVm.selectedTab == DetailTab.SIMILAR) {
                    similarVideosSection(
                        results = tabsVm.similarItems,
                        loading = tabsVm.similarLoading,
                        error = tabsVm.similarError,
                        retryEnabled = !tabsVm.similarBlocked,
                        onRetry = { tabsVm.retrySimilar(source, shownRef) },
                        onClick = { rel ->
                            val index = tabsVm.similarItems.indexOfFirst { it.pageUrl == rel.pageUrl }.coerceAtLeast(0)
                            PlaybackSession.play(tabsVm.similarItems, index)
                            onOpenDetail(rel)
                        },
                        onLongPress = { actionSheetRef = it },
                    )
                } else {
                    item {
                        CommentsSection(
                            comments = tabsVm.comments,
                            loading = tabsVm.commentsLoading,
                            error = tabsVm.commentsError,
                            retryEnabled = !tabsVm.commentsBlocked,
                            onRetry = { tabsVm.retryComments(source, shownRef) },
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }

    actionSheetRef?.let { target -> VideoActionSheet(target, onDismiss = { actionSheetRef = null }) }
}

private fun formatCount(n: Long): String = NumberFormat.getIntegerInstance().format(n)

/** yt-dlp's `upload_date` is `YYYYMMDD`; anything else (or a parse miss) is shown as-is rather
 *  than guessed at. */
private fun formatUploadDate(raw: String): String {
    if (raw.length != 8 || raw.any { !it.isDigit() }) return raw
    val year = raw.substring(0, 4)
    val month = raw.substring(4, 6).toIntOrNull() ?: return raw
    val day = raw.substring(6, 8).toIntOrNull() ?: return raw
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val name = months.getOrNull(month - 1) ?: return raw
    return "$name $day, $year"
}
