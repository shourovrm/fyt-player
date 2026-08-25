package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueMathTest {

    @Test fun `play next moves a queued entry right after current`() {
        // queue [a b c d], playing b (1): d (3) -> slot 2; a (0) -> slot 1 (b shifts down to 0)
        val q = listOf("a", "b", "c", "d")
        fun moved(from: Int, to: Int) = q.toMutableList().apply { add(to, removeAt(from)) }
        assertEquals(listOf("a", "b", "d", "c"), moved(3, QueueMath.playNextTarget(3, 1)))
        assertEquals(listOf("b", "a", "c", "d"), moved(0, QueueMath.playNextTarget(0, 1)))
    }

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

    // Decision logic PlaybackSession.enqueue() leans on (see its doc): a queue that had run out
    // (nextIndex null, repeat off, at the tail) gets a real next index again the moment it grows.
    // enqueue() re-derives via this exact call after appending, instead of trusting a window built
    // against the old, shorter queue -- that stale-window trust was the actual "Queue does nothing" bug.
    @Test fun `an exhausted queue has a next index again once it grows`() {
        assertNull(QueueMath.nextIndex(1, 2, RepeatMode.OFF))
        assertEquals(2, QueueMath.nextIndex(1, 3, RepeatMode.OFF))
    }

    // PlaybackSession.clearQueue() trims the queue to just [current] and still calls prefetchNext()
    // (every mutator does). This is why that call is a genuine no-op: on a 1-item queue there is
    // nothing to prefetch, or it wraps to the same index, and either way prefetchNext's own
    // `if (n == index) return` guard swallows it.
    @Test fun `a single-item queue never yields a real next index to prefetch`() {
        assertNull(QueueMath.nextIndex(0, 1, RepeatMode.OFF))
        assertEquals(0, QueueMath.nextIndex(0, 1, RepeatMode.ONE))
        assertEquals(0, QueueMath.nextIndex(0, 1, RepeatMode.ALL)) // wraps to itself
    }

    // queue [A, B, C, D], shuffle order plays C(2), A(0), D(3), B(1); current is D at index 3
    @Test fun `removeAt remaps shuffle order and preserves the current item`() {
        val order = listOf(2, 0, 3, 1)
        val current = 3
        val remapped = QueueMath.removeOrder(order, 0) // remove A
        assertEquals(listOf(1, 2, 0), remapped)
        val newCurrent = current - 1 // D shifts from 3 to 2
        assertEquals(0, QueueMath.nextIndex(newCurrent, 3, RepeatMode.OFF, remapped))
        assertEquals(1, QueueMath.previousIndex(newCurrent, 3, RepeatMode.OFF, remapped))
    }

    // queue [A, B, C, D], current B at index 1, shuffle order C(2), A(0), D(3), B(1)
    @Test fun `playNext inserts ahead in shuffle order and preserves the current item`() {
        val order = listOf(2, 0, 3, 1)
        val current = 1
        val insertAt = current + 1
        val playPos = order.indexOf(current)
        val remapped = QueueMath.insertOrder(order, insertAt, playPos + 1)
        assertEquals(listOf(3, 0, 4, 1, 2), remapped)
        assertEquals(2, QueueMath.nextIndex(current, 5, RepeatMode.OFF, remapped))
        assertEquals(4, QueueMath.previousIndex(current, 5, RepeatMode.OFF, remapped))
    }

    @Test fun `enqueue appends to shuffle order and keeps next and previous in bounds`() {
        val order = listOf(2, 0, 3, 1)
        val current = 1
        val remapped = QueueMath.appendOrder(order, 4)
        assertEquals(listOf(2, 0, 3, 1, 4), remapped)
        assertEquals(4, QueueMath.nextIndex(current, 5, RepeatMode.OFF, remapped))
        assertEquals(3, QueueMath.previousIndex(current, 5, RepeatMode.OFF, remapped))
    }

    // queue [A, B, C, D], current D at 3, shuffle order C(2), A(0), D(3), B(1); move C to the front
    @Test fun `move remaps shuffle order and current index consistently`() {
        val order = listOf(2, 0, 3, 1)
        val from = 2
        val to = 0
        val remapped = QueueMath.moveOrder(order, from, to)
        assertEquals(listOf(0, 1, 3, 2), remapped)
        val newCurrent = 3 // D stays at index 3
        assertEquals(2, QueueMath.nextIndex(newCurrent, 4, RepeatMode.OFF, remapped))
        assertEquals(1, QueueMath.previousIndex(newCurrent, 4, RepeatMode.OFF, remapped))
    }
}
