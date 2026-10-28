package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekPreviewLabelLeftTest {

    private val containerWidthPx = 400
    private val marginPx = 16
    private val cardWidthPx = 160

    @Test fun `a label that fits is centred on the card`() {
        // Card spans 100..260, centre 180; a 80px label centred there starts at 140.
        assertEquals(
            140,
            seekPreviewLabelLeftPx(100, cardWidthPx, 80, containerWidthPx, marginPx),
        )
    }

    @Test fun `a wide label near the left edge is pushed in to the margin`() {
        // Centred would start at -20; the margin keeps it at 16.
        assertEquals(
            16,
            seekPreviewLabelLeftPx(0, cardWidthPx, 200, containerWidthPx, marginPx),
        )
    }

    @Test fun `a wide label near the right edge is pulled back to the right margin`() {
        // Centred would start at 280 and end at 480; the right margin puts the end at 384.
        assertEquals(
            184,
            seekPreviewLabelLeftPx(300, cardWidthPx, 200, containerWidthPx, marginPx),
        )
    }

    @Test fun `a label exactly as wide as the container minus both margins fills it`() {
        assertEquals(
            16,
            seekPreviewLabelLeftPx(0, cardWidthPx, 368, containerWidthPx, marginPx),
        )
    }

    @Test fun `a label wider than the container minus both margins pins to the left margin`() {
        assertEquals(
            16,
            seekPreviewLabelLeftPx(120, cardWidthPx, 380, containerWidthPx, marginPx),
        )
    }
}
