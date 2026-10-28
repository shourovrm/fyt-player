package com.fyiplayer.app.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.update.UpdateInfo

/** App identity and the update status: logo, name, installed version, then the status chip. */
@Composable
internal fun AboutHero(
    versionName: String,
    check: CheckState,
    onRetryCheck: () -> Unit,
    onInstall: (UpdateInfo) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.s24, bottom = Spacing.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        AppLogoTile()
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_summary_version, versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UpdateStatus(check, onRetryCheck, onInstall)
    }
}

/** The launcher icon drawn at app size: the background colour tile with the foreground on it. */
@Composable
private fun AppLogoTile() {
    Box(
        Modifier.size(Sizes.aboutLogo)
            .clip(RoundedCornerShape(Radius.l))
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(Sizes.aboutLogoGlyph),
        )
    }
}

/** The app update state as a pill. Found also offers the install action beside the pill. */
@Composable
private fun UpdateStatus(check: CheckState, onRetryCheck: () -> Unit, onInstall: (UpdateInfo) -> Unit) {
    val neutralContainer = MaterialTheme.colorScheme.secondaryContainer
    val neutralContent = MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (check) {
            CheckState.Idle -> Unit
            CheckState.Checking -> StatusChip(
                text = stringResource(R.string.about_update_checking),
                containerColor = neutralContainer,
                contentColor = neutralContent,
                busy = true,
            )
            CheckState.UpToDate -> StatusChip(
                text = stringResource(R.string.about_update_up_to_date),
                containerColor = neutralContainer,
                contentColor = neutralContent,
                leadingIcon = Icons.Filled.Check,
            )
            CheckState.Failed -> StatusChip(
                text = stringResource(R.string.about_update_failed),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.error,
                onClick = onRetryCheck,
            )
            is CheckState.Found -> {
                StatusChip(
                    text = stringResource(R.string.settings_update_available, check.info.version),
                    containerColor = neutralContainer,
                    contentColor = neutralContent,
                )
                TextButton(onClick = { onInstall(check.info) }) {
                    Text(stringResource(R.string.settings_update_install))
                }
            }
        }
    }
}

/** A 32 dp pill. A tappable pill gets a 48 dp touch area around it; the visible pill stays 32 dp. */
@Composable
private fun StatusChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: (() -> Unit)? = null,
    busy: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val touchModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.minimumInteractiveComponentSize().clickable(onClick = onClick)
    }
    Box(touchModifier, contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(percent = 50))
                .background(containerColor)
                .heightIn(min = Sizes.chip)
                .padding(horizontal = Spacing.s16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(Sizes.iconSmall),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
            }
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(Sizes.iconSmall),
                )
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}
