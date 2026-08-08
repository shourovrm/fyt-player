package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGestureZoneTest {

    private val edgePx = 24f
    private val bottomPx = 32f
    private val width = 1000
    private val height = 2000

    @Test fun `center of the screen is never a dead zone`() {
        assertEquals(false, isEdgeDeadZone(500f, 500f, width, height, edgePx, bottomPx))
    }

    @Test fun `start within the left edge strip is a dead zone`() {
        assertEquals(true, isEdgeDeadZone(10f, 500f, width, height, edgePx, bottomPx))
    }

    @Test fun `start within the right edge strip is a dead zone`() {
        assertEquals(true, isEdgeDeadZone(width - 10f, 500f, width, height, edgePx, bottomPx))
    }

    @Test fun `start within the bottom strip is a dead zone`() {
        assertEquals(true, isEdgeDeadZone(500f, height - 10f, width, height, edgePx, bottomPx))
    }

    @Test fun `top edge has no exclusion`() {
        assertEquals(false, isEdgeDeadZone(500f, 1f, width, height, edgePx, bottomPx))
    }

    @Test fun `mode stays undecided below the slop threshold`() {
        assertEquals(DragMode.UNDECIDED, lockDragMode(accDx = 5f, accDy = 5f, slopPx = 24f))
    }

    @Test fun `mode locks vertical once slop clears and dy dominates`() {
        assertEquals(DragMode.VERTICAL, lockDragMode(accDx = 2f, accDy = 30f, slopPx = 24f))
    }

    @Test fun `mode locks horizontal once slop clears and dx dominates`() {
        assertEquals(DragMode.HORIZONTAL, lockDragMode(accDx = 30f, accDy = 2f, slopPx = 24f))
    }

    @Test fun `a rightward drag well past slop locks horizontal, not a vertical mis-lock`() {
        // Regression: the old single-frame abs(dy) greater than abs(dx) check could lock the
        // wrong axis off one shaky sample; the cumulative check must not do that for a clean
        // rightward swipe (the reported "swipe right restarts the video").
        assertEquals(DragMode.HORIZONTAL, lockDragMode(accDx = 50f, accDy = 1f, slopPx = 24f))
    }
}
