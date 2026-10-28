package com.fyiplayer.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fyiplayer.app.R
import com.fyiplayer.app.data.repo.youtubeThumbnailFor
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.core.VideoRef

/**
 * A slim strip showing where playback is in the queue, expanding on tap into the full list —
 * reorder with the up/down arrows, remove with the ×, jump by tapping a row (which also hands the
 * ref to [onOpen] -- the watch page, so a jump lands on that video's page, not just in the mini
 * player). Only rendered for an actual queue (more than one item); a single video has nothing to
 * show here.
 *
 * ponytail: reorder is two IconButtons per row, not drag-to-reorder — no drag-reorder dependency
 * is on the classpath and the queue is rarely more than a handful of items. Swap for a real drag
 * handle if long queues make tap-to-nudge annoying.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBar(onOpen: (VideoRef) -> Unit = {}, modifier: Modifier = Modifier) {
    val state by PlaybackSession.state.collectAsState()
    if (state.queueSize <= 1) return
    val current = state.current ?: return
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .height(Sizes.touchTarget)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { expanded = true }
            .padding(start = Spacing.s16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.player_queue_position, state.index + 1, state.queueSize),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            current.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.player_queue_show),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.icon),
        )
        // Close = clear: dropping to a 1-item queue makes this bar's own guard (queueSize <= 1)
        // hide it, which is the intended dismiss affordance. Never touches playback -- queue ≠ player.
        IconButton(onClick = { PlaybackSession.clearQueue() }, modifier = Modifier.size(Sizes.touchTarget)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.player_queue_clear),
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
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            sheetState = sheetState,
            dragHandle = { QueueDragHandle() },
        ) {
            QueueHeader(
                title = state.queueTitle ?: stringResource(R.string.player_queue_default_title),
                position = stringResource(R.string.player_queue_count_short, state.index + 1, state.queueSize),
                channel = current.uploader.orEmpty(),
                shuffled = state.shuffled,
                onShuffle = { PlaybackSession.toggleShuffle() },
                onClearAll = { PlaybackSession.clearQueue(); expanded = false },
            )
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = Spacing.s64 * 7)) {
                // No pageUrl key: the same video can be enqueued twice, and a duplicate LazyColumn
                // key is a hard crash ("Key ... was already used", seen on device).
                itemsIndexed(state.queue) { i, ref ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (i == state.index) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface,
                            )
                            .clickable { expanded = false; if (i != state.index) { PlaybackSession.playAt(i); onOpen(ref) } }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.s16, end = 4.dp),
                        )
                        // Same shape and placeholder as ResultsList's row thumb. The fallback is a
                        // pure pageUrl-to-id string match, so the row still does no I/O.
                        Box(
                            Modifier
                                .padding(end = Spacing.s8)
                                .size(Sizes.queueThumbWidth, Sizes.queueThumbHeight)
                                .clip(RoundedCornerShape(Radius.s))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            val thumb = ref.thumbnailUrl ?: youtubeThumbnailFor(ref.pageUrl)
                            if (thumb != null) {
                                AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Text(
                            ref.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                        )
                        IconButton(onClick = { PlaybackSession.move(i, i - 1) }, enabled = i > 0, modifier = Modifier.size(Sizes.touchTarget)) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.player_queue_move_up))
                        }
                        IconButton(
                            onClick = { PlaybackSession.move(i, i + 1) },
                            enabled = i < state.queue.lastIndex,
                            modifier = Modifier.size(Sizes.touchTarget),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.player_queue_move_down))
                        }
                        IconButton(onClick = { PlaybackSession.removeAt(i) }, modifier = Modifier.size(Sizes.touchTarget)) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove_from_queue))
                        }
                    }
                }
            }
        }
    }
}

// Material's DragHandle pads 22 dp above and below its grip, which left a band of empty sheet over
// the header. This grip keeps the same 4 dp height and 32 dp width with only the padding needed.
@Composable
private fun QueueDragHandle() {
    Box(Modifier.fillMaxWidth().padding(top = Spacing.s8, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = Spacing.s32, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)),
        )
    }
}

/**
 * Queue name and position on the left, Shuffle and Clear on the right. The position is its own
 * Text beside the title, so a long title truncates while "(1/30)" always stays fully visible.
 */
@Composable
private fun QueueHeader(
    title: String,
    position: String,
    channel: String,
    shuffled: Boolean,
    onShuffle: () -> Unit,
    onClearAll: () -> Unit,
) {
    // Icon-only buttons have no visible label, so these strings are their only accessible name.
    val shuffleLabel = stringResource(if (shuffled) R.string.player_queue_shuffle_on else R.string.player_queue_shuffle)
    val clearLabel = stringResource(R.string.action_clear)
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.s16, end = Spacing.s8, bottom = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    position,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (channel.isNotEmpty()) {
                Text(
                    channel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onShuffle,
            modifier = Modifier.size(Sizes.touchTarget).semantics { contentDescription = shuffleLabel },
        ) {
            ShuffleGlyph(tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(
            onClick = onClearAll,
            modifier = Modifier.size(Sizes.touchTarget).semantics { contentDescription = clearLabel },
        ) {
            ClearAllGlyph(tint = MaterialTheme.colorScheme.primary)
        }
    }
}
