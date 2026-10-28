package com.fyiplayer.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.core.Chapter
import com.fyiplayer.app.core.SeekThumbnails
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.ui.theme.fyiExtras

/**
 * All the bottom chrome there is. Two states, one composable, so a drag can begin on the idle
 * bar without a tap first:
 *  - collapsed (controls hidden, portrait only): a thin progress line on the video's bottom edge
 *    with a dot -- the shorts bar's shape. Touching it reports scrubbing, which the caller
 *    turns into "show the controls" as the same gesture.
 *  - expanded: a bottom gradient, then in portrait the 48dp control row ABOVE the bar (time left;
 *    icon buttons right) and the bar on the video's edge, full width (YouTube); in fullscreen the
 *    bar 24dp in from the screen sides with the row BELOW it (PipePipe -- the thumb otherwise sits
 *    under the rounded screen corner).
 * The row is the time label, then four icon-only 40dp targets: Speed, Quality, Loop, Fullscreen.
 * Captions moved to the top-right corner (PlayerTopActions). Speed draws its current value as text; Quality draws a fixed "HD" mark, not the
 * chosen height (the quality sheet shows that). Four 40dp targets and the label fit a 360dp-wide
 * row at the default font scale: 160dp of targets plus a label of about 105dp (an hour-long
 * "1:02:03 / 1:41:12") leaves about 75dp of the 340dp between the 10dp side paddings. Fullscreen
 * drops that 10dp gutter (the 24dp inset is already there): a portrait video keeps a portrait
 * fullscreen, and at 360dp the 10dp gutter on top of the inset left only 292dp for the same row. The bar stays
 * on its own full-width line so the thumb never shifts sideways when a drag on the idle line
 * expands the chrome.
 * The thumb is bigger under the finger; the 40dp-tall touch target never changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ControlBar(
    seekThumbs: SeekThumbnails?,
    fullscreen: Boolean,
    expanded: Boolean,
    onToggleFullscreen: () -> Unit,
    onOpenQuality: () -> Unit,
    speedLabel: String,
    onOpenSpeed: () -> Unit,
    looping: Boolean,
    onToggleLoop: () -> Unit,
    chapters: List<Chapter>,
    onScrubbingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Collected HERE, not by the caller (PlayerScreen) -- this is the one 500ms-ticking read in
    // the whole player screen; keeping it local to the seek bar means the tick recomposes just
    // this Column, not the entire player chrome tree (CLAUDE.md perf task).
    val progress by PlaybackSession.progress.collectAsState()
    val sponsorMarkers by PlaybackSession.sponsorMarkers.collectAsState()
    var sliderWidthPx by remember { mutableStateOf(0) }
    // The whole seek row, not the inset slider: the scrub label may reach into the side insets
    // but must stay inside the row's edges.
    var containerWidthPx by remember { mutableStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubValueMs by remember { mutableStateOf(0L) }
    val shownMs = if (isScrubbing) scrubValueMs else progress.positionMs
    val durationMs = progress.durationMs.coerceAtLeast(1L)
    val fraction = (shownMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val sideInset = if (fullscreen) Spacing.s24 else 0.dp
    val extras = MaterialTheme.fyiExtras
    val playedColor = MaterialTheme.colorScheme.primary
    val currentChapterTitle: String = chapterAt(chapters, shownMs)?.title ?: ""
    // Fullscreen already sits 24dp in for the rounded corners. The 10dp portrait gutter on top of
    // that is what runs a 360dp fullscreen row out of room, so fullscreen drops it.
    val rowGutter = if (fullscreen) 0.dp else 10.dp

    val slider: @Composable () -> Unit = {
        // Height pinned to the slider's own 40dp -- NOT left to wrap its children. The preview
        // is a sibling shown only while scrubbing; an unbounded Box would size itself to that
        // preview's natural height the instant a drag starts, shoving the slider up the screen
        // mid-drag so the thumb jumped out from under the finger (reported: several times).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.s40)
                .onGloballyPositioned { containerWidthPx = it.size.width },
        ) {
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
                            .height(if (expanded) Sizes.progressThin else Sizes.progressTrack)
                            .clip(RoundedCornerShape(2.dp))
                            .background(extras.onVideo.copy(alpha = 0.3f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(playedColor),
                        )
                        // Over the fill, so a segment stays visible on the part already played.
                        SponsorMarkers(sponsorMarkers, durationMs, Modifier.matchParentSize())
                        ChapterMarkers(chapters, durationMs, Modifier.matchParentSize())
                    }
                },
                thumb = {
                    // The 24dp box is the grab target; the dot inside is all that's drawn --
                    // 10dp on the idle line, 16dp with controls up (an always-visible thumb was
                    // the easiest to grab of the three apps compared), 24dp under the finger.
                    val dotSize = when {
                        isScrubbing -> Spacing.s24
                        expanded -> Sizes.iconSmall
                        else -> 10.dp
                    }
                    Box(Modifier.size(Spacing.s24), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(playedColor),
                        )
                    }
                },
                modifier = Modifier
                    .height(Spacing.s40)
                    .padding(horizontal = sideInset)
                    .onGloballyPositioned { sliderWidthPx = it.size.width },
            )
            if (isScrubbing) {
                val density = LocalDensity.current
                val previewWidth = Sizes.seekPreviewWidth
                val previewWidthPx = with(density) { previewWidth.roundToPx() }
                val gapPx = with(density) { Spacing.s8.roundToPx() }
                val insetPx = with(density) { sideInset.roundToPx() }
                // Measured, not guessed: the card drops its thumbnail box when no image is
                // available yet (see SeekThumbnailPreview), so its real height varies -- a fixed
                // lift only fit one of the two cases. Seeded with a close estimate so the very
                // first scrub frame doesn't visibly pop once the real measurement lands.
                var previewHeightPx by remember {
                    mutableStateOf(with(density) { (Sizes.seekPreviewHeight + Spacing.s40).roundToPx() })
                }
                // Clamped to the slider's own width so the card never pokes past either edge
                // instead of centering blindly on the thumb.
                val xPx = (insetPx + fraction * sliderWidthPx - previewWidthPx / 2)
                    .toInt()
                    .coerceIn(insetPx, (insetPx + sliderWidthPx - previewWidthPx).coerceAtLeast(insetPx))
                val seekLabel = seekPreviewLabel(
                    positionMs = scrubValueMs,
                    durationMs = progress.durationMs,
                    chapter = chapterAt(chapters, scrubValueMs),
                )
                SeekThumbnailPreview(
                    image = seekThumbs?.imageFor(scrubValueMs / 1000.0),
                    timestampText = seekLabel.timeText,
                    trailingText = seekLabel.trailingText,
                    labelPlacement = SeekLabelPlacement(
                        cardLeftPx = xPx,
                        containerWidthPx = containerWidthPx,
                        marginPx = with(density) { Spacing.pageMargin.roundToPx() },
                    ),
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
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.touchTarget)
                .padding(horizontal = rowGutter + sideInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatPosition(progress.positionMs, progress.durationMs),
                color = extras.onVideo,
                style = MaterialTheme.typography.labelMedium,
            )
            Box(modifier = Modifier.weight(1f))
            ControlIcon(description = stringResource(R.string.player_menu_speed), onClick = onOpenSpeed) {
                Text(speedLabel, color = extras.onVideo, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            ControlIcon(description = stringResource(R.string.player_menu_quality), onClick = onOpenQuality) {
                Text(
                    stringResource(R.string.player_quality_glyph),
                    color = extras.onVideo,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            ControlIcon(
                description = stringResource(if (looping) R.string.player_loop_stop else R.string.player_loop),
                onClick = onToggleLoop,
            ) {
                RepeatGlyph(tint = if (looping) playedColor else extras.onVideo, size = Sizes.icon)
            }
            ControlIcon(
                description = stringResource(
                    if (fullscreen) R.string.player_fullscreen_exit else R.string.player_fullscreen_enter,
                ),
                onClick = onToggleFullscreen,
            ) {
                FullscreenGlyph(fullscreen = fullscreen, tint = extras.onVideo, size = Sizes.icon)
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
            .background(Brush.verticalGradient(listOf(Color.Transparent, extras.videoScrim)))
            .padding(top = Spacing.s24)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(modifier = modifier.then(chrome)) {
        // Offset into the slider box's own (touch-only) top area so the row sits just above the
        // line; the box below is the hit area, not drawn chrome.
        if (expanded && !fullscreen) Box(Modifier.offset(y = Spacing.s16)) { row() }
        // Shown for the whole of a chaptered video, empty before the first chapter starts, so the
        // bar does not jump when the first chapter begins.
        if (expanded && chapters.isNotEmpty()) {
            Text(
                currentChapterTitle,
                color = extras.onVideo,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = rowGutter + sideInset),
            )
        }
        // Portrait: pushed down so the line sits on the video's bottom edge; the touch box keeps
        // its full 40dp, the overflow is only the invisible lower half of the thumb. Same push idle
        // and expanded, so the bar never jumps when controls appear.
        Box(Modifier.fillMaxWidth().height(Spacing.s40).offset(y = if (fullscreen) 0.dp else Spacing.s16)) { slider() }
        if (expanded && fullscreen) Box(Modifier.padding(bottom = 6.dp)) { row() }
    }
}

/** A 40dp icon-only control for the bottom row. Plain clickable box, not [IconButton]: IconButton
 *  enforces a 48dp minimum interactive size, and five of them would overflow a 360dp row. */
