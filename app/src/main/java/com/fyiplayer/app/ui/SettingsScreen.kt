package com.fyiplayer.app.ui

import com.fyiplayer.app.ui.theme.ExtraIcons
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.BuildConfig
import com.fyiplayer.app.R
import com.fyiplayer.app.settings.AboutSettings
import com.fyiplayer.app.settings.AccountSettings
import com.fyiplayer.app.settings.BackupSettings
import com.fyiplayer.app.settings.ContentSettings
import com.fyiplayer.app.settings.DownloadSettings
import com.fyiplayer.app.settings.GestureSettings
import com.fyiplayer.app.settings.HistorySettings
import com.fyiplayer.app.settings.PlaybackSettings
import com.fyiplayer.app.settings.SourcesSettings
import com.fyiplayer.app.settings.SponsorBlockSettings
import com.fyiplayer.app.settings.VideoPageSettings
import com.fyiplayer.app.source.newpipe.YoutubeAuth
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** The settings pages, in hub order. Each one opens as a bottom sheet over the hub. */
private enum class SettingsPage(@StringRes val titleRes: Int, val icon: ImageVector) {
    ACCOUNT(R.string.settings_account, Icons.Filled.Person),
    CONTENT(R.string.settings_content, Icons.AutoMirrored.Filled.List),
    PLAYBACK(R.string.settings_playback, Icons.Filled.PlayArrow),
    SKIPPING(R.string.settings_skipping, Icons.Filled.ArrowForward),
    DOWNLOADS(R.string.settings_downloads, ExtraIcons.Download),
    HISTORY(R.string.settings_history, ExtraIcons.History),
    BACKUP(R.string.settings_backup, Icons.Filled.Share),
    ABOUT(R.string.settings_about, Icons.Filled.Info),
}

/** The settings hub, reached from the app bar. Its rows open their page as a sheet, so the hub
 *  stays underneath and Back closes the sheet before it leaves the hub. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var openPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(stringResource(R.string.shell_tab_settings), style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize().padding(bottom = Spacing.s24)) {
            items(SettingsPage.entries, key = { it.name }) { page ->
                HubRowItem(page, summary = hubSummary(page), onClick = { openPage = page })
            }
        }
    }
    openPage?.let { page -> SettingsPageSheet(page, onDismiss = { openPage = null }) }
}

@Composable
private fun HubRowItem(page: SettingsPage, summary: String?, onClick: () -> Unit) {
    val minHeight = if (summary == null) Sizes.listOneLine else Sizes.listTwoLine
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = minHeight)
            .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Sizes.button)
                .clip(RoundedCornerShape(Radius.s))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.icon),
            )
        }
        Column(Modifier.weight(1f).padding(start = Spacing.s16)) {
            Text(stringResource(page.titleRes), style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Only Account (signed in or out) and About (version) need a second line: their state is not
 *  clear from the title alone. */
@Composable
private fun hubSummary(page: SettingsPage): String? = when (page) {
    SettingsPage.ACCOUNT -> accountSummary()
    SettingsPage.ABOUT -> BuildConfig.VERSION_NAME
    SettingsPage.CONTENT,
    SettingsPage.PLAYBACK,
    SettingsPage.SKIPPING,
    SettingsPage.DOWNLOADS,
    SettingsPage.HISTORY,
    SettingsPage.BACKUP,
    -> null
}

@Composable
private fun accountSummary(): String {
    val signedIn by YoutubeAuth.isLoggedIn.collectAsStateWithLifecycle()
    return stringResource(if (signedIn) R.string.settings_summary_signed_in else R.string.settings_summary_signed_out)
}

/** A page as a bottom sheet: title and close on top, the page body scrolls underneath. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPageSheet(page: SettingsPage, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.pageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(page.titleRes),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            // Animate the sheet out before removing it; removing it at once would cut the slide.
            IconButton(onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.s24)) {
            SettingsPageBody(page)
        }
    }
}

@Composable
private fun SettingsPageBody(page: SettingsPage) {
    val prefs = rememberFyiApp().prefs
    when (page) {
        SettingsPage.ACCOUNT -> AccountSettings()
        SettingsPage.CONTENT -> {
            SourcesSettings(prefs)
            ContentSettings(prefs)
        }
        SettingsPage.PLAYBACK -> {
            PlaybackSettings(prefs)
            GestureSettings(prefs)
            VideoPageSettings(prefs)
        }
        SettingsPage.SKIPPING -> SponsorBlockSettings(prefs)
        SettingsPage.DOWNLOADS -> DownloadSettings(prefs)
        SettingsPage.HISTORY -> HistorySettings(prefs)
        SettingsPage.BACKUP -> BackupSettings()
        SettingsPage.ABOUT -> AboutSettings()
    }
}
