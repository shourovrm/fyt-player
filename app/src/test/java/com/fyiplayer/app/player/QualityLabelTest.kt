package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityLabelTest {

    @Test fun `a chosen height shows as its height`() {
        assertEquals("720p", qualityLabel(selectedHeight = 720, isAuto = false, autoText = "Auto"))
    }

    @Test fun `every ladder rung keeps its own digits`() {
        listOf(144, 360, 1080, 1440, 2160).forEach { height ->
            assertEquals("${height}p", qualityLabel(selectedHeight = height, isAuto = false, autoText = "Auto"))
        }
    }

    @Test fun `Auto wins over the height the ceiling picked`() {
        assertEquals("Auto", qualityLabel(selectedHeight = 1080, isAuto = true, autoText = "Auto"))
    }

    @Test fun `unknown height shows the Auto text`() {
        assertEquals("Auto", qualityLabel(selectedHeight = null, isAuto = false, autoText = "Auto"))
    }
}
