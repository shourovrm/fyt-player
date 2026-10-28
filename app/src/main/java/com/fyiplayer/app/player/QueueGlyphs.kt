package com.fyiplayer.app.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.fyiplayer.app.ui.theme.Sizes

// material-icons-core 1.7.8 has neither glyph, so the Material filled paths are inlined. They are
// drawn from the 24-unit viewBox the paths were authored in, scaled to the caller's box.
private const val MATERIAL_VIEWPORT = 24f

private const val SHUFFLE_PATH_DATA =
    "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"

private const val CLEAR_ALL_PATH_DATA =
    "M14 10H3v2h11v-2zm0-4H3v2h11V6zM3 16h7v-2H3v2zm11.41 6L17 19.41 19.59 22 21 20.59 18.41 18 21 15.41 19.59 14 17 16.59 14.41 14 13 15.41 15.59 18 13 20.59 14.41 22z"

/** Crossed arrows, the queue's Shuffle action. */
@Composable
fun ShuffleGlyph(tint: Color, size: Dp = Sizes.icon, modifier: Modifier = Modifier) {
    PathGlyph(SHUFFLE_PATH_DATA, tint, size, modifier)
}

/** A list with an X, the queue's Clear action. */
@Composable
fun ClearAllGlyph(tint: Color, size: Dp = Sizes.icon, modifier: Modifier = Modifier) {
    PathGlyph(CLEAR_ALL_PATH_DATA, tint, size, modifier)
}

@Composable
private fun PathGlyph(pathData: String, tint: Color, size: Dp, modifier: Modifier) {
    // Parsed once per path string: the path is fixed, and re-parsing on every draw would be wasted work.
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier = modifier.size(size)) {
        val viewportScale = this.size.minDimension / MATERIAL_VIEWPORT
        withTransform({ scale(viewportScale, viewportScale, pivot = Offset.Zero) }) {
            drawPath(path, tint)
        }
    }
}
