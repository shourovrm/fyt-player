package com.fyiplayer.app.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    pageUrl: String,
    onOpenDetail: (VideoRef) -> Unit,
    onOpenListing: (Listing) -> Unit,
    onBack: () -> Unit,
    playerSurface: @Composable (ref: VideoRef) -> Unit = {},
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
    var detail by remember(pageUrl) { mutableStateOf(VideoDetail(ref)) }
    var descriptionExpanded by remember(pageUrl) { mutableStateOf(false) }
    var actionSheetRef by remember(pageUrl) { mutableStateOf<VideoRef?>(null) }
    var historyRecorded by remember(pageUrl) { mutableStateOf(false) }

    LaunchedEffect(pageUrl) {
        val source = SourceRegistry.bySourceId(ref.sourceId)
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
                playerSurface(shownRef)
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
                // This platform publishes no related videos (YoutubeSource.detail's own doc) --
                // an honest empty gap renders nothing, never an empty "Related videos" header.
                if (detail.related.isNotEmpty()) {
                    item {
                        Text(
                            "Related videos",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                        )
                    }
                    items(detail.related, key = { it.pageUrl }) { rel ->
                        ResultRow(
                            rel,
                            onClick = {
                                val index = detail.related.indexOfFirst { it.pageUrl == rel.pageUrl }.coerceAtLeast(0)
                                PlaybackSession.play(detail.related, index)
                                onOpenDetail(rel)
                            },
                            onLongPress = { actionSheetRef = rel },
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
