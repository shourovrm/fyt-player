package com.fyiplayer.app.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

private enum class HalfSide { LEFT, RIGHT }
private enum class ThirdSide { LEFT, MIDDLE, RIGHT }

private fun half(x: Float, width: Int) = if (x < width / 2f) HalfSide.LEFT else HalfSide.RIGHT
private fun third(x: Float, width: Int) = when {
    x < width / 3f -> ThirdSide.LEFT
    x > width * 2f / 3f -> ThirdSide.RIGHT
    else -> ThirdSide.MIDDLE
}

/**
 * Player gesture surface: a vertical drag on the left half adjusts brightness, the right half
 * volume; a horizontal drag scrubs; a double tap on either outer third accumulates a seek via
 * [accumulator]. Drags report raw per-frame pixel deltas — the caller owns scale and clamping.
 *
 * [gestureBrightness]/[gestureVolume] gate only those two halves of the drag: they're the only
 * gesture toggles `data/prefs/Prefs.kt` exposes today. Seek-drag and double-tap have no such
 * toggle and stay always on.
 *
 * [fullscreen] gates the whole drag detector: embedded, the player shares its bounds with a
 * scrollable detail page, and `detectDragGestures`'s `change.consume()` would otherwise swallow
 * every drag that starts over the video, leaving the page unscrollable there. Not attaching the
 * detector at all (rather than attaching-but-ignoring) is what lets the ancestor list's own scroll
 * gesture claim those drags normally in the embedded case. Tap gestures don't have this conflict
 * and stay active either way.
 *
 * No timers anywhere here: touch slop already separates a drag from a tap for free, and a vertical
 * drag still lets the ancestor list scroll when this detector isn't attached — a timed press would
 * only race a cold media load and lose.
 *
 * ponytail: drag and double-tap live in separate `pointerInput` blocks rather than one shared
 * `awaitPointerEventScope` state machine — the simplest thing that reads correctly for a single
 * active pointer. Upgrade only if multi-touch or drag/tap conflicts turn up.
 */
fun Modifier.playerGestures(
    fullscreen: Boolean,
    gestureBrightness: Boolean,
    gestureVolume: Boolean,
    accumulator: FastSeekAccumulator,
    onBrightnessDrag: (Float) -> Unit,
    onVolumeDrag: (Float) -> Unit,
    onSeekDrag: (deltaPx: Float) -> Unit,
    onSeekDragEnd: () -> Unit,
    onDoubleTapSeek: (totalSeconds: Int, isBurstStart: Boolean) -> Unit,
    onSingleTap: () -> Unit,
): Modifier = this
    .pointerInput(fullscreen, gestureBrightness, gestureVolume) {
        if (!fullscreen) return@pointerInput
        var verticalMode = false
        var decided = false
        var startSide = HalfSide.LEFT
        detectDragGestures(
            onDragStart = { offset ->
                decided = false
                startSide = half(offset.x, size.width)
            },
            onDragEnd = { onSeekDragEnd() },
            onDragCancel = { onSeekDragEnd() },
        ) { change, dragAmount ->
            change.consume()
            if (!decided) {
                verticalMode = abs(dragAmount.y) > abs(dragAmount.x)
                decided = true
            }
            if (verticalMode) {
                when (startSide) {
                    HalfSide.LEFT -> if (gestureBrightness) onBrightnessDrag(-dragAmount.y)
                    HalfSide.RIGHT -> if (gestureVolume) onVolumeDrag(-dragAmount.y)
                }
            } else {
                onSeekDrag(dragAmount.x)
            }
        }
    }
    // Single tap toggles the chrome only — never pause; the centred play button and the control
    // bar's own button are the sole tap-to-pause affordances. Shares one detectTapGestures with
    // the double-tap seek so the framework, not us, disambiguates a single tap from a double-tap.
    .pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = { offset ->
                val dir = when (third(offset.x, size.width)) {
                    ThirdSide.LEFT -> FastSeekAccumulator.Direction.BACKWARD
                    ThirdSide.RIGHT -> FastSeekAccumulator.Direction.FORWARD
                    ThirdSide.MIDDLE -> null
                } ?: return@detectTapGestures
                val result = accumulator.tap(dir)
                onDoubleTapSeek(result.signedSeconds, result.isBurstStart)
            },
            onTap = { onSingleTap() },
        )
    }
