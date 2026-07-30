package com.fyiplayer.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/** Player gestures (DESIGN.md §6): vertical drag for brightness/volume. Horizontal-drag scrub and
 *  double-tap seek are not gated here -- they carry no privacy/battery cost to opt out of. */
@Composable
fun GestureSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val brightness by prefs.gestureBrightness.collectAsStateWithLifecycle(initialValue = true)
    val volume by prefs.gestureVolume.collectAsStateWithLifecycle(initialValue = true)

    SettingsSection("Gestures") {
        SettingsSwitchRow(
            label = "Vertical drag on the left half changes brightness",
            checked = brightness,
            onCheckedChange = { scope.launch { prefs.setGestureBrightness(it) } },
        )
        SettingsSwitchRow(
            label = "Vertical drag on the right half changes volume",
            checked = volume,
            onCheckedChange = { scope.launch { prefs.setGestureVolume(it) } },
        )
    }
}
