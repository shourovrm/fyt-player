package com.fyiplayer.app.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fyiplayer.app.core.SeekThumbnails

/** Small pill HUD used for both the brightness and volume gestures. */
@Composable
fun GestureHud(percent: Int, isBrightness: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (isBrightness) "Brightness" else "Volume", color = Color.White)
        Spacer(Modifier.width(8.dp))
        Text("$percent%", color = Color.White)
    }
}

/** Horizontal-drag seek preview: "target position / total duration" pill. */
@Composable
fun SeekPreviewHud(previewText: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(previewText, color = Color.White)
    }
}

internal val SEEK_PREVIEW_WIDTH = 112.dp
internal val SEEK_PREVIEW_HEIGHT = 63.dp

/**
 * One preview frame in a [width]x[height] box: either a direct still ([SeekPreviewImage.Frame])
 * or one tile cropped out of a sprite sheet ([SeekPreviewImage.Tile]). A sprite tile is cropped
 * without any bitmap-region decoding: the whole sheet is loaded at a size scaled so one tile
 * exactly fills the box (cols/rows tiles -> cols/rows times that box), then shifted by col/row
 * tiles so the wanted tile lands in the visible window; this [Box]'s `clip` hides the rest. Coil
 * caches by URL, so cropping another tile of the *same* sheet — scrubbing, or the jump grid — costs
 * no extra fetch. Shared by the scrub preview and the jump grid, which is why the box size is a
 * parameter rather than the fixed preview constants.
 */
@Composable
fun SeekThumbnailTile(image: SeekPreviewImage?, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.DarkGray),
    ) {
        when (image) {
            null -> {}
            is SeekPreviewImage.Frame -> AsyncImage(
                model = image.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // requiredSize, NOT size: the sheet is cols x rows times bigger than this Box, and
            // `size` only coerces INTO the incoming constraints — the parent Box is already fixed
            // at one tile, so `size` would clamp the sheet back down and the offset would then
            // push it out of view, painting a blank grey square. `requiredSize` ignores the
            // incoming constraints, which is the whole point: draw it oversized, let the parent clip.
            is SeekPreviewImage.Tile -> AsyncImage(
                model = image.sheet.url,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .requiredSize(width * image.sheet.cols, height * image.sheet.rows)
                    .offset(x = -width * image.col, y = -height * image.row),
            )
        }
    }
}

/** Scrub preview floating above the scrubber thumb: the frame plus its timestamp. [image] null —
 *  the sheet is still in flight, or this source publishes none — drops the frame box entirely and
 *  shows only the timestamp pill, so an early scrub reads as "3:07", never an empty grey square. */
@Composable
fun SeekThumbnailPreview(image: SeekPreviewImage?, timestampText: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (image != null) {
            SeekThumbnailTile(image, SEEK_PREVIEW_WIDTH, SEEK_PREVIEW_HEIGHT)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            timestampText,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Accumulating double-tap overlay: direction + running total. */
@Composable
fun FastSeekOverlay(totalSeconds: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(if (totalSeconds >= 0) ">> ${totalSeconds}s" else "<< ${-totalSeconds}s", color = Color.White)
    }
}

/** Two bars — `material-icons-core` (the only icons dependency here) has no Pause glyph. [size] is
 *  a parameter because the centred play/pause control draws it larger than a bar icon. */
@Composable
fun PauseGlyph(tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Row(modifier = modifier.size(size), horizontalArrangement = Arrangement.spacedBy(size / 6)) {
        Spacer(Modifier.weight(1f).fillMaxHeight().background(tint))
        Spacer(Modifier.weight(1f).fillMaxHeight().background(tint))
    }
}

/** Triangle + bar — `material-icons-core` has no SkipNext/SkipPrevious glyph either, so this draws
 *  its own rather than adding an icons dependency for two shapes. [forward] mirrors it horizontally. */
@Composable
fun SkipGlyph(forward: Boolean, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barW = w * 0.12f
        val triW = w * 0.62f
        val path = Path().apply {
            if (forward) {
                moveTo(0f, h * 0.16f); lineTo(triW, h * 0.5f); lineTo(0f, h * 0.84f); close()
            } else {
                moveTo(w, h * 0.16f); lineTo(w - triW, h * 0.5f); lineTo(w, h * 0.84f); close()
            }
        }
        drawPath(path, tint)
        drawRect(
            color = tint,
            topLeft = Offset(if (forward) w - barW else 0f, h * 0.16f),
            size = Size(barW, h * 0.68f),
        )
    }
}

/** Four corner brackets — `material-icons-core` has no Fullscreen/FullscreenExit glyph either.
 *  [fullscreen] false draws brackets at the corners with arms pointing inward (enter fullscreen);
 *  true draws them near the centre with arms pointing outward (exit fullscreen). */
@Composable
fun FullscreenGlyph(fullscreen: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val arm = size.minDimension * 0.32f
        val inset = if (fullscreen) size.minDimension * 0.30f else size.minDimension * 0.08f
        val armSign = if (fullscreen) -1f else 1f
        val corners = listOf(
            Offset(inset, inset) to Offset(1f, 1f),
            Offset(size.width - inset, inset) to Offset(-1f, 1f),
            Offset(inset, size.height - inset) to Offset(1f, -1f),
            Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
        )
        corners.forEach { (origin, dir) ->
            val ax = dir.x * armSign * arm
            val ay = dir.y * armSign * arm
            drawLine(tint, origin, Offset(origin.x + ax, origin.y), stroke, cap = StrokeCap.Round)
            drawLine(tint, origin, Offset(origin.x, origin.y + ay), stroke, cap = StrokeCap.Round)
        }
    }
}

/**
 * 3x3 "jump to" grid, built from the same [SeekThumbnails.imageFor]/[storyboardTimes] mapper the
 * scrub preview uses. Tapping a tile seeks there and closes the sheet; the tile covering the
 * current position gets an outline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpGridSheet(
    seekThumbs: SeekThumbnails,
    durationSeconds: Double,
    positionSeconds: Double,
    onJump: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val times = storyboardTimes(durationSeconds)
    val activeIndex = storyboardIndexAt(positionSeconds, durationSeconds)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            times.chunked(3).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { colIndex, t ->
                        val tileIndex = rowIndex * 3 + colIndex
                        val outline = if (tileIndex == activeIndex) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        } else {
                            Modifier
                        }
                        Box(outline.clip(RoundedCornerShape(6.dp)).clickable { onJump(t) }) {
                            SeekThumbnailTile(seekThumbs.imageFor(t), width = 96.dp, height = 54.dp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
