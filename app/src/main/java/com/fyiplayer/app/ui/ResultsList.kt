package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.ui.theme.fyiExtras

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
            if (ref.thumbnailUrl != null) {
                AsyncImage(model = ref.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            if (ref.durationSeconds != null) {
                Text(
                    formatDuration(ref.durationSeconds),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    color = Color(0xFFF2F4F5),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(ref.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            // Channel · views · age, non-null parts only -- a listing that carried none of these
            // renders no meta line at all rather than a row of dangling separators.
            val meta = listOfNotNull(ref.uploader, ref.viewCountText, shortAge(ref.uploadedText)).joinToString(" · ")
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
