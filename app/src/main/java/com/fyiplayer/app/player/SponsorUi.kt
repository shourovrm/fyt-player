package com.fyiplayer.app.player

import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.fyiplayer.app.core.SponsorSegment

/** Coloured spans over the seekbar track, one per segment, in SponsorBlock's own category
 *  colours. Draws nothing until the duration is known. */
@Composable
internal fun SponsorMarkers(segments: List<SponsorSegment>, durationMs: Long, modifier: Modifier = Modifier) {
    if (segments.isEmpty() || durationMs <= 0) return
    Canvas(modifier) {
        segments.forEach { segment ->
            val startFraction = (segment.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val endFraction = (segment.endMs.toFloat() / durationMs).coerceIn(0f, 1f)
            drawRect(
                color = Color(segment.category.markerArgb),
                topLeft = Offset(startFraction * size.width, 0f),
                size = Size((endFraction - startFraction) * size.width, size.height),
            )
        }
    }
}

/** Shown while playback is inside a segment whose category is set to "Show skip button". */
@Composable
internal fun SponsorSkipButton(segment: SponsorSegment, modifier: Modifier = Modifier) {
    Button(
        onClick = { PlaybackSession.skipSponsorSegment() },
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.7f), contentColor = Color.White),
        modifier = modifier,
    ) {
        Text("Skip ${segment.category.label}")
    }
}
