package com.fyiplayer.app.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.fyiplayer.app.core.VideoRef
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.SeekThumbnails
import kotlinx.coroutines.delay

/** One friendly line per failure kind — never a stack trace, never the dead signed URL a
 *  Network/Expired message could otherwise carry. */
private fun friendlyMessage(e: ExtractionError): String = when (e) {
    is ExtractionError.Network -> "No connection. Check your network and retry."
    is ExtractionError.ContentUnavailable -> "This video is no longer available."
    is ExtractionError.AccessChallenge -> "Unavailable: this page requires a login, CAPTCHA, or age check."
    // Never claim a retry here: by the time this renders, the one permitted re-resolve is spent.
    is ExtractionError.Expired -> "Stream link expired."
    is ExtractionError.Unsupported -> "Can't play this video right now."
}

/**
 * The full player: shared surface, gesture layer, HUDs, centred transport and the one-row bottom
 * chrome, driven end to end by [PlaybackSession.state]. Works both embedded (pinned atop a
 * scrollable detail page) and fullscreen — [fullscreen] only changes chrome/gesture scaling,
 * system-bar visibility and orientation, never which item is playing, so toggling it never
 * interrupts playback.
 *
 * [fullscreen] is a parameter, not local state: the caller (`ui/DetailScreen.kt`) owns it because
 * it also owns the LAYOUT decision fullscreen implies (an early return to a bare full-size player,
 * skipping the rest of the page) — that can't happen from in here, one layer down.
 *
 * Reads [PlaybackSession.state] straight through [collectAsState] rather than mirroring it into a
 * `LaunchedEffect`-driven copy: a queue can advance underneath a route pinned to one video, and
 * any indirection here is exactly the one-frame "wrong title" flicker CLAUDE.md's pitfalls call out.
 *
 * [gestureBrightness]/[gestureVolume] and [onDownload] are the seams the caller wires from
 * `data/prefs/Prefs.kt` and the (not yet built) download queue — this file imports neither.
 */
