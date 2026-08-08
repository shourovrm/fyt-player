package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTimeFormatTest {

    @Test fun `mmss under an hour has no hours field`() {
        assertEquals("1:12", mmss(72_000))
    }

    @Test fun `mmss at or over an hour gets an hours field`() {
        assertEquals("1:41:12", mmss((101 * 60 + 12) * 1000L))
    }

    @Test fun `mmss forceHours pads a sub-hour value to match`() {
        assertEquals("0:05:12", mmss(312_000, forceHours = true))
    }

    @Test fun `formatPosition stays m ss when duration is under an hour`() {
        assertEquals("1:12 / 3:00", formatPosition(72_000, 180_000))
    }

    @Test fun `formatPosition widens position to h mm ss once duration crosses an hour`() {
        val durationMs = (101 * 60 + 12) * 1000L
        val positionMs = 312_000L // 5:12
        assertEquals("0:05:12 / 1:41:12", formatPosition(positionMs, durationMs))
    }
}
