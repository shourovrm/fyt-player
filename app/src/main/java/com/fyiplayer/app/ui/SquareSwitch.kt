package com.fyiplayer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.ui.theme.ControlShape
import com.fyiplayer.app.ui.theme.Sizes

/**
 * Drop-in for Material's `Switch` with a square track (Material's track shape is fixed to a pill).
 * Same contract: pass null [onCheckedChange] when the whole row is already toggleable.
 */
@Composable
fun SquareSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = Sizes.touchTarget, minHeight = Sizes.touchTarget)
            .then(toggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.squareSwitchWidth, Sizes.squareSwitchHeight)
                .then(
                    if (checked) Modifier.background(colors.primary, ControlShape)
                    else Modifier.border(BorderStroke(2.dp, colors.onSurfaceVariant), ControlShape),
                )
                .padding(4.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(Sizes.squareSwitchKnob)
                    .background(if (checked) colors.onPrimary else colors.onSurfaceVariant, ControlShape),
            )
        }
    }
}
