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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/** Playback behavior plus the resolution ceiling (DESIGN.md §6): background audio, SponsorBlock,
 *  and per-connection-type resolution live together since they all shape what plays and how --
 *  see FyiApp.currentMaxHeight for why wifi/mobile are separate prefs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val sponsorBlock by prefs.sponsorBlock.collectAsStateWithLifecycle(initialValue = false)
    val background by prefs.backgroundPlayback.collectAsStateWithLifecycle(initialValue = true)
    val autoplayNext by prefs.autoplayNext.collectAsStateWithLifecycle(initialValue = false)
    val wifi by prefs.maxResolutionWifi.collectAsStateWithLifecycle(initialValue = 1080)
    val mobile by prefs.maxResolutionMobile.collectAsStateWithLifecycle(initialValue = 720)

    SettingsSection("Playback") {
        SettingsSwitchRow(
            label = "Skip sponsored segments",
            checked = sponsorBlock,
            onCheckedChange = { scope.launch { prefs.setSponsorBlock(it) } },
        )
        SettingsSwitchRow(
            label = "Background playback",
            checked = background,
            onCheckedChange = { scope.launch { prefs.setBackgroundPlayback(it) } },
        )
        SettingsSwitchRow(
            label = "Autoplay next",
            checked = autoplayNext,
            onCheckedChange = { scope.launch { prefs.setAutoplayNext(it) } },
        )
        Text(
            "Default quality",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Wi-Fi", style = MaterialTheme.typography.bodyMedium)
            ResolutionRow(selected = wifi, onSelect = { scope.launch { prefs.setMaxResolutionWifi(it) } })
            Text("Mobile data", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HEIGHTS.forEach { height ->
            FilterChip(selected = height == selected, onClick = { onSelect(height) }, label = { Text("${height}p") })
        }
    }
}
