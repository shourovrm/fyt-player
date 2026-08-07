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
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.fyiplayer.app.core.SeekThumbnails
import kotlin.math.roundToInt

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

// 140dp ≈ NewPipe's scrub-card footprint; height floats with the tile aspect (see
// SeekThumbnailPreview), HEIGHT below is just the 16:9 default/floor.
internal val SEEK_PREVIEW_WIDTH = 140.dp
internal val SEEK_PREVIEW_HEIGHT = 79.dp
internal val SEEK_PREVIEW_GAP = 8.dp

/**
 * One preview frame in a [width]x[height] box: either a direct still ([SeekPreviewImage.Frame])
 * or one tile cropped out of a sprite sheet ([SeekPreviewImage.Tile]). A sprite tile is drawn by
 * decoding the whole sheet once (Coil, software bitmap) and painting ONLY the wanted tile via a
 * source-rect [drawImage] — the previous scale-shift-and-clip approach relied on layout centering
 * behaviour it didn't control and showed the whole sheet as a mini grid mid-scrub. Coil caches by
 * URL, so another tile of the *same* sheet (scrubbing, the jump grid) costs no refetch/redecode.
 * Shared by the scrub preview and the jump grid, which is why the box size is a parameter.
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
            is SeekPreviewImage.Tile -> {
                val context = LocalContext.current
                // Software bitmap: drawImage needs pixel access, hardware bitmaps refuse it.
                var sheetBitmap by remember(image.sheet.url) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(image.sheet.url) {
                    // ORIGINAL size is load-bearing: without it Coil can serve this URL from the
                    // memory cache at whatever size some other view decoded it, and the pixel
                    // tile math below then crops the wrong region (reported: stretched card
                    // showing parts of two frames).
                    val result = context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(image.sheet.url)
                            .size(coil.size.Size.ORIGINAL)
                            .allowHardware(false)
                            .build(),
                    )
                    sheetBitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)
                        ?.bitmap?.asImageBitmap()
                }
                sheetBitmap?.let { sheet ->
                    val rect = tileRect(image.sheet, image.col, image.row, sheet.width, sheet.height)
                    Canvas(Modifier.fillMaxSize()) {
                        drawImage(
                            image = sheet,
                            srcOffset = IntOffset(rect.left, rect.top),
                            srcSize = IntSize(rect.width, rect.height),
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        )
                    }
                }
            }
        }
    }
}

/** Scrub preview floating above the scrubber thumb: the frame plus its timestamp. [image] null —
 *  the sheet is still in flight, or this source publishes none — drops the frame box entirely and
 *  shows only the timestamp pill, so an early scrub reads as "3:07", never an empty grey square.
 *  The card keeps the TILE's own aspect ratio (NewPipe does the same): a portrait video's
 *  portrait storyboard must not get squeezed into a 16:9 box. */
@Composable
fun SeekThumbnailPreview(image: SeekPreviewImage?, timestampText: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (image != null) {
            val cardHeight = when (image) {
                is SeekPreviewImage.Tile -> {
                    val aspect = image.sheet.tileWidth.toFloat() /
                        image.sheet.tileHeight.coerceAtLeast(1).toFloat()
                    (SEEK_PREVIEW_WIDTH / aspect).coerceIn(SEEK_PREVIEW_HEIGHT, 250.dp)
                }
                else -> SEEK_PREVIEW_HEIGHT
            }
            SeekThumbnailTile(image, SEEK_PREVIEW_WIDTH, cardHeight)
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
 *  true draws them near the centre with arms pointing outward (exit fullscreen). [size] is a
 *  parameter (like [PauseGlyph]/[SkipGlyph]) so the slimmer control-bar icon cluster can draw it
 *  smaller than the original 20dp. */
@Composable
fun FullscreenGlyph(fullscreen: Boolean, tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        // this.size (DrawScope's own canvas-px Size), not the outer Dp parameter -- same name,
        // shadowed, so it must be qualified (matches SkipGlyph/PauseGlyph just above).
        val stroke = 2.dp.toPx()
        val arm = this.size.minDimension * 0.32f
        val inset = if (fullscreen) this.size.minDimension * 0.30f else this.size.minDimension * 0.08f
        val armSign = if (fullscreen) -1f else 1f
        val corners = listOf(
            Offset(inset, inset) to Offset(1f, 1f),
            Offset(this.size.width - inset, inset) to Offset(-1f, 1f),
            Offset(inset, this.size.height - inset) to Offset(1f, -1f),
            Offset(this.size.width - inset, this.size.height - inset) to Offset(-1f, -1f),
        )
        corners.forEach { (origin, dir) ->
            val ax = dir.x * armSign * arm
            val ay = dir.y * armSign * arm
            drawLine(tint, origin, Offset(origin.x + ax, origin.y), stroke, cap = StrokeCap.Round)
            drawLine(tint, origin, Offset(origin.x, origin.y + ay), stroke, cap = StrokeCap.Round)
        }
    }
}

/** Rounded rect + two text-line ticks — neither `material-icons-core` nor the extended set is on
 *  this app's classpath, and there is no built-in "CC" glyph either way. [dim] is the disabled
 *  look the control bar uses when the current item has no caption tracks at all. */
@Composable
fun CaptionsGlyph(tint: Color, dim: Boolean = false, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val color = if (dim) tint.copy(alpha = 0.4f) else tint
    Box(
        modifier = modifier
            .size(width = size, height = size * 0.72f)
            .border(1.5.dp, color, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(size * 0.12f)) {
            Spacer(Modifier.width(size * 0.26f).height(1.5.dp).background(color))
            Spacer(Modifier.width(size * 0.26f).height(1.5.dp).background(color))
        }
    }
}

/** 2x2 grid of rounded squares — plain [Box]es, not a hand-drawn [Canvas] path, since four squares
 *  need no path math. Opens the storyboard preview grid ([JumpGridSheet]) from the control bar. */
@Composable
fun PreviewGridGlyph(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val cell = size * 0.42f
    val gap = size * 0.16f
    Column(modifier = modifier.size(size), verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(2) {
                    Spacer(Modifier.size(cell).background(tint, RoundedCornerShape(1.5.dp)))
                }
            }
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
