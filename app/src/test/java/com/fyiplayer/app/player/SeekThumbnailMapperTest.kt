package com.fyiplayer.app.player

import com.fyiplayer.app.core.SeekThumbnails
import com.fyiplayer.app.core.SpriteSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekThumbnailMapperTest {

    @Test fun `flat frame list maps time to the right index`() {
        val thumbs = SeekThumbnails(intervalSeconds = 10.0, frames = listOf("f0", "f1", "f2", "f3"))
        assertEquals(SeekPreviewImage.Frame("f0"), thumbs.imageFor(0.0))
        assertEquals(SeekPreviewImage.Frame("f1"), thumbs.imageFor(15.0))
        assertEquals(SeekPreviewImage.Frame("f2"), thumbs.imageFor(29.9))
    }

    @Test fun `flat frame list clamps past the end to the last frame`() {
        val thumbs = SeekThumbnails(intervalSeconds = 10.0, frames = listOf("f0", "f1"))
        assertEquals(SeekPreviewImage.Frame("f1"), thumbs.imageFor(999.0))
    }

    @Test fun `negative time clamps to index zero`() {
        val thumbs = SeekThumbnails(intervalSeconds = 10.0, frames = listOf("f0", "f1"))
        assertEquals(SeekPreviewImage.Frame("f0"), thumbs.imageFor(-5.0))
    }

    private fun sheet(url: String, count: Int, cols: Int = 5) =
        SpriteSheet(url = url, cols = cols, rows = (count + cols - 1) / cols, tileWidth = 100, tileHeight = 60, count = count)

    @Test fun `sprite sheet maps time to row-major tile within one sheet`() {
        val thumbs = SeekThumbnails(intervalSeconds = 1.0, sprites = listOf(sheet("sheet0", count = 10, cols = 5)))
        val tile = thumbs.imageFor(7.0) as SeekPreviewImage.Tile
        assertEquals("sheet0", tile.sheet.url)
        assertEquals(2, tile.col) // index 7 % 5
        assertEquals(1, tile.row) // index 7 / 5
    }

    @Test fun `sprite index crossing a sheet boundary lands in the next sheet`() {
        val sheet0 = sheet("sheet0", count = 10, cols = 5) // indices 0..9
        val sheet1 = sheet("sheet1", count = 10, cols = 5) // indices 10..19
        val thumbs = SeekThumbnails(intervalSeconds = 1.0, sprites = listOf(sheet0, sheet1))
        val tile = thumbs.imageFor(12.0) as SeekPreviewImage.Tile
        assertEquals("sheet1", tile.sheet.url)
        assertEquals(2, tile.col) // (12 - 10) % 5
        assertEquals(0, tile.row) // (12 - 10) / 5
    }

    @Test fun `sprite time past every sheet clamps to the last tile of the last sheet`() {
        val sheet0 = sheet("sheet0", count = 4, cols = 5)
        val thumbs = SeekThumbnails(intervalSeconds = 1.0, sprites = listOf(sheet0))
        val tile = thumbs.imageFor(999.0) as SeekPreviewImage.Tile
        assertEquals("sheet0", tile.sheet.url)
        assertEquals(3, tile.col) // last valid index (count - 1) = 3
        assertEquals(0, tile.row)
    }

    @Test fun `non-positive interval yields no image`() {
        val thumbs = SeekThumbnails(intervalSeconds = 0.0, frames = listOf("f0"))
        assertNull(thumbs.imageFor(5.0))
    }

    @Test fun `neither frames nor sprites yields no image`() {
        val thumbs = SeekThumbnails(intervalSeconds = 1.0)
        assertNull(thumbs.imageFor(5.0))
    }

    @Test fun `previewImages flattens sprite sheets row-major in sheet order`() {
        val sheet0 = sheet("sheet0", count = 3, cols = 2)
        val thumbs = SeekThumbnails(intervalSeconds = 1.0, sprites = listOf(sheet0))
        val images = thumbs.previewImages()
        assertEquals(3, images.size)
        val tile2 = images[2] as SeekPreviewImage.Tile
        assertEquals(0, tile2.col) // index 2 % 2
        assertEquals(1, tile2.row) // index 2 / 2
    }

    @Test fun `previewInterval spreads frames across the known duration`() {
        assertEquals(12.0, previewInterval(durationSeconds = 120, frameCount = 10), 0.001)
    }

    @Test fun `previewInterval falls back to one second when duration is unknown`() {
        assertEquals(1.0, previewInterval(durationSeconds = null, frameCount = 10), 0.001)
        assertEquals(1.0, previewInterval(durationSeconds = 0, frameCount = 10), 0.001)
    }

    @Test fun `storyboardTimes spreads evenly and never returns the true final instant`() {
        val times = storyboardTimes(durationSeconds = 90.0, count = 9)
        assertEquals(9, times.size)
        assertEquals(0.0, times.first(), 0.001)
        assertTrue(times.last() < 90.0)
    }

    @Test fun `storyboardTimes is empty for unknown duration`() {
        assertEquals(emptyList<Double>(), storyboardTimes(durationSeconds = 0.0))
        assertEquals(emptyList<Double>(), storyboardTimes(durationSeconds = -1.0))
    }

    @Test fun `storyboardIndexAt finds the slot containing a position, clamped at both ends`() {
        assertEquals(0, storyboardIndexAt(-5.0, durationSeconds = 90.0, count = 9))
        assertEquals(0, storyboardIndexAt(0.0, durationSeconds = 90.0, count = 9))
        assertEquals(4, storyboardIndexAt(45.0, durationSeconds = 90.0, count = 9))
        assertEquals(8, storyboardIndexAt(999.0, durationSeconds = 90.0, count = 9))
    }
}
