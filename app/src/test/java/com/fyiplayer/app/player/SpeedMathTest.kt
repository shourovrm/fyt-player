package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedMathTest {

    @Test fun `roundSpeed clamps to 0_1 and 3`() {
        assertEquals(0.1f, roundSpeed(0.01f), 0f)
        assertEquals(3f, roundSpeed(5f), 0f)
    }

    @Test fun `roundSpeed rounds to the nearest 0_05`() {
        assertEquals(1.35f, roundSpeed(1.3612f), 0f)
        assertEquals(0.35f, roundSpeed(0.34f), 0f)
    }

    @Test fun `twenty nudges up from 0_1 land on 1_1 without drift`() {
        var speed = 0.1f
        repeat(20) { speed = roundSpeed(speed + SPEED_STEP) }
        assertEquals(1.1f, speed, 0f)
    }

    @Test fun `snapSpeed settles within one step of a major mark`() {
        assertEquals(1f, snapSpeed(0.95f), 0f)
        assertEquals(1f, snapSpeed(1.05f), 0f)
        assertEquals(0.25f, snapSpeed(0.3f), 0f)
        assertEquals(3f, snapSpeed(2.95f), 0f)
    }

    @Test fun `snapSpeed leaves values between marks alone`() {
        assertEquals(1.1f, snapSpeed(1.1f), 0f)
        assertEquals(0.15f, snapSpeed(0.15f), 0f)
        assertEquals(0.1f, snapSpeed(0.1f), 0f)
    }

    @Test fun `formatSpeed drops trailing zeros`() {
        assertEquals("1x", formatSpeed(1f))
        assertEquals("1.5x", formatSpeed(1.5f))
        assertEquals("0.35x", formatSpeed(0.35f))
        assertEquals("0.1x", formatSpeed(0.1f))
        assertEquals("1.05x", formatSpeed(1.05f))
        assertEquals("3x", formatSpeed(3f))
    }
}
