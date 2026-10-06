package com.fyiplayer.app.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Each arrow is two polylines (shaft, then head); points are (x, y) fractions of the square. */
private val UPPER_ARROW = listOf(
    listOf(0.18f to 0.52f, 0.18f to 0.30f, 0.82f to 0.30f),
    listOf(0.70f to 0.18f, 0.84f to 0.30f, 0.70f to 0.42f),
)
private val LOWER_ARROW = listOf(
    listOf(0.82f to 0.48f, 0.82f to 0.70f, 0.18f to 0.70f),
    listOf(0.30f to 0.58f, 0.16f to 0.70f, 0.30f to 0.82f),
)

/** Two arrows chasing each other round a rectangle (the usual "repeat" mark). Hand-drawn because
 *  material-icons-core has no Repeat glyph. */
@Composable
fun RepeatGlyph(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val side = this.size.width
        val path = Path()
        for (polyline in UPPER_ARROW + LOWER_ARROW) {
            polyline.forEachIndexed { index, (x, y) ->
                if (index == 0) path.moveTo(x * side, y * side) else path.lineTo(x * side, y * side)
            }
        }
        drawPath(path, tint, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
