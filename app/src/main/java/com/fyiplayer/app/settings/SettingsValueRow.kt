package com.fyiplayer.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

// Material's disabled-content opacity.
private const val DISABLED_ALPHA = 0.38f

/** Which affordance trails the value: a chevron opens a dialog, a drop arrow opens a menu. */
internal enum class ValueRowOpens { DIALOG, MENU }

/** A 56 dp one-line row: optional leading icon, label, the current value in onSurfaceVariant, then
 *  the affordance. For a menu, wrap the row and its DropdownMenu in a Box so the menu anchors to the row. */
@Composable
internal fun SettingsValueRow(
    label: String,
    value: String,
    opens: ValueRowOpens,
    onClick: () -> Unit,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val rowModifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier.alpha(DISABLED_ALPHA)
    ValueRowContent(rowModifier, label, value, leadingIcon) {
        val arrow = when (opens) {
            ValueRowOpens.DIALOG -> Icons.AutoMirrored.Filled.KeyboardArrowRight
            ValueRowOpens.MENU -> Icons.Filled.ArrowDropDown
        }
        Icon(
            arrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).size(Sizes.icon),
        )
    }
}

/** A 56 dp one-line row that cannot be tapped: it only shows a value, so it has no affordance. */
@Composable
internal fun SettingsReadOnlyRow(label: String, value: String, leadingIcon: ImageVector? = null) {
    ValueRowContent(Modifier, label, value, leadingIcon) {}
}

@Composable
private fun ValueRowContent(
    rowModifier: Modifier,
    label: String,
    value: String,
    leadingIcon: ImageVector?,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().then(rowModifier).heightIn(min = Sizes.listOneLine)
            .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.icon),
            )
            Spacer(Modifier.width(Spacing.s16))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trailing()
    }
}
