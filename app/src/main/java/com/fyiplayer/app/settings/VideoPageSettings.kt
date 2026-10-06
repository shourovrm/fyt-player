package com.fyiplayer.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/** What the video page shows beyond what YouTube itself returns. */
@Composable
fun VideoPageSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val showDislikes by prefs.showDislikeCounts.collectAsStateWithLifecycle(initialValue = false)

    SettingsSection("Video page") {
        SettingsSwitchRow(
            label = "Show dislike counts — asks returnyoutubedislike.com about each video you open",
            checked = showDislikes,
            onCheckedChange = { scope.launch { prefs.setShowDislikeCounts(it) } },
        )
    }
}
