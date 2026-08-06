package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptionSelectionTest {

    @Test fun `same item keeps the pick`() {
        assertEquals("en", carryOverCaptionSelection("en", isSameItem = true))
    }

    @Test fun `a different item resets to Off`() {
        assertNull(carryOverCaptionSelection("en", isSameItem = false))
    }

    @Test fun `Off stays Off either way`() {
        assertNull(carryOverCaptionSelection(null, isSameItem = true))
        assertNull(carryOverCaptionSelection(null, isSameItem = false))
    }
}