@Composable
private fun ControlIcon(
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Spacing.s40)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** The centred 64dp play/pause control -- the only play affordance now that the bottom row is
 *  scrub-track-only. [playing] false draws the play triangle, true the pause bars; [ended] wins
 *  over both and draws Replay. */
@Composable
internal fun CenterPlayButton(onClick: () -> Unit, playing: Boolean, ended: Boolean = false, modifier: Modifier = Modifier) {
    val extras = MaterialTheme.fyiExtras
    val description = stringResource(
        when {
            ended -> R.string.player_replay
            playing -> R.string.player_pause
            else -> R.string.action_play
        },
    )
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(Sizes.transportButton)
            .clip(CircleShape)
            .background(extras.videoScrimLight)
            .semantics { contentDescription = description },
    ) {
        if (ended) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = extras.onVideo, modifier = Modifier.size(Sizes.transportGlyph))
        } else if (playing) {
            PauseGlyph(tint = extras.onVideo, size = Sizes.iconLarge)
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = extras.onVideo, modifier = Modifier.size(Sizes.transportGlyph))
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

/** The playhead clock. Hours appear for every position once the VIDEO is an hour or longer, so the
 *  position and the total always share one format. */
internal fun positionClock(positionMs: Long, durationMs: Long): String =
    mmss(positionMs, forceHours = durationMs / 1000 >= 3600)

internal fun formatPosition(positionMs: Long, durationMs: Long): String =
    "${positionClock(positionMs, durationMs)} / ${mmss(durationMs)}"
