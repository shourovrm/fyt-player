package com.fyiplayer.app.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout

/**
 * Bare host to exercise [PlaybackSession] end to end: the shared surface, play/pause, a seek
 * bar. Full chrome (gestures, overlays, sheets, fullscreen) is a later phase — deliberately not
 * built here. A later UI phase wires equivalents of these controls into `AppScaffold`'s
 * `miniPlayer` / `queueBar` slots; this file is not that wiring.
 */
@Composable
fun PlayerScreenScaffold(modifier: Modifier = Modifier) {
    val state by PlaybackSession.state.collectAsState()

    Column(modifier.fillMaxWidth().padding(8.dp)) {
        SharedVideoSurface(
            player = PlaybackSession.exoPlayer,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Text(state.current?.title ?: "Nothing playing")
        IconButton(onClick = { PlaybackSession.togglePlayPause() }) {
            // material-icons-core has no Pause glyph (see DECISIONS.md); full chrome swaps this
            // for a bundled asset. This minimal host reuses PlayArrow for both states.
            Icon(Icons.Filled.PlayArrow, contentDescription = if (state.isPlaying) "Pause" else "Play")
        }
        Slider(
            value = state.positionMs.toFloat(),
            valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
            onValueChange = { PlaybackSession.seekTo(it.toLong()) },
        )
    }
}