@Composable
fun PlayerScreen(
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    gestureBrightness: Boolean = true,
    gestureVolume: Boolean = true,
    onDownload: (() -> Unit)? = null,
    // The watch page's own video, when embedded in one: with the session cleared (notification
    // Close) the surface shows this ref's poster + replay instead of a dead "Nothing playing".
    pageRef: VideoRef? = null,
    modifier: Modifier = Modifier,
) {
    val state by PlaybackSession.state.collectAsState()
    val context = LocalContext.current
    val activity = context.asActivity()

    var showQualitySheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showJumpGrid by remember { mutableStateOf(false) }
    var showCaptionSheet by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var seekThumbs by remember { mutableStateOf<SeekThumbnails?>(null) }

    LaunchedEffect(state.current?.pageUrl) {
        seekThumbs = state.current?.let { fetchSeekThumbnails(it) }
    }

    // Exiting fullscreen (button, or back — handled one level up in DetailScreen, which owns the
    // state) always tears down and rebuilds this composable at a different call site, so onDispose
    // here is what guarantees the bars come back on every exit path, not just this one.
    DisposableEffect(fullscreen) {
        activity?.let { setFullscreen(it, fullscreen) }
        onDispose { activity?.let { setFullscreen(it, false) } }
    }
    // Portrait video must stay portrait fullscreen, not get force-landscaped into a letterbox —
    // narrows the sensor-default lock above once the decoder reports real dimensions.
    LaunchedEffect(fullscreen, state.videoWidth, state.videoHeight) {
        activity?.let { applyAspectOrientation(it, fullscreen, state.videoWidth, state.videoHeight) }
    }
    DisposableEffect(state.isPlaying) {
        activity?.let { setKeepScreenOn(it, state.isPlaying) }
        onDispose { activity?.let { setKeepScreenOn(it, false) } }
    }

    val accumulator = remember { FastSeekAccumulator(stepSeconds = 10) }
    var fastSeekTotal by remember { mutableStateOf<Int?>(null) }
    var fastSeekToken by remember { mutableStateOf(0) }
    var seekBurstBaseMs by remember { mutableStateOf(0L) }
    var brightnessPercent by remember { mutableStateOf(activity?.let(::currentBrightnessPercent) ?: 50) }
    var volumePercent by remember { mutableStateOf(currentStreamVolumePercent(context)) }
    // Float accumulators, not the displayed Ints directly: playerGestures reports a percent delta
    // per pointer-move frame, and most frames move well under 1% -- rounding each one to Int before
    // adding it back would silently drop small drags instead of letting them add up.
    var brightnessAccum by remember { mutableStateOf(brightnessPercent.toFloat()) }
    var volumeAccum by remember { mutableStateOf(volumePercent.toFloat()) }
    var showBrightnessHud by remember { mutableStateOf(false) }
    var showVolumeHud by remember { mutableStateOf(false) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsToken by remember { mutableStateOf(0) }
    fun interact() { controlsVisible = true; controlsToken++ }

    // Entering fullscreen drops the chrome immediately -- controlsVisible defaults true, and
    // without this the bars-follow-chrome effect below would re-show system bars right after
    // the entry hide (forever, when paused: the 3s auto-hide only runs while playing).
    LaunchedEffect(fullscreen) {
        if (fullscreen) controlsVisible = false
    }
    // DELIBERATELY no bars-follow-chrome: the hide/show churn wedges this OEM's inset delivery
    // (the "page shifted right" bug) and even the latched-restore trick didn't hold on device.
    // Bars change exactly twice per session (setFullscreen: hide on entry, show on exit); the
    // status bar stays reachable via the system edge swipe. Do not re-add without a device-
    // verified fix for the wedge.

    LaunchedEffect(fastSeekToken) {
        if (fastSeekTotal == null) return@LaunchedEffect
        delay(800)
        if (!accumulator.isActive()) { fastSeekTotal = null; accumulator.reset() }
    }
    LaunchedEffect(showBrightnessHud, brightnessPercent) {
        if (!showBrightnessHud) return@LaunchedEffect
        delay(800); showBrightnessHud = false
    }
    LaunchedEffect(showVolumeHud, volumePercent) {
        if (!showVolumeHud) return@LaunchedEffect
        delay(800); showVolumeHud = false
    }
    // menuOpen/scrubbing are keys, not just guards: closing the menu or lifting the finger
    // restarts the countdown from zero instead of resuming a timer that expired mid-interaction
    // and would yank the chrome away — a hide during a seekbar drag unmounts the bar and
    // releases the drag under the user's finger.
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(controlsToken, state.isPlaying, menuOpen, scrubbing) {
        if (!state.isPlaying || menuOpen || scrubbing) return@LaunchedEffect
        delay(3000); controlsVisible = false
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        val ref = state.current
        val error = state.error
        when {
            ref == null && pageRef != null -> {
                pageRef.thumbnailUrl?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                IconButton(
                    onClick = { PlaybackSession.play(listOf(pageRef), 0) },
                    modifier = Modifier.align(Alignment.Center).size(72.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            ref == null -> Text("Nothing playing", color = Color.White, modifier = Modifier.align(Alignment.Center))
            error != null -> Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(friendlyMessage(error), color = Color.White)
                // An AccessChallenge is an honest wall (login/CAPTCHA/age) -- re-resolving can't
                // pass it, so no Retry there, same convention as ResultsList's onRetry = null.
                if (error !is ExtractionError.AccessChallenge) {
                    Spacer(Modifier.height(12.dp))
                    // The reported "stuck play button": an error used to render dead-end text with
                    // no way forward short of leaving the screen. retryCurrent() re-resolves the
                    // same item from where it left off -- not a bypass of whatever stopped it.
                    OutlinedButton(
                        onClick = { PlaybackSession.retryCurrent() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    ) { Text("Retry") }
                }
            }
            else -> {
                SharedVideoSurface(
                    player = PlaybackSession.exoPlayer,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    modifier = Modifier
                        .fillMaxSize()
                        .playerGestures(
                            fullscreen = fullscreen,
                            gestureBrightness = gestureBrightness,
                            gestureVolume = gestureVolume,
                            accumulator = accumulator,
                            onBrightnessDrag = { deltaPercent ->
                                brightnessAccum = (brightnessAccum + deltaPercent).coerceIn(0f, 100f)
                                brightnessPercent = brightnessAccum.toInt()
                                showBrightnessHud = true
                                activity?.let { setWindowBrightness(it, brightnessPercent) }
                            },
                            onVolumeDrag = { deltaPercent ->
                                volumeAccum = (volumeAccum + deltaPercent).coerceIn(0f, 100f)
                                volumePercent = volumeAccum.toInt()
                                showVolumeHud = true
                                setStreamVolumePercent(context, volumePercent)
                            },
                            onSeekDrag = { deltaPx ->
                                val deltaMs = (deltaPx * 200).toLong()
                                val base = seekPreviewMs ?: state.positionMs
                                seekPreviewMs = (base + deltaMs).coerceIn(0L, state.durationMs.coerceAtLeast(1L))
                            },
                            onSeekDragEnd = {
                                seekPreviewMs?.let { PlaybackSession.seekTo(it) }
                                seekPreviewMs = null
                            },
                            onDoubleTapSeek = { total, isBurstStart ->
                                if (isBurstStart) seekBurstBaseMs = state.positionMs
                                fastSeekTotal = total
                                fastSeekToken++
                                val target = (seekBurstBaseMs + total * 1000L).coerceIn(0L, state.durationMs.coerceAtLeast(1L))
                                PlaybackSession.seekTo(target)
                            },
                            onSingleTap = {
                                // Hiding the chrome unmounts the menu's anchor, so drop the open
                                // flag with it — otherwise the menu reappears on the next tap.
                                if (controlsVisible) { controlsVisible = false; menuOpen = false } else interact()
                            },
                        ),
                )

                if (state.isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                }
                if (showBrightnessHud) {
                    GestureHud(brightnessPercent, isBrightness = true, modifier = Modifier.align(Alignment.CenterStart).padding(24.dp))
                }
                if (showVolumeHud) {
                    GestureHud(volumePercent, isBrightness = false, modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp))
                }
                seekPreviewMs?.let {
                    SeekPreviewHud(formatPosition(it, state.durationMs), modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
                }
                fastSeekTotal?.let { total ->
                    val side = if (total >= 0) Alignment.CenterEnd else Alignment.CenterStart
                    FastSeekOverlay(total, modifier = Modifier.align(side).padding(24.dp))
                }

                if (controlsVisible) {
                    // Prev / play / next, centred as one row. With a single video there is
                    // nothing to skip to, so the transport collapses to just the play button.
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (state.queueSize > 1) {
                            IconButton(onClick = { PlaybackSession.skipPrevious(); interact() }, modifier = Modifier.size(48.dp)) {
                                SkipGlyph(forward = false, tint = Color.White)
                            }
                        }
                        CenterPlayButton(onClick = { PlaybackSession.togglePlayPause(); interact() }, playing = state.isPlaying)
                        if (state.queueSize > 1) {
                            IconButton(onClick = { PlaybackSession.skipNext(); interact() }, modifier = Modifier.size(48.dp)) {
                                SkipGlyph(forward = true, tint = Color.White)
                            }
                        }
                    }
                    OverflowControls(
                        state = state,
                        expanded = menuOpen,
                        onExpandedChange = { menuOpen = it; interact() },
                        onDownload = onDownload?.let { cb -> { interact(); cb() } },
                        onQuality = { interact(); showQualitySheet = true },
                        onSpeed = { interact(); showSpeedSheet = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    )
                    ControlBar(
                        state = state,
                        seekThumbs = seekThumbs,
                        fullscreen = fullscreen,
                        onToggleFullscreen = { interact(); onToggleFullscreen() },
                        captionsAvailable = state.availableCaptions.isNotEmpty(),
                        onOpenCaptions = { interact(); showCaptionSheet = true },
                        canPreviewGrid = seekThumbs != null && state.durationMs > 0,
                        onOpenPreviewGrid = { interact(); showJumpGrid = true },
                        onScrubbingChange = { scrubbing = it },
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }

                if (showQualitySheet) {
                    QualitySheet(
                        heights = state.availableHeights,
                        selectedHeight = state.selectedHeight,
                        onSelect = { PlaybackSession.selectQuality(it); showQualitySheet = false },
                        onDismiss = { showQualitySheet = false },
                    )
                }
                if (showSpeedSheet) {
                    SpeedSheet(
                        current = state.speed,
                        onSelect = { PlaybackSession.setSpeed(it); showSpeedSheet = false },
                        onDismiss = { showSpeedSheet = false },
                    )
                }
                if (showCaptionSheet) {
                    CaptionSheet(
                        tracks = state.availableCaptions,
                        selectedLanguage = state.selectedCaptionLanguage,
                        onSelect = { PlaybackSession.selectCaption(it); showCaptionSheet = false },
                        onDismiss = { showCaptionSheet = false },
                    )
                }
                val thumbs = seekThumbs
                if (showJumpGrid && thumbs != null) {
                    JumpGridSheet(
                        seekThumbs = thumbs,
                        durationSeconds = state.durationMs / 1000.0,
                        positionSeconds = state.positionMs / 1000.0,
                        onJump = { seconds -> PlaybackSession.seekTo((seconds * 1000).toLong()); showJumpGrid = false },
                        onDismiss = { showJumpGrid = false },
                    )
                }
            }
        }
    }
}
