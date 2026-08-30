package com.fyiplayer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.VideoDetail
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.HistoryRepository
import com.fyiplayer.app.download.DownloadOption
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
    onOpenShorts: (List<VideoRef>, Int) -> Unit,
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
    // Gates the SIMILAR fetch below: Similar reads detail.related, which is only meaningful once
    // this resolves (success or fallback placeholder) -- firing earlier would search on an empty
    // related list and latch (similarLoadedFor), so the real related list would never load.
    var detailLoaded by remember(pageUrl) { mutableStateOf(false) }
    var actionSheetRef by remember(pageUrl) { mutableStateOf<VideoRef?>(null) }
    var historyRecorded by remember(pageUrl) { mutableStateOf(false) }
    // NOT keyed on pageUrl: this screen's own lifetime is the right scope for it, not a per-video
    // reset. Toggled by the player slot's own fullscreen button, or by the BackHandler below.
    var fullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = fullscreen) { fullscreen = false }
    // AppScaffold reads this to drop the nav bar and its inset padding for exactly as long as
    // fullscreen lasts — restored on every exit path, including this composable leaving
    // composition outright (e.g. navigating away without explicitly toggling back first).
    // Decide on the key, never re-read `fullscreen` inside onDispose: by the time the old effect
    // disposes the state already reads false, so a guard there skipped the release and the claim
    // leaked (nav bar gone + page under the status bar on every route after a fullscreen exit).
    DisposableEffect(fullscreen) {
        if (!fullscreen) return@DisposableEffect onDispose {}
        FullscreenChrome.acquire()
        onDispose { FullscreenChrome.release() }
    }

    // Being on a watch page means "this page's video plays": on every entry -- fresh open OR
    // back-return from deeper in a Similar chain -- a session playing anything else switches
    // here. The old once-per-nav-entry latch left the deeper video running over this page's
    // description (user-reported mismatch). Same-video re-entry stays a no-op, so reopening the
    // mini player's detail never restarts it; error state replays (retry-on-reopen).
    LaunchedEffect(pageUrl) {
        val st = PlaybackSession.state.value
        if (st.current?.pageUrl != ref.pageUrl || st.error != null) {
            PlaybackSession.play(listOf(ref), 0)
        }
    }

    LaunchedEffect(pageUrl) {
        detail = try {
            source?.detail(ref) ?: VideoDetail(ref)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VideoDetail(ref)
        }
        detailLoaded = true
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
    // A bare-URL open (share) started playback with title="" -- hand the enriched ref to the
    // session so the mini player / notification stop showing an empty title.
    LaunchedEffect(shownRef) { if (shownRef.title.isNotBlank()) PlaybackSession.updateCurrentMeta(shownRef) }

    // Only the selected tab fetches, and only once per video -- ensureXLoaded is idempotent
    // (DetailTabsViewModel), so re-running this on every recomposition (e.g. re-entering from
    // fullscreen) never refetches. Similar waits on detailLoaded so it sees the real
    // detail.related instead of firing early on an empty list and latching onto a search fallback
    // for good; Comments doesn't need detail at all. Keeps loading even while fullscreen so
    // content is ready the moment the user exits it.
    LaunchedEffect(tabsVm.selectedTab, shownRef.pageUrl, shownRef.title, detailLoaded) {
        when (tabsVm.selectedTab) {
            DetailTab.SIMILAR -> if (detailLoaded && shownRef.title.isNotBlank()) {
                tabsVm.ensureSimilarLoaded(source, shownRef, detail.related)
            }
            DetailTab.DESCRIPTION -> {} // reads straight off `detail`, already fetched above
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

    // No top bar (neither YouTube nor PipePipe has one): the title lives once, under the video,
    // and close/minimise are the player's own top-left buttons. Scaffold still pads the status bar.
    Scaffold { padding ->
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
                // Before detail() lands (or when it fails), the ref itself often already knows the
                // channel URL (listings + playlist rows persist it) -- link from that instead of
                // degrading to plain text for the whole wait.
                val uploaderListing = detail.uploader ?: shownRef.uploaderUrl?.let {
                    Listing(sourceId = shownRef.sourceId, kind = Listing.Kind.CHANNEL, key = it, title = shownRef.uploader ?: "")
                }
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
                item { VideoActionRow(rememberVideoActions(shownRef)) }
                // Always at least SIMILAR + DESCRIPTION, so the tab row is never a bar with one
                // option (the old showCommentsTab-only gate no longer applies).
                item {
                    PrimaryTabRow(selectedTabIndex = tabsVm.selectedTab.ordinal, modifier = Modifier.padding(top = 16.dp)) {
                        Tab(
                            selected = tabsVm.selectedTab == DetailTab.SIMILAR,
                            onClick = { tabsVm.selectedTab = DetailTab.SIMILAR },
                            text = { Text("Similar") },
                        )
                        Tab(
                            selected = tabsVm.selectedTab == DetailTab.DESCRIPTION,
                            onClick = { tabsVm.selectedTab = DetailTab.DESCRIPTION },
                            text = { Text("Description") },
                        )
                        if (showCommentsTab) {
                            Tab(
                                selected = tabsVm.selectedTab == DetailTab.COMMENTS,
                                onClick = { tabsVm.selectedTab = DetailTab.COMMENTS },
                                text = { Text(tabsVm.commentsCount?.let { "Comments ($it)" } ?: "Comments") },
                            )
                        }
                    }
                }
                when (tabsVm.selectedTab) {
                    DetailTab.SIMILAR -> similarVideosSection(
                        results = tabsVm.similarItems,
                        loading = tabsVm.similarLoading,
                        error = tabsVm.similarError,
                        retryEnabled = !tabsVm.similarBlocked,
                        onRetry = { tabsVm.retrySimilar(source, shownRef, detail.related) },
                        // Just opens Detail -- Detail autoplays the single video (PipePipe queue
                        // model, CLAUDE.md), so a chain of Similar taps unwinds one video per back.
                        onClick = onOpenDetail,
                        onLongPress = { actionSheetRef = it },
                        onOpenShorts = onOpenShorts,
                    )
                    DetailTab.DESCRIPTION -> descriptionTabSection(detail, onOpenDetail, onOpenListing)
                    DetailTab.COMMENTS -> item {
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

/**
 * Like / Save / Download / Share, directly under the title (no Queue: the page's own video is already the queue) -- the same handlers
 * [VideoActionSheet]'s long-press sheet uses ([VideoActions], `ui/VideoActionSheet.kt`), just laid
 * out as a permanent row instead of a sheet. Like is the only button with an active look (accent +
 * filled icon); it's read from [VideoActions.likedFlow], not a one-shot check, so a toggle from
 * elsewhere (or from this row itself) is never stale.
 */
@Composable
private fun VideoActionRow(actions: VideoActions, modifier: Modifier = Modifier) {
    val liked by actions.likedFlow.collectAsState(initial = false)
    var showPlaylistPicker by remember(actions.ref.pageUrl) { mutableStateOf(false) }
    val context = LocalContext.current

    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        ActionButton(label = "Like", active = liked, onClick = { actions.toggleLike(liked) }) { color ->
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null, tint = color, modifier = Modifier.size(20.dp),
            )
        }
        ActionButton(label = "Save", onClick = { showPlaylistPicker = true }) { color -> SaveGlyph(tint = color) }
        ActionButton(label = "Download", onClick = { actions.startDownload() }) { color -> DownloadTrayGlyph(tint = color) }
        ActionButton(label = "Share", onClick = { actions.share() }) { color ->
            Icon(Icons.Filled.Share, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(ref = actions.ref, playlists = actions.playlists, onDismiss = { showPlaylistPicker = false })
    }
    actions.downloadPicker?.let { state ->
        DownloadQualityDialog(
            state = state,
            onSelect = { option: DownloadOption -> actions.confirmDownload(option) { message -> showToast(context, message) } },
            onDismiss = { actions.dismissDownloadPicker() },
        )
    }
}

/** One column of the action row: icon over a 10.5sp-class label, ink2 idle / accent when [active]. */
@Composable
private fun RowScope.ActionButton(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(color)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** `material-icons-core` has neither a bookmark nor a download glyph (CLAUDE.md's own gotcha);
 *  drawn by hand the same way the player's Pause/Skip/Fullscreen glyphs solve the same gap
 *  (`player/PlayerOverlays.kt`). */
@Composable
private fun SaveGlyph(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.06f)
            lineTo(w * 0.78f, h * 0.06f)
            lineTo(w * 0.78f, h * 0.95f)
            lineTo(w * 0.5f, h * 0.72f)
            lineTo(w * 0.22f, h * 0.95f)
            close()
        }
        drawPath(path, tint, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun DownloadTrayGlyph(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f
        drawLine(tint, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.6f), stroke, cap = StrokeCap.Round)
        val arrow = Path().apply {
            moveTo(w * 0.26f, h * 0.42f)
            lineTo(w * 0.5f, h * 0.68f)
            lineTo(w * 0.74f, h * 0.42f)
        }
        drawPath(arrow, tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tint, Offset(w * 0.14f, h * 0.88f), Offset(w * 0.86f, h * 0.88f), stroke, cap = StrokeCap.Round)
    }
}

private fun formatCount(n: Long): String = NumberFormat.getIntegerInstance().format(n)

/** yt-dlp's `upload_date` is `YYYYMMDD`; anything else (or a parse miss) is shown as-is rather
 *  than guessed at. */
private fun formatUploadDate(input: String): String {
    // Two shapes reach here: yt-dlp's "20260805" and NewPipe's ISO-8601
    // "2026-08-05T04:00:27-07:00". Reduce the second to the first, then one code path.
    val raw = if (input.length >= 10 && input[4] == '-' && input[7] == '-') {
        input.take(10).replace("-", "")
    } else {
        input
    }
    if (raw.length != 8 || raw.any { !it.isDigit() }) return input
    val year = raw.substring(0, 4)
    val month = raw.substring(4, 6).toIntOrNull() ?: return input
    val day = raw.substring(6, 8).toIntOrNull() ?: return input
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val name = months.getOrNull(month - 1) ?: return input
    return "$name $day, $year"
}
