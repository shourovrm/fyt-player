package com.fyiplayer.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
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
 * All the bottom chrome there is, ~30% slimmer than the original two-purpose row: a 2.5dp seek
 * track with a 10dp thumb, then one line underneath -- elapsed/total on the left, a right-aligned
 * icon cluster (preview grid, captions, fullscreen) at 16dp. Play/pause, skip and everything else
 * live off this row entirely (a centred overlay and the overflow menu — see [PlayerScreen]), and
 * quality/speed/save stay in that overflow menu; only the preview-grid trigger moved here from
 * there, next to the seekbar it actually controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ControlBar(
    state: PlayerState,
    seekThumbs: SeekThumbnails?,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    captionsAvailable: Boolean,
    onOpenCaptions: () -> Unit,
    canPreviewGrid: Boolean,
    onOpenPreviewGrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        var sliderWidthPx by remember { mutableStateOf(0) }
        var isScrubbing by remember { mutableStateOf(false) }
        var scrubValueMs by remember { mutableStateOf(0L) }
        val shownMs = if (isScrubbing) scrubValueMs else state.positionMs
        val durationMs = state.durationMs.coerceAtLeast(1L)
        val fraction = (shownMs.toFloat() / durationMs).coerceIn(0f, 1f)

        Box(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = shownMs.toFloat(),
                valueRange = 0f..durationMs.toFloat(),
                onValueChange = { value ->
                    isScrubbing = true
                    scrubValueMs = value.toLong()
                },
                onValueChangeFinished = {
                    PlaybackSession.seekTo(scrubValueMs)
                    isScrubbing = false
                },
                // Custom track/thumb slots (material3 1.4's Slider supports both) draw the exact
                // 2.5dp/10dp the slimmer chrome calls for -- SliderDefaults' own Track/Thumb have no
                // parameter for a track this thin. The 24dp row height keeps the touch target
                // bigger than the drawn line, same idea as the original 48dp row had.
                track = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                },
                thumb = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                },
                modifier = Modifier
                    .height(24.dp)
                    .onGloballyPositioned { sliderWidthPx = it.size.width },
            )
            if (isScrubbing) {
                val density = LocalDensity.current
                val previewWidthPx = with(density) { SEEK_PREVIEW_WIDTH.roundToPx() }
                val liftPx = with(density) { 80.dp.roundToPx() }
                val xPx = (fraction * sliderWidthPx - previewWidthPx / 2).toInt()
                SeekThumbnailPreview(
                    image = seekThumbs?.imageFor(scrubValueMs / 1000.0),
                    timestampText = formatPosition(scrubValueMs, state.durationMs),
                    modifier = Modifier.align(Alignment.TopStart).offset { IntOffset(xPx, -liftPx) },
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatPosition(state.positionMs, state.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            Box(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenPreviewGrid, enabled = canPreviewGrid, modifier = Modifier.size(32.dp)) {
                    PreviewGridGlyph(tint = if (canPreviewGrid) Color.White else Color.White.copy(alpha = 0.35f), size = 16.dp)
                }
                IconButton(onClick = onOpenCaptions, enabled = captionsAvailable, modifier = Modifier.size(32.dp)) {
                    CaptionsGlyph(tint = Color.White, dim = !captionsAvailable, size = 16.dp)
                }
                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(32.dp)) {
                    FullscreenGlyph(fullscreen = fullscreen, tint = Color.White, size = 16.dp)
                }
            }
        }
    }
}

/**
 * Top-right overflow: quality, speed and (when the caller wired downloads up) save — one tap away
 * instead of permanently occupying the frame. Preview grid lives in [ControlBar] now, next to the
 * seekbar it jumps around on, not buried in here. A [DropdownMenu] renders into its own popup
 * window, so it's never clipped by the player [Box] it's anchored inside the way a hand-positioned
 * Column would be.
 */
@Composable
internal fun OverflowControls(
    state: PlayerState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDownload: (() -> Unit)?,
    onQuality: () -> Unit,
    onSpeed: () -> Unit,
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
