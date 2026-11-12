package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionSizeTest {

    @Test fun `medium is the default`() {
        assertEquals(CaptionSize.MEDIUM, CaptionSize.DEFAULT)
    }

    @Test fun `the four steps are 8, 10, 14 and 16 points`() {
        assertEquals(listOf(8f, 10f, 14f, 16f), CaptionSize.entries.map { it.points })
    }

    @Test fun `every stored name reads back as its own step`() {
        CaptionSize.entries.forEach { size ->
            assertEquals(size, CaptionSize.fromName(size.name))
        }
    }

    @Test fun `a missing name falls back to the default`() {
        assertEquals(CaptionSize.DEFAULT, CaptionSize.fromName(null))
    }

    @Test fun `an unknown name falls back to the default`() {
        assertEquals(CaptionSize.DEFAULT, CaptionSize.fromName("HUGE"))
    }

    @Test fun `name lookup is case sensitive`() {
        assertEquals(CaptionSize.DEFAULT, CaptionSize.fromName("large"))
    }
}
