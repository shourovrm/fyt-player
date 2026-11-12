package com.fyiplayer.app.player

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.fyiplayer.app.ui.theme.Sizes

/**
 * A vertical scroll wheel over [values]: three rows tall, the middle row between two lines is the
 * pick, and a fling always comes to rest on a row. [onSelect] gets the middle value whenever it
 * changes. Compose has no wheel picker, so this is a snapping LazyColumn padded by one row top and
 * bottom, which lets the first and last values reach the middle.
 */
@Composable
fun NumberWheel(
    values: List<Int>,
    initial: Int,
    onSelect: (Int) -> Unit,
    format: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val rowHeight = Sizes.touchTarget
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(initial).coerceAtLeast(0))
    // The first visible row sits in the middle slot (the top padding row is above it), until it
    // has scrolled more than half a row up and the next one takes the middle.
    val middle by remember {
        derivedStateOf {
            val next = if (state.firstVisibleItemScrollOffset > rowHeightPx / 2) 1 else 0
            (state.firstVisibleItemIndex + next).coerceIn(values.indices)
        }
    }
    LaunchedEffect(middle) { onSelect(values[middle]) }

    Box(modifier.height(rowHeight * 3)) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            contentPadding = PaddingValues(vertical = rowHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(values) { index, value ->
                val picked = index == middle
                Box(Modifier.height(rowHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        format(value),
                        style = if (picked) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        color = if (picked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.align(Alignment.TopCenter).padding(top = rowHeight))
        HorizontalDivider(Modifier.align(Alignment.BottomCenter).padding(bottom = rowHeight))
    }
}
