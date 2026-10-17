package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.data.prefs.Prefs
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Background audio, autoplay and the resolution ceiling (DESIGN.md §6). Wi-Fi and mobile are
 *  separate prefs, see FyiApp.currentMaxHeight for why. */
@Composable
fun PlaybackSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val background by prefs.backgroundPlayback.collectAsStateWithLifecycle(initialValue = true)
    val autoplayNext by prefs.autoplayNext.collectAsStateWithLifecycle(initialValue = false)
    val wifi by prefs.maxResolutionWifi.collectAsStateWithLifecycle(initialValue = 1080)
    val mobile by prefs.maxResolutionMobile.collectAsStateWithLifecycle(initialValue = 720)

    SettingsSection(null) {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_background_playback),
            checked = background,
            onCheckedChange = { scope.launch { prefs.setBackgroundPlayback(it) } },
        )
        SettingsSwitchRow(
            label = stringResource(R.string.settings_autoplay_next),
            checked = autoplayNext,
            onCheckedChange = { scope.launch { prefs.setAutoplayNext(it) } },
        )
    }
    SettingsSection(stringResource(R.string.settings_quality)) {
        ResolutionRow(
            label = stringResource(R.string.settings_quality_wifi),
            selected = wifi,
            onSelect = { scope.launch { prefs.setMaxResolutionWifi(it) } },
        )
        ResolutionRow(
            label = stringResource(R.string.settings_quality_mobile),
            selected = mobile,
            onSelect = { scope.launch { prefs.setMaxResolutionMobile(it) } },
        )
    }
}

private val HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)

/** A value row showing the chosen height; tapping it opens the radio dialog. */
@Composable
private fun ResolutionRow(label: String, selected: Int, onSelect: (Int) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    SettingsValueRow(
        label = label,
        value = stringResource(R.string.settings_resolution_option, selected),
        opens = ValueRowOpens.DIALOG,
        onClick = { showDialog = true },
    )
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Column {
                    HEIGHTS.forEach { height ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Sizes.listOneLine).selectable(
                                selected = height == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(height)
                                    showDialog = false
                                },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The row owns the click; the button only draws the state.
                            RadioButton(selected = height == selected, onClick = null)
                            Text(
                                stringResource(R.string.settings_resolution_option, height),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = Spacing.s16),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
