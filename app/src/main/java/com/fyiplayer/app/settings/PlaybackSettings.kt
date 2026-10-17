package com.fyiplayer.app.settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.data.prefs.Prefs
import com.fyiplayer.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Playback behavior plus the resolution ceiling (DESIGN.md §6): background audio
 *  and per-connection-type resolution live together since they all shape what plays and how --
 *  see FyiApp.currentMaxHeight for why wifi/mobile are separate prefs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val background by prefs.backgroundPlayback.collectAsStateWithLifecycle(initialValue = true)
    val autoplayNext by prefs.autoplayNext.collectAsStateWithLifecycle(initialValue = false)
    val wifi by prefs.maxResolutionWifi.collectAsStateWithLifecycle(initialValue = 1080)
    val mobile by prefs.maxResolutionMobile.collectAsStateWithLifecycle(initialValue = 720)

    SettingsSection(stringResource(R.string.settings_playback)) {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_background_playback),
            hint = stringResource(R.string.settings_background_playback_hint),
            checked = background,
            onCheckedChange = { scope.launch { prefs.setBackgroundPlayback(it) } },
        )
        SettingsSwitchRow(
            label = stringResource(R.string.settings_autoplay_next),
            checked = autoplayNext,
            onCheckedChange = { scope.launch { prefs.setAutoplayNext(it) } },
        )
        Text(
            stringResource(R.string.settings_default_quality),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Spacing.pageMargin, end = Spacing.pageMargin, top = 10.dp, bottom = 4.dp),
        )
        Column(Modifier.padding(horizontal = Spacing.pageMargin)) {
            Text(stringResource(R.string.settings_quality_wifi), style = MaterialTheme.typography.bodySmall)
            ResolutionRow(selected = wifi, onSelect = { scope.launch { prefs.setMaxResolutionWifi(it) } })
            Text(stringResource(R.string.settings_quality_mobile), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            ResolutionRow(selected = mobile, onSelect = { scope.launch { prefs.setMaxResolutionMobile(it) } })
        }
    }
}

private val HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResolutionRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
    ) {
        HEIGHTS.forEach { height ->
            FilterChip(selected = height == selected, onClick = { onSelect(height) }, label = { Text(stringResource(R.string.settings_resolution_option, height)) })
        }
    }
}
