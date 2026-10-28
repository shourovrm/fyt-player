package com.fyiplayer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

/**
 * A channel's Shorts tab as the same portrait grid the main Shorts tab uses. Loading, empty and
 * error states mirror [VideoTabBody], so the tab reads the same whichever layout it uses.
 */
@Composable
internal fun ChannelShortsGrid(
    state: VideoTabState,
    selection: Set<String>,
    gridState: LazyGridState,
    onTap: (VideoRef) -> Unit,
    onToggle: (VideoRef) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.items.isEmpty() && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.state_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // A finished load with nothing in it and no error is a real empty tab, not a blank grid.
        state.loaded && state.items.isEmpty() && state.error == null -> EmptyStateScreen(stringResource(R.string.channel_empty_videos_title))
        else -> ChannelShortsTiles(state, selection, gridState, onTap, onToggle, onLoadMore, onRetry)
    }
}

/** Same 3-column, 2 dp gutter grid as the main Shorts tab ([ShortsGrid]). */
@Composable
private fun ChannelShortsTiles(
    state: VideoTabState,
    selection: Set<String>,
    gridState: LazyGridState,
    onTap: (VideoRef) -> Unit,
    onToggle: (VideoRef) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val hasMore = state.nextPage != null
    val isLoadingMore = state.loading && state.items.isNotEmpty()
    // Fires a few rows before the bottom, same trigger as ResultsListColumn's list paging.
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layout.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, hasMore, isLoadingMore, state.error) {
        if (shouldAutoLoadMore(nearEnd, hasMore, isLoadingMore, loadMoreFailed = state.error != null)) onLoadMore()
    }

    val selecting = selection.isNotEmpty()
    val channelErrorName = stringResource(R.string.channel_error_source)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        // Selection mode shows a Play button over the bottom row; leave room so it hides no caption.
        contentPadding = PaddingValues(start = 2.dp, top = 2.dp, end = 2.dp, bottom = if (selecting) Spacing.s24 * 4 else 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.items, key = { it.pageUrl }) { ref ->
            ShortsGridTile(
                ref,
                onClick = { if (selecting) onToggle(ref) else onTap(ref) },
                onLongPress = { onToggle(ref) },
                selected = ref.pageUrl in selection,
            )
        }
        if (isLoadingMore) {
            item(key = "loadingMore", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(Spacing.s16), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(Sizes.icon))
                }
            }
        }
        // A failed page gets the shared error row with Retry; an access wall gets no Retry.
        state.error?.let { message ->
            item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                ErrorRowView(ErrorRow(channelErrorName, message, onRetry = if (state.blocked) null else onRetry))
            }
        }
    }
}
