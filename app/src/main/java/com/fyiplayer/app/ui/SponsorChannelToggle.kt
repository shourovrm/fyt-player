package com.fyiplayer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.core.canonicalChannelKey
import com.fyiplayer.app.ui.theme.Sizes
import kotlinx.coroutines.launch

/**
 * Per-channel SponsorBlock exception, the fifth icon of the video page's action row. Renders
 * nothing while SponsorBlock is off, since there would be nothing to except the channel from.
 * The icon is a check-shield while skipping, an outline shield while this channel is excluded.
 * [onMessage] receives the confirmation text for each tap; the caller decides where it shows.
 */
@Composable
internal fun SponsorChannelToggle(channelUrl: String?, onMessage: (String) -> Unit) {
    val prefs = rememberFyiApp().prefs
    val scope = rememberCoroutineScope()
    val enabled by prefs.sponsorBlock.collectAsStateWithLifecycle(initialValue = false)
    val whitelist by prefs.sponsorWhitelist.collectAsStateWithLifecycle(initialValue = emptySet())
    val key = channelUrl?.let(::canonicalChannelKey)
    if (channelUrl == null || key == null || !enabled) return

    val whitelisted = key in whitelist
    // Icon-only, so the spoken label carries the full action the glyph cannot show.
    val spokenAction = if (whitelisted) stringResource(R.string.detail_sponsor_resume_spoken) else stringResource(R.string.detail_sponsor_stop_spoken)
    val skippingOnMessage = stringResource(R.string.detail_sponsor_skipping_on)
    val skippingOffMessage = stringResource(R.string.detail_sponsor_skipping_off)
    val iconTint = if (whitelisted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    IconButton(
        onClick = {
            scope.launch { prefs.setSponsorChannelWhitelisted(channelUrl, !whitelisted) }
            // Message names the state the tap switches to, so it is chosen from the current value.
            onMessage(if (whitelisted) skippingOnMessage else skippingOffMessage)
        },
        modifier = Modifier.semantics { contentDescription = spokenAction },
    ) {
        ShieldGlyph(checked = !whitelisted, tint = iconTint, size = Sizes.icon)
    }
}

/** A shield outline, with a check inside when [checked]. Drawn by hand: material-icons-core has
 *  no shield glyph (same gap as SaveGlyph in DetailScreen). Coordinates are the 24-unit grid. */
@Composable
private fun ShieldGlyph(checked: Boolean, tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val unit = this.size.width / 24f
        val stroke = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val shield = Path().apply {
            moveTo(12f * unit, 1f * unit)
            lineTo(3f * unit, 5f * unit)
            lineTo(3f * unit, 11f * unit)
            cubicTo(3f * unit, 16.55f * unit, 6.84f * unit, 21.74f * unit, 12f * unit, 23f * unit)
            cubicTo(17.16f * unit, 21.74f * unit, 21f * unit, 16.55f * unit, 21f * unit, 11f * unit)
            lineTo(21f * unit, 5f * unit)
            close()
        }
        drawPath(shield, tint, style = stroke)
        if (checked) {
            val check = Path().apply {
                moveTo(8f * unit, 12f * unit)
                lineTo(11f * unit, 15f * unit)
                lineTo(16f * unit, 10f * unit)
            }
            drawPath(check, tint, style = stroke)
        }
    }
}
