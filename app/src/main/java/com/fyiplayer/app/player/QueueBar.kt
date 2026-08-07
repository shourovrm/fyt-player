package com.fyiplayer.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val QUEUE_BAR_HEIGHT = 44.dp

/**
 * A slim strip showing where playback is in the queue, expanding on tap into the full list —
 * reorder with the up/down arrows, remove with the ×, jump by tapping a row. Only rendered for an
 * actual queue (more than one item); a single video has nothing to show here.
 *
 * ponytail: reorder is two IconButtons per row, not drag-to-reorder — no drag-reorder dependency
 * is on the classpath and the queue is rarely more than a handful of items. Swap for a real drag
 * handle if long queues make tap-to-nudge annoying.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBar(modifier: Modifier = Modifier) {
    val state by PlaybackSession.state.collectAsState()
    if (state.queueSize <= 1) return
    val current = state.current ?: return
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .height(QUEUE_BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { expanded = true }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "${state.index + 1} of ${state.queueSize}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            current.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = "Show queue",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        // Close = clear: dropping to a 1-item queue makes this bar's own guard (queueSize <= 1)
        // hide it, which is the intended dismiss affordance. Never touches playback -- queue ≠ player.
        IconButton(onClick = { PlaybackSession.clearQueue() }, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close queue",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (expanded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val listState = rememberLazyListState()
        // Open on the item that is playing, not at the top: with a long queue the useful rows are
        // the ones around the current position.
        LaunchedEffect(state.index) { listState.scrollToItem(state.index.coerceAtLeast(0)) }
        ModalBottomSheet(onDismissRequest = { expanded = false }, sheetState = sheetState) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Queue — ${state.index + 1} of ${state.queueSize}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { PlaybackSession.clearQueue(); expanded = false }) {
                    Text("Clear")
                }
            }
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(state.queue, key = { _, ref -> ref.pageUrl }) { i, ref ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (i == state.index) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface,
                            )
                            .clickable { PlaybackSession.playAt(i) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                        )
                        Text(
                            ref.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                        )
                        IconButton(onClick = { PlaybackSession.move(i, i - 1) }, enabled = i > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { PlaybackSession.move(i, i + 1) },
                            enabled = i < state.queue.lastIndex,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        IconButton(onClick = { PlaybackSession.removeAt(i) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove from queue")
                        }
                    }
                }
            }
        }
    }
}
