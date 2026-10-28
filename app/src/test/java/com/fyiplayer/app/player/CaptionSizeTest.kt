package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionSizeTest {

    @Test fun `medium is the default and keeps today's caption size`() {
        assertEquals(CaptionSize.MEDIUM, CaptionSize.DEFAULT)
        assertEquals(1.0f, CaptionSize.DEFAULT.scale, 0f)
    }

    @Test fun `the four steps scale as specified`() {
        assertEquals(listOf(0.8f, 1.0f, 1.3f, 1.7f), CaptionSize.entries.map { it.scale })
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
