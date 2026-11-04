package com.fyiplayer.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.BuildConfig
import com.fyiplayer.app.R
import com.fyiplayer.app.source.newpipe.YoutubeAuth
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

/** One hub row: where it goes, how it looks, and its state in words when the title alone does not say it. */
private class HubRow(
    val page: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val summary: String?,
)

/** The settings hub: eight rows, each opening its own page (SettingsPageScreen). Only Account and
 *  About show a second line, because only their state (signed in, version) is not clear from the title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenPage: (String) -> Unit) {
    val rows = hubRows()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(title = { Text(stringResource(R.string.shell_tab_settings), style = MaterialTheme.typography.titleLarge) })
        LazyColumn(Modifier.fillMaxSize().padding(bottom = Spacing.s24)) {
            items(rows, key = { it.page }) { row ->
                HubRowItem(row, onClick = { onOpenPage(row.page) })
            }
        }
    }
}

@Composable
private fun HubRowItem(row: HubRow, onClick: () -> Unit) {
    val minHeight = if (row.summary == null) Sizes.listOneLine else Sizes.listTwoLine
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = minHeight)
            .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.icon))
        Column(Modifier.weight(1f).padding(horizontal = Spacing.s16)) {
            Text(stringResource(row.titleRes), style = MaterialTheme.typography.bodyLarge)
            if (row.summary != null) {
                Text(
                    row.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

@Composable
private fun hubRows(): List<HubRow> = listOf(
    HubRow(SettingsPage.ACCOUNT, R.string.settings_account, Icons.Filled.Person, accountSummary()),
    HubRow(SettingsPage.CONTENT, R.string.settings_content, Icons.AutoMirrored.Filled.List, null),
    HubRow(SettingsPage.PLAYBACK, R.string.settings_playback, Icons.Filled.PlayArrow, null),
    HubRow(SettingsPage.SKIPPING, R.string.settings_skipping, Icons.Filled.ArrowForward, null),
    HubRow(SettingsPage.DOWNLOADS, R.string.settings_downloads, Icons.Filled.KeyboardArrowDown, null),
    HubRow(SettingsPage.HISTORY, R.string.settings_history, Icons.Filled.Refresh, null),
    HubRow(SettingsPage.BACKUP, R.string.settings_backup, Icons.Filled.Share, null),
    HubRow(SettingsPage.ABOUT, R.string.settings_about, Icons.Filled.Info, BuildConfig.VERSION_NAME),
)

@Composable
private fun accountSummary(): String {
    val signedIn by YoutubeAuth.isLoggedIn.collectAsStateWithLifecycle()
    return stringResource(if (signedIn) R.string.settings_summary_signed_in else R.string.settings_summary_signed_out)
}
