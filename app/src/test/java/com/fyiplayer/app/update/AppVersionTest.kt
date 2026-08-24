package com.fyiplayer.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test fun newerWins() {
        assertTrue(isNewer("0.3.0", "0.2.12"))
        assertTrue(isNewer("0.2.13", "0.2.12"))
        assertTrue(isNewer("1.10.0", "1.9.9")) // numeric, not lexicographic
        assertTrue(isNewer("0.3", "0.2.12")) // shorter side zero-padded
    }

    @Test fun equalAndOlderLose() {
        assertFalse(isNewer("0.2.12", "0.2.12"))
        assertFalse(isNewer("0.2", "0.2.0"))
        assertFalse(isNewer("0.2.11", "0.2.12"))
    }

    @Test fun garbageNeverBanners() {
        assertFalse(isNewer("abc", "0.2.12"))
        assertFalse(isNewer("", "0.2.12"))
        assertFalse(isNewer("0.3.0-beta", "0.2.12"))
        assertFalse(isNewer("0.3.0", ""))
    }
}
