package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fyiplayer.app.core.VideoRef

/**
 * The Shorts tab lands here first: a portrait thumbnail grid, not an immediately-playing pager.
 * Tapping a tile opens the full-screen pager at that tile's index (ShortsScreen.kt owns the
 * grid/pager toggle). No I/O per tile (project rule 6) -- everything painted here already came
 * back with the feed. No duration badge, ever: a short's `durationSeconds` is null (never
 * published for this shape of listing), so there is nothing honest to render.
 */
@Composable
internal fun ShortsGrid(
    items: List<VideoRef>,
    gridState: LazyGridState,
    onOpenPlayer: (Int) -> Unit,
    onLongPress: (VideoRef) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Feed items are already deduped by page URL (ShortsFeed.kt's interleave), so a stable
        // key here is safe and lets a mid-scroll refresh keep tiles instead of re-laying them all.
        itemsIndexed(items, key = { _, ref -> ref.pageUrl }) { index, ref ->
            ShortsGridTile(ref, onClick = { onOpenPlayer(index) }, onLongPress = { onLongPress(ref) })
        }
    }
}

@Composable
private fun ShortsGridTile(ref: VideoRef, onClick: () -> Unit, onLongPress: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        ref.thumbnailUrl?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(6.dp)) {
            Text(
                ref.title,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
            )
            // Compact form: channel · views (no " views" suffix -- the cell is narrow) · short age.
            // Video rows keep the full "views" word (ResultsList.kt); shorts cells don't have room.
            val meta = listOfNotNull(ref.uploader, ref.viewCountText?.removeSuffix(" views"), shortAge(ref.uploadedText))
                .joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
