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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.core.SponsorCategory
import com.fyiplayer.app.core.SponsorSegment
import com.fyiplayer.app.ui.theme.fyiExtras

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

/** The core label ("Intermission / intro animation") is a settings description, too long for a
 *  button, so the button names the part in one word. */
private fun skipNounResource(category: SponsorCategory): Int = when (category) {
    SponsorCategory.SPONSOR -> R.string.player_skip_sponsor
    SponsorCategory.INTRO -> R.string.player_skip_intro
    SponsorCategory.OUTRO -> R.string.player_skip_credits
    SponsorCategory.INTERACTION -> R.string.player_skip_reminder
    SponsorCategory.SELF_PROMO -> R.string.player_skip_promotion
    SponsorCategory.MUSIC_OFFTOPIC -> R.string.player_skip_non_music
    SponsorCategory.PREVIEW -> R.string.player_skip_recap
    SponsorCategory.FILLER -> R.string.player_skip_filler
}

/** Shown while playback is inside a segment whose category is set to "Show skip button". */
@Composable
internal fun SponsorSkipButton(segment: SponsorSegment, modifier: Modifier = Modifier) {
    val extras = MaterialTheme.fyiExtras
    Button(
        onClick = { PlaybackSession.skipSponsorSegment() },
        colors = ButtonDefaults.buttonColors(containerColor = extras.videoScrim, contentColor = extras.onVideo),
        modifier = modifier,
    ) {
        Text(stringResource(R.string.player_skip, stringResource(skipNounResource(segment.category))))
    }
}
