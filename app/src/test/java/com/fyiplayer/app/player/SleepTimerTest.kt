package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTimerTest {

    @Test fun `minutes left round up so a running timer never shows 0`() {
        assertEquals(1L, sleepTimerMinutesLeft(1L))
        assertEquals(1L, sleepTimerMinutesLeft(60_000L))
        assertEquals(2L, sleepTimerMinutesLeft(60_001L))
        assertEquals(15L, sleepTimerMinutesLeft(15 * 60_000L))
    }

    @Test fun `remaining time reads m ss under an hour`() {
        assertEquals("12:34", formatSleepTimerRemaining((12 * 60 + 34) * 1_000L))
        assertEquals("0:01", formatSleepTimerRemaining(1L))
        assertEquals("59:59", formatSleepTimerRemaining(3_599_000L))
    }

    @Test fun `remaining time reads h mm ss from an hour up`() {
        assertEquals("1:00:00", formatSleepTimerRemaining(3_600_000L))
        assertEquals("2:05:09", formatSleepTimerRemaining((2 * 3_600 + 5 * 60 + 9) * 1_000L))
    }

    @Test fun `custom timer accepts 1 minute to 12 hours`() {
        assertEquals(5, sleepTimerTotalMinutes(0, 5))
        assertEquals(90, sleepTimerTotalMinutes(1, 30))
        assertEquals(720, sleepTimerTotalMinutes(12, 0))
    }

    @Test fun `custom timer rejects zero and past 12 hours`() {
        assertNull(sleepTimerTotalMinutes(0, 0))
        assertNull(sleepTimerTotalMinutes(12, 5))
    }
}
