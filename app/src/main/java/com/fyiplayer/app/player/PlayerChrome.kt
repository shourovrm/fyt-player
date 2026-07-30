package com.fyiplayer.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.core.SeekThumbnails

/**
 * All the bottom chrome there is: exactly 48dp tall — elapsed, the scrub track taking every
 * remaining pixel, total, fullscreen. Play/pause, skip and everything else live off this row
 * entirely (a centred overlay and the overflow menu — see [PlayerScreen]) so the one control
 * anyone actually drags gets the room instead of the leftovers.
 */
@Composable
internal fun ControlBar(
    state: PlayerState,
    seekThumbs: SeekThumbnails?,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(mmss(state.positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
        Box(modifier = Modifier.weight(1f)) {
            var sliderWidthPx by remember { mutableStateOf(0) }
            var isScrubbing by remember { mutableStateOf(false) }
            var scrubValueMs by remember { mutableStateOf(0L) }
            Slider(
                value = (if (isScrubbing) scrubValueMs else state.positionMs).toFloat(),
                valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                onValueChange = { value ->
                    isScrubbing = true
                    scrubValueMs = value.toLong()
                },
                onValueChangeFinished = {
                    PlaybackSession.seekTo(scrubValueMs)
                    isScrubbing = false
                },
                // Full 48dp of touchable height even though the drawn track is 4dp — the whole
                // point of this row is that the grab area is big.
                modifier = Modifier
                    .height(48.dp)
                    .padding(horizontal = 6.dp)
                    .onGloballyPositioned { sliderWidthPx = it.size.width },
            )
            if (isScrubbing) {
                val density = LocalDensity.current
                val fraction = (scrubValueMs.toFloat() / state.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                val previewWidthPx = with(density) { SEEK_PREVIEW_WIDTH.roundToPx() }
                val liftPx = with(density) { 88.dp.roundToPx() }
                val leftInsetPx = with(density) { 6.dp.roundToPx() } // Slider's own horizontal padding
                val xPx = (leftInsetPx + fraction * sliderWidthPx - previewWidthPx / 2).toInt()
                SeekThumbnailPreview(
                    image = seekThumbs?.imageFor(scrubValueMs / 1000.0),
                    timestampText = formatPosition(scrubValueMs, state.durationMs),
                    modifier = Modifier.align(Alignment.TopStart).offset { IntOffset(xPx, -liftPx) },
                )
            }
        }
        Text(mmss(state.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
        IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(48.dp)) {
            FullscreenGlyph(fullscreen = fullscreen, tint = Color.White)
        }
    }
}

/**
 * Top-right overflow: quality, speed, the jump grid and (when the caller wired downloads up)
 * save — one tap away instead of permanently occupying the frame. A [DropdownMenu] renders into
 * its own popup window, so it's never clipped by the player [Box] it's anchored inside the way a
 * hand-positioned Column would be.
 */
@Composable
internal fun OverflowControls(
    state: PlayerState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDownload: (() -> Unit)?,
    onQuality: () -> Unit,
    onSpeed: () -> Unit,
    onPreviewGrid: () -> Unit,
    canPreviewGrid: Boolean,
    modifier: Modifier = Modifier,
) {
    val qualityLabel = state.selectedHeight?.let { "${it}p" } ?: "auto"
    Box(modifier = modifier) {
        IconButton(onClick = { onExpandedChange(true) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More controls", tint = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text("Quality") },
                trailingIcon = { Text(qualityLabel, style = MaterialTheme.typography.labelMedium) },
                onClick = { onExpandedChange(false); onQuality() },
            )
            DropdownMenuItem(
                text = { Text("Speed") },
                trailingIcon = { Text("${trimSpeed(state.speed)}x", style = MaterialTheme.typography.labelMedium) },
                onClick = { onExpandedChange(false); onSpeed() },
            )
            if (canPreviewGrid) {
                DropdownMenuItem(
                    text = { Text("Preview grid") },
                    onClick = { onExpandedChange(false); onPreviewGrid() },
                )
            }
            if (onDownload != null) {
                DropdownMenuItem(
                    text = { Text("Save") },
                    onClick = { onExpandedChange(false); onDownload() },
                )
            }
        }
    }
}

/** The centred 64dp play/pause control — the only play affordance now that the bottom row is
 *  scrub-track-only. [playing] false draws the play triangle, true the pause bars. */
@Composable
internal fun CenterPlayButton(onClick: () -> Unit, playing: Boolean, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .semantics { contentDescription = if (playing) "Pause" else "Play" },
    ) {
        if (playing) {
            PauseGlyph(tint = Color.White, size = 28.dp)
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}

internal fun mmss(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

internal fun formatPosition(positionMs: Long, durationMs: Long): String =
    "${mmss(positionMs)} / ${mmss(durationMs)}"

/** Null when there's no queue at all — a single video has no position to show. */
internal fun queuePositionLabel(queueSize: Int, index: Int): String? =
    if (queueSize > 1) "${index + 1} of $queueSize" else null
