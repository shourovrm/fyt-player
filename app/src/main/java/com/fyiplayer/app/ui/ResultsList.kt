package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fyiplayer.app.core.ResultKind
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.PlaybackPosition
import com.fyiplayer.app.data.repo.youtubeThumbnailFor
import com.fyiplayer.app.ui.theme.fyiExtras
import java.util.Locale
import kotlin.math.roundToInt

/** App-wide resume positions, keyed by page URL -- provided once at [AppShell]'s root from one
 *  [com.fyiplayer.app.data.repo.PositionsRepository.observeAll] flow (rule 6: never a per-row
 *  query). Default empty so any screen that forgets to provide it just shows no bars, not a crash. */
val LocalPlaybackPositions = compositionLocalOf<Map<String, PlaybackPosition>> { emptyMap() }

/**
 * Shared results-list rendering: Home and Listing both funnel into this instead of duplicating
 * the LazyColumn shape. Owns no state itself -- paging and scroll position ([listState]) live
 * with the caller.
 */
@Composable
internal fun ResultsListColumn(
    items: List<VideoRef>,
    errors: List<ErrorRow> = emptyList(),
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onClick: (VideoRef) -> Unit,
    onLongPress: (VideoRef) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    isLoadingMore: Boolean = false,
    /** Placeholder rows shown instead of the list while the very first page is in flight. */
    skeletonRows: Int = 0,
    /** Every feeding source is exhausted -- a real end, not a stall. */
    endOfResults: Boolean = false,
    /** Rendered as the first list item so it scrolls away with the results (search's shorts shelf). */
    topContent: (@Composable () -> Unit)? = null,
) {
    // Endless scroll: fire onLoadMore a few rows before the true bottom so scrolling never stalls.
    val shouldLoadMore by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layout.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, isLoadingMore) {
        if (shouldLoadMore && hasMore && !isLoadingMore) onLoadMore()
    }

    LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        topContent?.let { item(key = "topContent") { it() } }
        errors.forEach { row -> item { ErrorRowView(row) } }
        if (items.isEmpty() && skeletonRows > 0) {
            items(skeletonRows) { SkeletonRow() }
        }
        // Deduped by page URL: the same video can legitimately turn up twice (a listing shifting
        // between page loads, or a video appearing in both a search and its own related list), and
        // LazyColumn's `key` throws on a duplicate.
        val deduped = items.distinctBy { it.pageUrl }
        items(deduped, key = { it.pageUrl }) { ref ->
            ResultRow(ref, onClick = { onClick(ref) }, onLongPress = { onLongPress(ref) })
        }
        if (isLoadingMore) {
            item { LoadingTailRow() }
        } else if (endOfResults && items.isNotEmpty()) {
            item {
                Text(
                    "No more results",
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (hasMore) {
            item { TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("Load more") } }
        }
    }
}

/** One error banner: which source, what happened, and how to retry -- [onRetry] null means an
 *  [com.fyiplayer.app.core.ExtractionError.AccessChallenge] stopped it, an honest wall with no
 *  retry affordance, never a retry prompt. */
internal data class ErrorRow(val sourceName: String, val message: String, val onRetry: (() -> Unit)?)

internal fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

/** Channel rows show subscriber count instead of the video-only uploader/views/age line. */
private fun resultMetaText(ref: VideoRef): String {
    if (ref.kind == ResultKind.CHANNEL) {
        val subs = ref.subscriberCount
        if (subs != null && subs >= 0) return "${compactCount(subs)} subscribers"
    }
    return listOfNotNull(ref.uploader, ref.viewCountText, shortAge(ref.uploadedText)).joinToString(" · ")
}

// Duplicated from NewPipeYoutubeSource.compactCount -- UI can't import from source/, extraction
// stays behind the VideoSource seam.
private fun compactCount(count: Long): String {
    if (count < 1000) return count.toString()
    val (divisor, suffix) = when {
        count < 1_000_000 -> 1_000.0 to "K"
        count < 1_000_000_000 -> 1_000_000.0 to "M"
        else -> 1_000_000_000.0 to "B"
    }
    val tenths = (count / divisor * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}$suffix" else "${"%.1f".format(Locale.US, tenths / 10.0)}$suffix"
}

private val ROW_THUMB = DpSize(120.dp, 67.5.dp)

/** The one result row the whole app renders (Home, Listing, Detail's related). TAP opens the
 *  video, LONG-PRESS opens the shared action sheet. */
@Composable
internal fun ResultRow(ref: VideoRef, onClick: () -> Unit, onLongPress: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ROW_THUMB).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            // Library rows (likes/playlist/history) can carry only a bare pageUrl when the add
            // happened before enrichment landed -- fall back to the un-expiring hqdefault so the
            // row isn't blank instead of leaving it that way forever.
            val thumb = ref.thumbnailUrl ?: youtubeThumbnailFor(ref.pageUrl)
            if (thumb != null) {
                AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            // isLive/isUpcoming take the corner over a duration -- neither is a fixed-length clip
            // yet, so a "12:34" badge would be a lie. Playlist rows carry no durationSeconds at
            // all (no VideoRef shape fits one), so they fall through with no badge, correctly.
            when {
                ref.isLive -> CornerBadge("LIVE", background = MaterialTheme.colorScheme.error, color = MaterialTheme.colorScheme.onError)
                ref.isUpcoming -> CornerBadge("UPCOMING", background = Color.Black.copy(alpha = 0.72f), color = Color(0xFFF2F4F5))
                ref.durationSeconds != null -> CornerBadge(formatDuration(ref.durationSeconds), background = Color.Black.copy(alpha = 0.72f), color = Color(0xFFF2F4F5))
            }
            val position = LocalPlaybackPositions.current[ref.pageUrl]
            if (position != null && position.positionMs > 0 && position.durationMs > 0) {
                // YouTube's own idiom (red on a dark track), not the theme accent: the accent is
                // blue and 2dp of it was invisible on a light thumbnail edge.
                LinearProgressIndicator(
                    progress = { (position.positionMs.toFloat() / position.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp),
                    color = Color(0xFFFF0033),
                    trackColor = Color.Black.copy(alpha = 0.45f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            // A Library row can still be mid-enrichment (add landed before detail() resolved, or
            // detail() failed) -- an empty line reads as broken, "Untitled" reads as loading/unknown.
            Text(ref.title.ifBlank { "Untitled" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            // Channel · views · age, non-null parts only -- a listing that carried none of these
            // renders no meta line at all rather than a row of dangling separators.
            val meta = resultMetaText(ref)
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.fyiExtras.ink3,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Bottom-right thumbnail overlay -- one shape shared by the duration/LIVE/UPCOMING badges so they
 *  read as the same UI element, not three different overlays. */
@Composable
private fun BoxScope.CornerBadge(text: String, background: Color, color: Color) {
    Text(
        text,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
    )
}

@Composable
private fun LoadingTailRow() {
    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

/** Same row shape as [ResultRow], flat placeholder colour instead of content -- an honest "this is
 *  loading" shape, no shimmer. */
@Composable
private fun SkeletonRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(
            Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.padding(start = 12.dp).fillMaxWidth()) {
            Box(Modifier.fillMaxWidth(0.8f).size(14.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            Box(
                Modifier.padding(top = 8.dp).fillMaxWidth(0.4f).size(10.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun ErrorRowView(row: ErrorRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(row.sourceName) }
                append(" · ${row.message}")
            },
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (row.onRetry != null) {
            TextButton(onClick = row.onRetry) { Text("Retry") }
        }
    }
}
