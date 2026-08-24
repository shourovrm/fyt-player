package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/** Sections (mockup-v2.png), top to bottom: sources, language & region, account, playback
 *  (resolution merged in), gestures, downloads (file format merged in), history, app, video
 *  engine, backup -- each its own file under `settings/`, this just lists them in order. */
@Composable
fun SettingsScreen() {
    val app = rememberFyiApp()
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(bottom = 24.dp)) {
        item { SourcesSettings(app.prefs) }
        item { ContentSettings(app.prefs) }
        item { AccountSettings() }
        item { PlaybackSettings(app.prefs) }
        item { GestureSettings(app.prefs) }
        item { DownloadSettings(app.prefs) }
        item { HistorySettings(app.prefs) }
        item { AppSettings() }
        item { EngineSettings() }
        item { BackupSettings() }
    }
}
