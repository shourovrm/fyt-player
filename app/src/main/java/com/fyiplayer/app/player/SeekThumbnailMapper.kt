package com.fyiplayer.app.player

import com.fyiplayer.app.core.SeekThumbnails
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.SpriteSheet
import com.fyiplayer.app.core.VideoRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

/**
 * One resolved scrub-preview image: a direct still, or one tile location within a sprite sheet
 * (cropped at draw time — see `SeekThumbnailTile` in PlayerOverlays.kt).
 */
sealed class SeekPreviewImage {
    data class Frame(val url: String) : SeekPreviewImage()
    data class Tile(val sheet: SpriteSheet, val col: Int, val row: Int) : SeekPreviewImage()
}

/**
 * Maps a scrub time to the still/tile that covers it: [SeekThumbnails.frames] is index =
 * floor(time/interval) into a flat list; [SeekThumbnails.sprites] walks the sheets in order,
 * subtracting each sheet's tile count, until the running index lands inside one — the col/row
 * within that sheet is row-major (index % cols, index / cols). Past either list's end clamps to
 * the last entry rather than returning null, so the preview simply "sticks" on the final
 * frame/tile instead of vanishing.
 */
fun SeekThumbnails.imageFor(timeSec: Double): SeekPreviewImage? {
    if (intervalSeconds <= 0.0) return null
    if (frames.isNotEmpty()) {
        val index = floor(timeSec / intervalSeconds).toInt().coerceIn(0, frames.size - 1)
        return SeekPreviewImage.Frame(frames[index])
    }
    if (sprites.isNotEmpty()) {
        var index = floor(timeSec / intervalSeconds).toInt().coerceAtLeast(0)
        for (sheet in sprites) {
            if (index < sheet.count) return SeekPreviewImage.Tile(sheet, index % sheet.cols, index / sheet.cols)
            index -= sheet.count
        }
        val last = sprites.last()
        val lastIndex = (last.count - 1).coerceAtLeast(0)
        return SeekPreviewImage.Tile(last, lastIndex % last.cols, lastIndex / last.cols)
    }
    return null
}

/**
 * [SeekThumbnails.intervalSeconds] for a tile preview of [frameCount] frames spread over a video
 * of [durationSeconds]. Falls back to 1.0 when the listing gave no duration — the flipbook cycles
 * frames and never asks what time a frame is, so the interval is otherwise unused, but it must
 * still be > 0 because [imageFor] returns null on a non-positive interval.
 */
fun previewInterval(durationSeconds: Int?, frameCount: Int): Double {
    val seconds = durationSeconds?.takeIf { it > 0 } ?: return 1.0
    return (seconds.toDouble() / frameCount).coerceAtLeast(0.001)
}

/**
 * Every frame this [SeekThumbnails] holds, in time order, as renderable images. A flipbook needs
 * the whole set rather than a lookup by time; flattening both shapes here is what lets one preview
 * composable serve numbered stills and sprite tiles with no branch of its own.
 */
fun SeekThumbnails.previewImages(): List<SeekPreviewImage> = when {
    frames.isNotEmpty() -> frames.map { SeekPreviewImage.Frame(it) }
    else -> sprites.flatMap { sheet ->
        (0 until sheet.count).map { SeekPreviewImage.Tile(sheet, it % sheet.cols, it / sheet.cols) }
    }
}

/** Tiles in the "jump to" grid. */
const val STORYBOARD_TILES = 9

/**
 * [count] evenly-spaced sample times (seconds) across a video: index i sits at
 * `i * duration / count`, so tile 0 is the opening frame and the last tile lands one slot short of
 * the end (the final frame of a clip is usually black/credits — a useless preview tile).
 * Non-positive duration (unknown yet) yields an empty list, the caller's cue to render no grid at
 * all rather than nine copies of frame 0.
 */
fun storyboardTimes(durationSeconds: Double, count: Int = STORYBOARD_TILES): List<Double> {
    if (durationSeconds <= 0.0 || count <= 0) return emptyList()
    return List(count) { it * durationSeconds / count }
}

/**
 * Index into [storyboardTimes] whose slot contains [positionSeconds] — the tile that gets the
 * active outline. Clamped both ends, so a position past the end sticks on the last tile.
 */
fun storyboardIndexAt(positionSeconds: Double, durationSeconds: Double, count: Int = STORYBOARD_TILES): Int {
    if (durationSeconds <= 0.0 || count <= 0) return 0
    return floor(positionSeconds / durationSeconds * count).toInt().coerceIn(0, count - 1)
}

/**
 * One fetch of [ref]'s scrubber sprite sheet / frame list, off the main thread. Every failure is
 * silent — no thumbnails is not an error state, the scrubber just shows the timestamp alone.
 */
suspend fun fetchSeekThumbnails(ref: VideoRef): SeekThumbnails? = withContext(Dispatchers.IO) {
    try {
        SourceRegistry.bySourceId(ref.sourceId)?.seekThumbnails(ref)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }
}
