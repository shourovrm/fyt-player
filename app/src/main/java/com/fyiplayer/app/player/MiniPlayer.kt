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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.rememberFyiApp
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import androidx.media3.ui.AspectRatioFrameLayout
import com.fyiplayer.app.core.VideoRef

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
    val captionSize by rememberFyiApp().prefs.captionSizeVideo.collectAsState(initial = CaptionSize.DEFAULT)
    val ref = state.current ?: return

    val position = if (state.queueSize > 1) {
        stringResource(R.string.player_queue_position, state.index + 1, state.queueSize)
    } else {
        null
    }
    val sourceLabel = ref.sourceId.uppercase()

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 4.dp, modifier = modifier.fillMaxWidth()) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Sizes.miniPlayer)
                    .clickable(onClickLabel = stringResource(R.string.player_open)) { onOpen(ref) }
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(Sizes.miniThumbWidth, Sizes.miniThumbHeight)
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(MaterialTheme.colorScheme.scrim),
                ) {
                    SharedVideoSurface(
                        player = PlaybackSession.exoPlayer,
                        // ZOOM, not FIT: a 96x54 box with letterbox bars either side reads as
                        // broken, and the thumb is a glance target, not a viewing surface.
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        captionSize = captionSize,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(ref.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MiniPlayerSubtitle(position, sourceLabel)
                }
                IconButton(onClick = { PlaybackSession.togglePlayPause() }, modifier = Modifier.size(Sizes.touchTarget)) {
                    if (state.isPlaying) {
                        PauseGlyph(tint = MaterialTheme.colorScheme.onSurface, size = Sizes.icon)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play))
                    }
                }
                // Skip-next only exists when there is somewhere to skip to.
                if (state.queueSize > 1) {
                    IconButton(onClick = { PlaybackSession.skipNext() }, modifier = Modifier.size(Sizes.touchTarget)) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.player_next_video))
                    }
                }
                IconButton(onClick = { PlaybackSession.clear() }, modifier = Modifier.size(Sizes.touchTarget)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.player_stop))
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
            .height(Sizes.progressThin)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}
