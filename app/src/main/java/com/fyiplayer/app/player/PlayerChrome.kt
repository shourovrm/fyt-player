package com.fyiplayer.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
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
 * All the bottom chrome there is. Two states, one composable, so a drag can begin on the idle
 * bar without a tap first:
 *  - collapsed (controls hidden, portrait only): a 3dp progress line on the video's bottom edge
 *    with a 10dp dot -- the shorts bar's shape. Touching it reports scrubbing, which the caller
 *    turns into "show the controls" as the same gesture.
 *  - expanded: a bottom gradient, then in portrait the text row ABOVE the bar (time left;
 *    quality · speed · captions · fullscreen right) and the bar on the video's edge, full width
 *    (YouTube); in fullscreen the bar 24dp in from the screen sides with the row BELOW it
 *    (PipePipe -- the thumb otherwise sits under the rounded screen corner).
 * The thumb is 12dp idle and 20dp under the finger; the 40dp-tall touch target never changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ControlBar(
    seekThumbs: SeekThumbnails?,
    fullscreen: Boolean,
    expanded: Boolean,
    onToggleFullscreen: () -> Unit,
    captionsAvailable: Boolean,
    onOpenCaptions: () -> Unit,
    qualityLabel: String,
    onOpenQuality: () -> Unit,
    speedLabel: String,
    onOpenSpeed: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Collected HERE, not by the caller (PlayerScreen) -- this is the one 500ms-ticking read in
    // the whole player screen; keeping it local to the seek bar means the tick recomposes just
    // this Column, not the entire player chrome tree (CLAUDE.md perf task).
    val progress by PlaybackSession.progress.collectAsState()
    var sliderWidthPx by remember { mutableStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubValueMs by remember { mutableStateOf(0L) }
    val shownMs = if (isScrubbing) scrubValueMs else progress.positionMs
    val durationMs = progress.durationMs.coerceAtLeast(1L)
    val fraction = (shownMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val sideInset = if (fullscreen) 24.dp else 0.dp

    val slider: @Composable () -> Unit = {
        // Height pinned to the slider's own 40dp -- NOT left to wrap its children. The preview
        // is a sibling shown only while scrubbing; an unbounded Box would size itself to that
        // preview's natural height the instant a drag starts, shoving the slider up the screen
        // mid-drag so the thumb jumped out from under the finger (reported: several times).
        Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Slider(
                value = shownMs.toFloat(),
                valueRange = 0f..durationMs.toFloat(),
                onValueChange = { value ->
                    // The caller must know too: PlayerScreen's 3s auto-hide otherwise unmounts
                    // this whole bar mid-drag and the disposal releases the drag (reported:
                    // "released after around 4 sec"); in the collapsed state it is also what
                    // brings the rest of the controls up.
                    if (!isScrubbing) onScrubbingChange(true)
                    isScrubbing = true
                    scrubValueMs = value.toLong()
                },
                onValueChangeFinished = {
                    PlaybackSession.seekTo(scrubValueMs)
                    isScrubbing = false
                    onScrubbingChange(false)
                },
                // Custom track/thumb slots (material3 1.4's Slider supports both) draw the thin
                // line the chrome calls for -- SliderDefaults' own Track/Thumb have no parameter
                // for a track this thin. Drag hit-testing is Slider's own layout bounds, not the
                // thumb's, so the 40dp modifier height is the real grab area.
                track = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (expanded) 2.5.dp else 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(SEEK_RED),
                        )
                    }
                },
                thumb = {
                    // 28dp box is the grab target; the dot inside is all that's drawn -- 10dp on
                    // the idle line, 12dp with controls up (PipePipe's always-visible thumb was
                    // the easiest to grab of the three apps compared), 20dp under the finger.
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(if (isScrubbing) 20.dp else if (expanded) 12.dp else 10.dp)
                                .clip(CircleShape)
                                .background(SEEK_RED),
                        )
                    }
                },
                modifier = Modifier
                    .height(40.dp)
                    .padding(horizontal = sideInset)
                    .onGloballyPositioned { sliderWidthPx = it.size.width },
            )
            if (isScrubbing) {
                val density = LocalDensity.current
                val previewWidth = if (fullscreen) SEEK_PREVIEW_WIDTH_FULLSCREEN else SEEK_PREVIEW_WIDTH_PORTRAIT
                val previewWidthPx = with(density) { previewWidth.roundToPx() }
                val gapPx = with(density) { SEEK_PREVIEW_GAP.roundToPx() }
                val insetPx = with(density) { sideInset.roundToPx() }
                // Measured, not guessed: the card drops its thumbnail box when no image is
                // available yet (see SeekThumbnailPreview), so its real height varies -- a fixed
                // lift only fit one of the two cases. Seeded with a close estimate so the very
                // first scrub frame doesn't visibly pop once the real measurement lands.
                var previewHeightPx by remember {
                    mutableStateOf(with(density) { (previewWidth * 9 / 16 + 40.dp).roundToPx() })
                }
                // Clamped to the slider's own width so the card never pokes past either edge
                // instead of centering blindly on the thumb.
                val xPx = (insetPx + fraction * sliderWidthPx - previewWidthPx / 2)
                    .toInt()
                    .coerceIn(insetPx, (insetPx + sliderWidthPx - previewWidthPx).coerceAtLeast(insetPx))
                SeekThumbnailPreview(
                    image = seekThumbs?.imageFor(scrubValueMs / 1000.0),
                    timestampText = formatPosition(scrubValueMs, progress.durationMs),
                    width = previewWidth,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        // unbounded: the 40dp Box above would otherwise clamp the card to what's
                        // left after the timestamp -- the 79dp frame came out ~27dp tall, a 3x
                        // vertical squash (reported: "too wide").
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .offset { IntOffset(xPx, -(previewHeightPx + gapPx)) }
                        .onGloballyPositioned { previewHeightPx = it.size.height },
                )
            }
        }
    }

    // Fullscreen idle shows nothing at all (YouTube); portrait idle keeps the thin line so
    // progress stays visible and a drag can start without a tap.
    if (!expanded && fullscreen) return

    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp + sideInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatPosition(progress.positionMs, progress.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(shadow = Shadow(Color.Black, blurRadius = 6f)),
            )
            Box(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                ChromeTextButton(qualityLabel, onClick = onOpenQuality)
                ChromeTextButton(speedLabel, onClick = onOpenSpeed)
                IconButton(onClick = onOpenCaptions, enabled = captionsAvailable, modifier = Modifier.size(32.dp)) {
                    CaptionsGlyph(tint = Color.White, dim = !captionsAvailable, size = 16.dp)
                }
                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(32.dp)) {
                    FullscreenGlyph(fullscreen = fullscreen, tint = Color.White, size = 16.dp)
                }
            }
        }
    }

    // ONE call site for the slider in every state. A drag that starts on the collapsed line
    // expands the bar mid-gesture; if the slider were composed from a different branch for each
    // state it would be a new Slider instance and the old one's drag would never finish --
    // isScrubbing stuck true, preview frozen on screen (seen live). Only the modifiers and the
    // conditional row around it change.
    val chrome = if (expanded) {
        Modifier
            .fillMaxWidth()
            // Gradient, not a solid band: the bottom fifth of the video stays visible through it.
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            .padding(top = 24.dp)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(modifier = modifier.then(chrome)) {
        // Offset into the slider box's own (touch-only) top area so text sits ~10dp above the
        // line instead of 34dp; the box below is the hit area, not drawn chrome.
        if (expanded && !fullscreen) Box(Modifier.offset(y = 16.dp)) { row() }
        // Portrait: pushed down so the line sits on the video's bottom edge, not 20dp above it;
        // the touch box keeps its full 40dp, the overflow is only the invisible lower half of
        // the thumb. Same push idle and expanded, so the bar never jumps when controls appear.
        Box(Modifier.fillMaxWidth().height(40.dp).offset(y = if (fullscreen) 0.dp else 14.dp)) { slider() }
        if (expanded && fullscreen) Box(Modifier.padding(bottom = 6.dp)) { row() }
    }
}

// YouTube's seek red: the theme's primary blue read as dim over video (user-reported).
private val SEEK_RED = Color(0xFFFF0033)

/** Quality ("720p") and speed ("1x") as plain text buttons in the bottom row -- both apps
 *  compared show them as text, and there is no glyph in material-icons-core for either. */
@Composable
private fun ChromeTextButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium.copy(shadow = Shadow(Color.Black, blurRadius = 6f)),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
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
