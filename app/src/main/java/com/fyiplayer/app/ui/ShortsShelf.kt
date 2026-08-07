package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.ui.theme.fyiExtras

/**
 * Splits a listing into (shorts, longform), each preserving its own relative order. Feeds the
 * search-mode shelf and keeps shorts out of the regular rows and the row-tap playback queue.
 */
internal fun partitionShorts(items: List<VideoRef>): Pair<List<VideoRef>, List<VideoRef>> =
    items.partition { it.isShort }

private val SHELF_CARD_WIDTH = 108.dp

/** Target shelf size: page 1 rarely has more than a handful of shorts, so search auto-pages
 *  (bounded by [MAX_SHELF_AUTO_FETCHES]) until the shelf is worth swiping through. */
internal const val MIN_SHELF_SHORTS = 20
internal const val MAX_SHELF_AUTO_FETCHES = 3

/**
 * Horizontal shelf of short-form cards above the regular search rows -- same tap-to-pager idiom
 * as [ShortsGrid]'s tile, just a row instead of a grid. Search mode only; wired from HomeScreen.
 */
@Composable
internal fun ShortsShelf(items: List<VideoRef>, onClick: (VideoRef) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            "Shorts",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.pageUrl }) { ref ->
                ShortsShelfCard(ref, onClick = { onClick(ref) })
            }
        }
    }
}

@Composable
private fun ShortsShelfCard(ref: VideoRef, onClick: () -> Unit) {
    Column(Modifier.width(SHELF_CARD_WIDTH).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            ref.thumbnailUrl?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            ref.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Compact form, no " views" suffix -- the card is narrow (ShortsGrid.kt's tile uses the
        // same trim for the same reason).
        ref.viewCountText?.removeSuffix(" views")?.let {
            Text(
                it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.fyiExtras.ink3,
            )
        }
    }
}
