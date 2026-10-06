package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** The queue-index remaps PlaybackSession applies to its prefetch bookkeeping (`index`, `window`)
 *  whenever a queue edit shifts positions. */
class QueueIndexShiftTest {

    @Test fun `removing an entry before the index shifts it down`() {
        assertEquals(2, QueueMath.indexAfterRemove(index = 3, removed = 1))
    }

    @Test fun `removing an entry after the index leaves it alone`() {
        assertEquals(3, QueueMath.indexAfterRemove(index = 3, removed = 5))
    }

    @Test fun `removing the entry just before the index shifts it down`() {
        assertEquals(0, QueueMath.indexAfterRemove(index = 1, removed = 0))
    }

    @Test fun `moving the indexed entry itself follows it to its target`() {
        assertEquals(0, QueueMath.indexAfterMove(index = 3, from = 3, to = 0))
    }

    @Test fun `moving an entry from before to after the index shifts it down`() {
        assertEquals(2, QueueMath.indexAfterMove(index = 3, from = 0, to = 4))
    }

    @Test fun `moving an entry from after to before the index shifts it up`() {
        assertEquals(4, QueueMath.indexAfterMove(index = 3, from = 5, to = 1))
    }

    @Test fun `moving an entry entirely before the index leaves it alone`() {
        assertEquals(3, QueueMath.indexAfterMove(index = 3, from = 0, to = 2))
    }

    @Test fun `move remap agrees with the shuffle order remap for every index`() {
        val queue = listOf("a", "b", "c", "d", "e")
        val moved = queue.toMutableList().apply { add(1, removeAt(4)) }
        for (oldIndex in queue.indices) {
            val newIndex = QueueMath.indexAfterMove(oldIndex, from = 4, to = 1)
            assertEquals(queue[oldIndex], moved[newIndex])
        }
    }

    @Test fun `remove remap keeps pointing at the same item`() {
        val queue = listOf("a", "b", "c", "d")
        val remaining = queue.toMutableList().apply { removeAt(1) }
        for (oldIndex in listOf(0, 2, 3)) {
            val newIndex = QueueMath.indexAfterRemove(oldIndex, removed = 1)
            assertEquals(queue[oldIndex], remaining[newIndex])
        }
    }
}
