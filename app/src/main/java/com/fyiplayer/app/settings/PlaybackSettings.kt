package com.fyiplayer.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/** Whether audio keeps playing after the app is backgrounded. The preference itself is the whole
 *  contract this file owns; the player module (DESIGN.md §6) is what reads it. */
@Composable
fun PlaybackSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val background by prefs.backgroundPlayback.collectAsStateWithLifecycle(initialValue = true)

    SettingsSection("Playback") {
        SettingsSwitchRow(
            label = "Keep playing when the app is in the background",
            checked = background,
            onCheckedChange = { scope.launch { prefs.setBackgroundPlayback(it) } },
        )
    }
}
