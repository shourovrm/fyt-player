package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePillPlacementTest {

    @Test fun `dot runs from half a thumb inside one end of the row to half a thumb inside the other`() {
        assertEquals(12f, resumeDotCenterX(fraction = 0f, rowWidthPx = 400f, sideInsetPx = 0f, thumbHalfPx = 12f))
        assertEquals(200f, resumeDotCenterX(fraction = 0.5f, rowWidthPx = 400f, sideInsetPx = 0f, thumbHalfPx = 12f))
        assertEquals(388f, resumeDotCenterX(fraction = 1f, rowWidthPx = 400f, sideInsetPx = 0f, thumbHalfPx = 12f))
        assertEquals(36f, resumeDotCenterX(fraction = 0f, rowWidthPx = 400f, sideInsetPx = 24f, thumbHalfPx = 12f))
    }

    @Test fun `pill is centred on a dot in the middle of the row`() {
        assertEquals(150, resumePillLeft(dotCenterX = 200f, pillWidthPx = 100, rowWidthPx = 400, marginPx = 16))
    }

    @Test fun `pill stops at the left margin for a dot near the start`() {
        assertEquals(16, resumePillLeft(dotCenterX = 12f, pillWidthPx = 100, rowWidthPx = 400, marginPx = 16))
    }

    @Test fun `pill stops at the right margin for a dot near the end`() {
        assertEquals(284, resumePillLeft(dotCenterX = 388f, pillWidthPx = 100, rowWidthPx = 400, marginPx = 16))
    }

    @Test fun `pill wider than the row starts at the left margin`() {
        assertEquals(16, resumePillLeft(dotCenterX = 200f, pillWidthPx = 500, rowWidthPx = 400, marginPx = 16))
    }
}
