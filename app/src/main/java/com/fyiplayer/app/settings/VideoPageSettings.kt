package com.fyiplayer.app.settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/** What the video page shows beyond what YouTube itself returns. */
@Composable
fun VideoPageSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val showDislikes by prefs.showDislikeCounts.collectAsStateWithLifecycle(initialValue = false)

    SettingsSection(stringResource(R.string.settings_video_page)) {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_dislike_counts),
            hint = stringResource(R.string.settings_dislike_counts_hint),
            checked = showDislikes,
            onCheckedChange = { scope.launch { prefs.setShowDislikeCounts(it) } },
        )
    }
}
