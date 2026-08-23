package com.fyiplayer.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import com.fyiplayer.app.core.VideoRef

private val MINI_BAR_HEIGHT = 66.dp
private val MINI_THUMB_WIDTH = 106.dp
private val MINI_THUMB_HEIGHT = 60.dp
private val MINI_PROGRESS_HEIGHT: Dp = 2.dp

/**
 * The docked mini player: the live shared surface (moved here, not rebuilt — see
 * [SharedVideoSurface]), title, queue position and transport. `AppScaffold` renders this outside
 * its nav bar's own auto-hide [AnimatedVisibility], so playback chrome is never what disappears
 * when the nav slides away.
 *
 * Renders nothing when nothing is playing, so a caller can drop this into a layout slot
 * unconditionally.
 */
@Composable
fun MiniPlayer(onOpen: (VideoRef) -> Unit, modifier: Modifier = Modifier) {
    val state by PlaybackSession.state.collectAsState()
    val ref = state.current ?: return

    val position = queuePositionLabel(state.queueSize, state.index)
    val sourceLabel = ref.sourceId.uppercase()

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(MINI_BAR_HEIGHT)
                    .clickable(onClickLabel = "Open player") { onOpen(ref) }
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .padding(3.dp)
                        .size(MINI_THUMB_WIDTH, MINI_THUMB_HEIGHT)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black),
                ) {
                    SharedVideoSurface(
                        player = PlaybackSession.exoPlayer,
                        // ZOOM, not FIT: a 106x60 box with letterbox bars either side reads as
                        // broken, and the thumb is a glance target, not a viewing surface.
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(ref.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MiniPlayerSubtitle(position, sourceLabel)
                }
                IconButton(onClick = { PlaybackSession.togglePlayPause() }, modifier = Modifier.size(48.dp)) {
                    if (state.isPlaying) {
                        PauseGlyph(tint = MaterialTheme.colorScheme.onSurface, size = 20.dp)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                    }
                }
                // Skip-next only exists when there is somewhere to skip to.
                if (state.queueSize > 1) {
                    IconButton(onClick = { PlaybackSession.skipNext() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next in queue")
                    }
                }
                IconButton(onClick = { PlaybackSession.clear() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop playback")
                }
            }
            MiniPlayerProgressBar(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }
    }
}

/** Elapsed/total time label -- collects [PlaybackSession.progress] itself (not the caller) so the
 *  500ms tick recomposes only this small Text, not the whole [MiniPlayer] row. */
@Composable
private fun MiniPlayerSubtitle(position: String?, sourceLabel: String) {
    val progress by PlaybackSession.progress.collectAsState()
    val subtitle = listOfNotNull(position, sourceLabel, formatPosition(progress.positionMs, progress.durationMs))
        .joinToString(" · ")
    Text(
        subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The thin progress line -- same isolation reason as [MiniPlayerSubtitle]. */
@Composable
private fun MiniPlayerProgressBar(modifier: Modifier = Modifier) {
    val progress by PlaybackSession.progress.collectAsState()
    val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .height(MINI_PROGRESS_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}
