package com.fyiplayer.app.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private enum class HalfSide { LEFT, RIGHT }
private enum class ThirdSide { LEFT, MIDDLE, RIGHT }
internal enum class DragMode { UNDECIDED, VERTICAL, HORIZONTAL }

private fun half(x: Float, width: Int) = if (x < width / 2f) HalfSide.LEFT else HalfSide.RIGHT
private fun third(x: Float, width: Int) = when {
    x < width / 3f -> ThirdSide.LEFT
    x > width * 2f / 3f -> ThirdSide.RIGHT
    else -> ThirdSide.MIDDLE
}

// dp; converted to px at the gesture site via PointerInputScope's own Density.
private const val EDGE_DEAD_ZONE_DP = 24
private const val BOTTOM_DEAD_ZONE_DP = 32

// px of total drag distance before a direction is locked -- keeps a shaky near-vertical tap from
// firing a one-frame seek, and vice versa.
private const val DRAG_SLOP_PX = 24f

// A full-viewport-height drag sweeps 150% of the 0-100 range: fast enough to reach either end
// without a second swipe, slow enough that small moves aren't all-or-nothing.
private const val VERTICAL_SWEEP_PERCENT = 150f

/** True when a drag STARTING at ([xPx],[yPx]) begins inside the reserved strip along either
 *  side edge or the bottom edge -- pure so it's unit-testable without a real Density/pointer
 *  event. Callers must skip handling entirely for such a drag (no consume) so it falls through
 *  to the system's own back/home gesture recognizer. */
internal fun isEdgeDeadZone(xPx: Float, yPx: Float, widthPx: Int, heightPx: Int, edgePx: Float, bottomPx: Float): Boolean =
    xPx < edgePx || xPx > widthPx - edgePx || yPx > heightPx - bottomPx

/** Locks a drag to vertical or horizontal once the cumulative distance from the drag's start
 *  exceeds [slopPx]; stays [DragMode.UNDECIDED] (no effect applied yet) below that threshold. */
internal fun lockDragMode(accDx: Float, accDy: Float, slopPx: Float): DragMode {
    if (kotlin.math.hypot(accDx, accDy) < slopPx) return DragMode.UNDECIDED
    return if (abs(accDy) > abs(accDx)) DragMode.VERTICAL else DragMode.HORIZONTAL
}

/**
 * Player gesture surface: a vertical drag on the left half adjusts brightness, the right half
 * volume; a horizontal drag scrubs; a double tap on either outer third accumulates a seek via
 * [accumulator]. Seek reports a raw per-frame pixel delta — the caller owns scale and clamping;
 * brightness/volume report a pre-scaled percent delta (see below) since only the caller knows the
 * 0-100 range they're being applied to.
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
 * A drag starting inside [isEdgeDeadZone] (24dp off either side, 32dp off the bottom) is never
 * consumed at all, so Android's own back/home edge gestures still win there instead of fighting
 * this detector. Everything else waits for [lockDragMode] to clear [DRAG_SLOP_PX] of cumulative
 * movement before committing to vertical or horizontal -- a single frame's `dy > dx` used to lock
 * the wrong axis off a shaky first sample, and any horizontal drag (including a plain rightward
 * swipe) used to always mean seek.
 *
 * onBrightnessDrag/onVolumeDrag receive a *percent* delta already scaled by [VERTICAL_SWEEP_PERCENT]
 * over the gesture's own viewport height, not a raw pixel delta -- callers accumulate it as a
 * float and apply it as an Int so small moves aren't lost to truncation.
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
    onBrightnessDrag: (deltaPercent: Float) -> Unit,
    onVolumeDrag: (deltaPercent: Float) -> Unit,
    onSeekDrag: (deltaPx: Float) -> Unit,
    onSeekDragEnd: () -> Unit,
    onDoubleTapSeek: (totalSeconds: Int, isBurstStart: Boolean) -> Unit,
    onSingleTap: () -> Unit,
): Modifier = this
    .pointerInput(fullscreen, gestureBrightness, gestureVolume) {
        if (!fullscreen) return@pointerInput
        var mode = DragMode.UNDECIDED
        var accDx = 0f
        var accDy = 0f
        var startSide = HalfSide.LEFT
        var inDeadZone = false
        val edgePx = EDGE_DEAD_ZONE_DP.dp.toPx()
        val bottomPx = BOTTOM_DEAD_ZONE_DP.dp.toPx()
        detectDragGestures(
            onDragStart = { offset ->
                mode = DragMode.UNDECIDED
                accDx = 0f
                accDy = 0f
                startSide = half(offset.x, size.width)
                inDeadZone = isEdgeDeadZone(offset.x, offset.y, size.width, size.height, edgePx, bottomPx)
            },
            onDragEnd = { onSeekDragEnd() },
            onDragCancel = { onSeekDragEnd() },
        ) { change, dragAmount ->
            if (inDeadZone) return@detectDragGestures
            accDx += dragAmount.x
            accDy += dragAmount.y
            if (mode == DragMode.UNDECIDED) {
                mode = lockDragMode(accDx, accDy, DRAG_SLOP_PX)
                if (mode == DragMode.UNDECIDED) return@detectDragGestures
            }
            change.consume()
            when (mode) {
                DragMode.VERTICAL -> {
                    val deltaPercent = -dragAmount.y * VERTICAL_SWEEP_PERCENT / size.height
                    when (startSide) {
                        HalfSide.LEFT -> if (gestureBrightness) onBrightnessDrag(deltaPercent)
                        HalfSide.RIGHT -> if (gestureVolume) onVolumeDrag(deltaPercent)
                    }
                }
                DragMode.HORIZONTAL -> onSeekDrag(dragAmount.x)
                DragMode.UNDECIDED -> Unit
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
