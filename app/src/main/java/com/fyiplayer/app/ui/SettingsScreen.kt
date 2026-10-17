package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.settings.AccountSettings
import com.fyiplayer.app.settings.AppSettings
import com.fyiplayer.app.settings.BackupSettings
import com.fyiplayer.app.settings.ContentSettings
import com.fyiplayer.app.settings.DownloadSettings
import com.fyiplayer.app.settings.EngineSettings
import com.fyiplayer.app.settings.GestureSettings
import com.fyiplayer.app.settings.HistorySettings
import com.fyiplayer.app.settings.PlaybackSettings
import com.fyiplayer.app.settings.SponsorBlockSettings
import com.fyiplayer.app.settings.SourcesSettings
import com.fyiplayer.app.settings.VideoPageSettings

/** Sections (mockup-v2.png), top to bottom: sources, language & region, account, playback
 *  (resolution merged in), gestures, downloads (file format merged in), history, app, site
 *  support, backup -- each its own file under `settings/`, this just lists them in order. */
@Composable
fun SettingsScreen() {
    val app = rememberFyiApp()
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(bottom = Spacing.s24)) {
        item { SourcesSettings(app.prefs) }
        item { ContentSettings(app.prefs) }
        item { AccountSettings() }
        item { PlaybackSettings(app.prefs) }
        item { SponsorBlockSettings(app.prefs) }
        item { VideoPageSettings(app.prefs) }
        item { GestureSettings(app.prefs) }
        item { DownloadSettings(app.prefs) }
        item { HistorySettings(app.prefs) }
        item { AppSettings() }
        item { EngineSettings() }
        item { BackupSettings() }
    }
}
