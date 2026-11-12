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

    @Test fun `custom minutes accept 1 to 720`() {
        assertEquals(1, parseSleepTimerMinutes("1"))
        assertEquals(90, parseSleepTimerMinutes(" 90 "))
        assertEquals(720, parseSleepTimerMinutes("720"))
    }

    @Test fun `custom minutes reject empty, zero and too long`() {
        assertNull(parseSleepTimerMinutes(""))
        assertNull(parseSleepTimerMinutes("0"))
        assertNull(parseSleepTimerMinutes("721"))
        assertNull(parseSleepTimerMinutes("99999999999"))
    }
}
