package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastSeekAccumulatorTest {

    private class FakeClock(var now: Long = 0L) {
        fun advance(millis: Long) { now += millis }
    }

    private fun accumulator(step: Int = 10, windowMillis: Long = 800, clock: FakeClock) =
        FastSeekAccumulator(stepSeconds = step, windowMillis = windowMillis, clock = { clock.now })

    @Test fun `first tap starts a burst at one step`() {
        val acc = accumulator(clock = FakeClock())
        val result = acc.tap(FastSeekAccumulator.Direction.FORWARD)
        assertEquals(10, result.signedSeconds)
        assertTrue(result.isBurstStart)
    }

    @Test fun `taps within the window on the same side accumulate`() {
        val clock = FakeClock()
        val acc = accumulator(clock = clock)
        acc.tap(FastSeekAccumulator.Direction.FORWARD)
        clock.advance(200)
        val second = acc.tap(FastSeekAccumulator.Direction.FORWARD)
        clock.advance(200)
        val third = acc.tap(FastSeekAccumulator.Direction.FORWARD)
        assertEquals(20, second.signedSeconds)
        assertFalse(second.isBurstStart)
        assertEquals(30, third.signedSeconds)
        assertFalse(third.isBurstStart)
    }

    @Test fun `backward taps accumulate as negative seconds`() {
        val clock = FakeClock()
        val acc = accumulator(clock = clock)
        acc.tap(FastSeekAccumulator.Direction.BACKWARD)
        clock.advance(100)
        val second = acc.tap(FastSeekAccumulator.Direction.BACKWARD)
        assertEquals(-20, second.signedSeconds)
    }

    @Test fun `a tap after the window closes starts a fresh burst`() {
        val clock = FakeClock()
        val acc = accumulator(windowMillis = 800, clock = clock)
        acc.tap(FastSeekAccumulator.Direction.FORWARD)
        clock.advance(801)
        val result = acc.tap(FastSeekAccumulator.Direction.FORWARD)
        assertEquals(10, result.signedSeconds)
        assertTrue(result.isBurstStart)
    }

    @Test fun `switching direction starts a fresh burst even within the window`() {
        val clock = FakeClock()
        val acc = accumulator(clock = clock)
        acc.tap(FastSeekAccumulator.Direction.FORWARD)
        clock.advance(100)
        val result = acc.tap(FastSeekAccumulator.Direction.BACKWARD)
        assertEquals(-10, result.signedSeconds)
        assertTrue(result.isBurstStart)
    }

    @Test fun `isActive true within the window, false after it expires`() {
        val clock = FakeClock()
        val acc = accumulator(windowMillis = 800, clock = clock)
        assertFalse(acc.isActive()) // never tapped
        acc.tap(FastSeekAccumulator.Direction.FORWARD)
        assertTrue(acc.isActive())
        clock.advance(801)
        assertFalse(acc.isActive())
    }

    @Test fun `reset clears direction and total`() {
        val clock = FakeClock()
        val acc = accumulator(clock = clock)
        acc.tap(FastSeekAccumulator.Direction.FORWARD)
        acc.reset()
        assertFalse(acc.isActive())
        assertEquals(0, acc.totalSeconds)
        assertEquals(null, acc.direction)
        // next tap after reset starts fresh even though the clock never moved
        val result = acc.tap(FastSeekAccumulator.Direction.FORWARD)
        assertEquals(10, result.signedSeconds)
        assertTrue(result.isBurstStart)
    }
}
