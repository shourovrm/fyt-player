package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.settings.ContainerSettings
import com.fyiplayer.app.settings.GestureSettings
import com.fyiplayer.app.settings.HistorySettings
import com.fyiplayer.app.settings.PlaybackSettings
import com.fyiplayer.app.settings.ResolutionSettings
import com.fyiplayer.app.settings.SourcesSettings

/** Sections (DESIGN.md §5): sources, resolution, container, history, gestures, playback -- each
 *  its own file under `settings/`, this just lists them in order. */
@Composable
fun SettingsScreen() {
    val app = rememberFyiApp()
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(bottom = 24.dp)) {
        item { SourcesSettings(app.prefs) }
        item { ResolutionSettings(app.prefs) }
        item { ContainerSettings(app.prefs) }
        item { HistorySettings(app.prefs) }
        item { GestureSettings(app.prefs) }
        item { PlaybackSettings(app.prefs) }
    }
}
