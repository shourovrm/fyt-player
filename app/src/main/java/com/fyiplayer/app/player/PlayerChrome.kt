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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.foundation.layout.wrapContentHeight
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
    seekThumbs: SeekThumbnails?,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    captionsAvailable: Boolean,
    onOpenCaptions: () -> Unit,
    onOpenPreviewGrid: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Collected HERE, not by the caller (PlayerScreen) -- this is the one 500ms-ticking read in
    // the whole player screen; keeping it local to the seek bar means the tick recomposes just
    // this Column, not the entire player chrome tree (CLAUDE.md perf task).
    val progress by PlaybackSession.progress.collectAsState()
    val canPreviewGrid = seekThumbs != null && progress.durationMs > 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        var sliderWidthPx by remember { mutableStateOf(0) }
        var isScrubbing by remember { mutableStateOf(false) }
        var scrubValueMs by remember { mutableStateOf(0L) }
        val shownMs = if (isScrubbing) scrubValueMs else progress.positionMs
        val durationMs = progress.durationMs.coerceAtLeast(1L)
        val fraction = (shownMs.toFloat() / durationMs).coerceIn(0f, 1f)

        // Height pinned to the slider's own 40dp -- NOT left to wrap its children. The preview
        // below is a sibling shown only while scrubbing; an unbounded Box would size itself to
        // that preview's ~90dp natural height (Modifier.offset shifts placement, not the measured
        // size the parent sees) the instant a drag starts. This Column is bottom-anchored in
        // PlayerScreen, so that growth shoved the slider itself up the screen mid-drag -- the
        // thumb jumped out from under the finger and the drag "released" (reported: several times).
        Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Slider(
                value = shownMs.toFloat(),
                valueRange = 0f..durationMs.toFloat(),
                onValueChange = { value ->
                    // The caller must know too: PlayerScreen's 3s auto-hide otherwise unmounts
                    // this whole bar mid-drag and the disposal releases the drag (reported:
                    // "released after around 4 sec").
                    if (!isScrubbing) onScrubbingChange(true)
                    isScrubbing = true
                    scrubValueMs = value.toLong()
                },
                onValueChangeFinished = {
                    PlaybackSession.seekTo(scrubValueMs)
                    isScrubbing = false
                    onScrubbingChange(false)
                },
                // Custom track/thumb slots (material3 1.4's Slider supports both) draw the exact
                // 2.5dp/10dp the slimmer chrome calls for -- SliderDefaults' own Track/Thumb have no
                // parameter for a track this thin. Drag hit-testing is Slider's own layout bounds,
                // not the thumb's, so a 24dp row let fingers slip off (reported: released easily) --
                // 40dp modifier height grabs a real touch target while track/thumb stay visually thin.
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
                    // 28dp box is the grab target; the dot inside is all that's drawn -- 12dp
                    // idle (findable, PipePipe's 14dp was the easiest to grab of the three apps
                    // compared), 20dp under the finger like YouTube/PipePipe.
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(if (isScrubbing) 20.dp else 12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                },
                modifier = Modifier
                    .height(40.dp)
                    .onGloballyPositioned { sliderWidthPx = it.size.width },
            )
            if (isScrubbing) {
                val density = LocalDensity.current
                val previewWidth = if (fullscreen) SEEK_PREVIEW_WIDTH_FULLSCREEN else SEEK_PREVIEW_WIDTH_PORTRAIT
                val previewWidthPx = with(density) { previewWidth.roundToPx() }
                val gapPx = with(density) { SEEK_PREVIEW_GAP.roundToPx() }
                // Measured, not guessed: the card drops its thumbnail box when no image is
                // available yet (see SeekThumbnailPreview), so its real height varies -- a fixed
                // lift only fit one of the two cases. Seeded with a close estimate so the very
                // first scrub frame doesn't visibly pop once the real measurement lands.
                var previewHeightPx by remember {
                    mutableStateOf(with(density) { (previewWidth * 9 / 16 + 40.dp).roundToPx() })
                }
                // Clamped to the slider's own width -- the track already spans the full available
                // width (minus the Column's edge padding), so this keeps the card from poking past
                // either edge instead of centering blindly on the thumb.
                val xPx = (fraction * sliderWidthPx - previewWidthPx / 2)
                    .toInt()
                    .coerceIn(0, (sliderWidthPx - previewWidthPx).coerceAtLeast(0))
                SeekThumbnailPreview(
                    image = seekThumbs?.imageFor(scrubValueMs / 1000.0),
                    timestampText = formatPosition(scrubValueMs, progress.durationMs),
                    width = previewWidth,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        // unbounded: this Box is pinned to 40dp (see above) and would otherwise
                        // clamp the card to what's left after the timestamp -- the 79dp frame
                        // came out ~27dp tall, a 3x vertical squash (reported: "too wide").
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .offset { IntOffset(xPx, -(previewHeightPx + gapPx)) }
                        .onGloballyPositioned { previewHeightPx = it.size.height },
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatPosition(progress.positionMs, progress.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
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

/** [forceHours] widens m:ss to h:mm:ss even when this value's own hours digit is zero, so a
 *  position label stays the same width as its duration ("0:05:12 / 1:41:12") instead of the
 *  label reflowing as the clock crosses the hour mark. */
internal fun mmss(ms: Long, forceHours: Boolean = false): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0 || forceHours) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun formatPosition(positionMs: Long, durationMs: Long): String {
    val hasHours = durationMs / 1000 >= 3600
    return "${mmss(positionMs, forceHours = hasHours)} / ${mmss(durationMs)}"
}

/** Null when there's no queue at all — a single video has no position to show. */
internal fun queuePositionLabel(queueSize: Int, index: Int): String? =
    if (queueSize > 1) "${index + 1} of $queueSize" else null
