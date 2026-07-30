package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueMathTest {

    @Test fun `next repeat off stops at the end`() {
        assertEquals(1, QueueMath.nextIndex(0, 3, RepeatMode.OFF))
        assertNull(QueueMath.nextIndex(2, 3, RepeatMode.OFF))
    }

    @Test fun `next repeat one stays put`() {
        assertEquals(1, QueueMath.nextIndex(1, 3, RepeatMode.ONE))
        assertEquals(2, QueueMath.nextIndex(2, 3, RepeatMode.ONE))
    }

    @Test fun `next repeat all wraps to the start`() {
        assertEquals(0, QueueMath.nextIndex(2, 3, RepeatMode.ALL))
    }

    @Test fun `previous repeat off stops at the start`() {
        assertEquals(0, QueueMath.previousIndex(1, 3, RepeatMode.OFF))
        assertNull(QueueMath.previousIndex(0, 3, RepeatMode.OFF))
    }

    @Test fun `previous repeat one stays put`() {
        assertEquals(1, QueueMath.previousIndex(1, 3, RepeatMode.ONE))
    }

    @Test fun `previous repeat all wraps to the end`() {
        assertEquals(2, QueueMath.previousIndex(0, 3, RepeatMode.ALL))
    }

    @Test fun `empty queue never yields an index`() {
        assertNull(QueueMath.nextIndex(0, 0, RepeatMode.ALL))
        assertNull(QueueMath.previousIndex(0, 0, RepeatMode.ALL))
    }

    @Test fun `next and previous follow the shuffle order, not queue order`() {
        val order = listOf(2, 0, 1) // play-order position -> queue index
        assertEquals(0, QueueMath.nextIndex(2, 3, RepeatMode.OFF, order))
        assertEquals(1, QueueMath.nextIndex(0, 3, RepeatMode.OFF, order))
        assertNull(QueueMath.nextIndex(1, 3, RepeatMode.OFF, order)) // last position, repeat off
        assertEquals(2, QueueMath.nextIndex(1, 3, RepeatMode.ALL, order)) // wraps to order[0]
        assertEquals(0, QueueMath.previousIndex(1, 3, RepeatMode.OFF, order))
        assertNull(QueueMath.previousIndex(2, 3, RepeatMode.OFF, order)) // first position
    }

    @Test fun `resolve window is current plus at most one ahead`() {
        assertEquals(listOf(0, 1), QueueMath.resolveWindow(0, 5, RepeatMode.OFF))
        assertEquals(listOf(4), QueueMath.resolveWindow(4, 5, RepeatMode.OFF)) // no next, repeat off
        assertEquals(listOf(4, 0), QueueMath.resolveWindow(4, 5, RepeatMode.ALL)) // wraps
        assertEquals(listOf(2), QueueMath.resolveWindow(2, 5, RepeatMode.ONE)) // "next" is itself
        assertEquals(emptyList<Int>(), QueueMath.resolveWindow(0, 0, RepeatMode.ALL))
        assertTrue(QueueMath.resolveWindow(0, 100, RepeatMode.OFF).size <= 2) // never the whole list
    }

    @Test fun `clamp keeps an index inside the queue`() {
        assertEquals(0, QueueMath.clamp(-5, 3))
        assertEquals(2, QueueMath.clamp(99, 3))
        assertEquals(1, QueueMath.clamp(1, 3))
        assertEquals(0, QueueMath.clamp(0, 0))
    }

    @Test fun `shuffle order is deterministic for a given seed`() {
        val a = QueueMath.shuffleOrder(20, seed = 42L)
        val b = QueueMath.shuffleOrder(20, seed = 42L)
        assertEquals(a, b)
        assertEquals((0 until 20).toSet(), a.toSet()) // a permutation, nothing dropped or duplicated
    }

    @Test fun `shuffle order of size zero is empty`() {
        assertEquals(emptyList<Int>(), QueueMath.shuffleOrder(0, seed = 1L))
    }
}
