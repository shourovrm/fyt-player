package com.fyiplayer.app.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.fyiExtras

/** YouTube-style autoplay switch for the player's bottom bar: a small track with a knob that
 *  carries a play glyph. On = primary track, knob at the end; off = outlined track, knob at the
 *  start. The touch target is the full 48 dp square around it. */
@Composable
internal fun AutoplaySwitch(checked: Boolean, onToggle: () -> Unit) {
    val extras = MaterialTheme.fyiExtras
    val colors = MaterialTheme.colorScheme
    val description = stringResource(if (checked) R.string.player_autoplay_on else R.string.player_autoplay_off)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = Sizes.touchTarget, minHeight = Sizes.touchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .semantics {
                contentDescription = description
                stateDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.switchTrackWidth, Sizes.switchTrackHeight)
                .then(
                    if (checked) Modifier.background(colors.primary, CircleShape)
                    else Modifier.border(BorderStroke(2.dp, extras.onVideo), CircleShape),
                )
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.switchKnob)
                    .background(if (checked) colors.onPrimary else extras.onVideo, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (checked) colors.primary else extras.videoScrim,
                    modifier = Modifier.size(Sizes.switchGlyph),
                )
            }
        }
    }
}
