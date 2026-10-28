package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

/** The three actions of the About page as round buttons with captions. Reports is shown only when
 *  a report exists; without it the two remaining buttons are spread evenly across the row. */
@Composable
internal fun AboutActionStrip(
    appCheckRunning: Boolean,
    onCheckApp: () -> Unit,
    siteSupportRunning: Boolean,
    onUpdateSiteSupport: () -> Unit,
    reportsAvailable: Boolean,
    onReports: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = Spacing.s16),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionCircle(
            icon = Icons.Filled.Refresh,
            caption = stringResource(R.string.about_action_update),
            contentDescription = stringResource(R.string.settings_check_updates),
            enabled = !appCheckRunning,
            busy = false,
            onClick = onCheckApp,
        )
        ActionCircle(
            icon = Icons.Filled.Build,
            caption = stringResource(R.string.settings_site_support),
            contentDescription = stringResource(R.string.settings_site_support_update),
            enabled = !siteSupportRunning,
            busy = siteSupportRunning,
            onClick = onUpdateSiteSupport,
        )
        if (reportsAvailable) {
            ActionCircle(
                icon = Icons.Filled.Warning,
                caption = stringResource(R.string.settings_reports),
                contentDescription = stringResource(R.string.settings_reports),
                enabled = true,
                busy = false,
                onClick = onReports,
            )
        }
    }
}

/** A 56 dp tonal circle with its glyph and a caption under it. While [busy] the glyph becomes a spinner. */
@Composable
private fun ActionCircle(
    icon: ImageVector,
    caption: String,
    contentDescription: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(Sizes.actionButton),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(Sizes.icon), strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(Sizes.icon))
            }
        }
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.s8),
        )
    }
}
