package com.fyiplayer.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.settings.AccountSettings
import com.fyiplayer.app.settings.AppSettings
import com.fyiplayer.app.settings.BackupSettings
import com.fyiplayer.app.settings.ContentSettings
import com.fyiplayer.app.settings.DownloadSettings
import com.fyiplayer.app.settings.EngineSettings
import com.fyiplayer.app.settings.GestureSettings
import com.fyiplayer.app.settings.HistorySettings
import com.fyiplayer.app.settings.PlaybackSettings
import com.fyiplayer.app.settings.SourcesSettings
import com.fyiplayer.app.settings.SponsorBlockSettings
import com.fyiplayer.app.settings.VideoPageSettings
import com.fyiplayer.app.ui.theme.Spacing

/** Route keys of the settings pages, shared by the hub rows and the page switch below. */
object SettingsPage {
    const val ACCOUNT = "account"
    const val CONTENT = "content"
    const val PLAYBACK = "playback"
    const val SKIPPING = "skipping"
    const val DOWNLOADS = "downloads"
    const val HISTORY = "history"
    const val BACKUP = "backup"
    const val ABOUT = "about"
}

/** Page title string per key, or null for a key that names no page. */
@StringRes
internal fun settingsPageTitle(page: String): Int? = when (page) {
    SettingsPage.ACCOUNT -> R.string.settings_account
    SettingsPage.CONTENT -> R.string.settings_content
    SettingsPage.PLAYBACK -> R.string.settings_playback
    SettingsPage.SKIPPING -> R.string.settings_skipping
    SettingsPage.DOWNLOADS -> R.string.settings_downloads
    SettingsPage.HISTORY -> R.string.settings_history
    SettingsPage.BACKUP -> R.string.settings_backup
    SettingsPage.ABOUT -> R.string.settings_about
    else -> null
}

/** One settings page under a 64 dp app bar with a back arrow. The bottom nav stays visible. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScreen(page: String, onBack: () -> Unit) {
    val titleRes = settingsPageTitle(page)
    if (titleRes == null) {
        // A stale or mistyped key has nothing to show; leave rather than render an empty page.
        LaunchedEffect(page) { onBack() }
        return
    }
    val prefs = rememberFyiApp().prefs
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.s24)) {
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
                SettingsPage.ABOUT -> {
                    AppSettings()
                    EngineSettings()
                }
            }
        }
    }
}
