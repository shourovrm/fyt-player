package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.engine.EngineChannel
import java.text.DateFormat
import java.util.Date

// Every channel is selectable, Master included. Master is the unreleased development build.
private val SELECTABLE_CHANNELS = EngineChannel.entries

/** Site support values: the installed engine version, the update channel, and the last check time.
 *  Nothing here runs on its own; the channel only changes which build a tap of Update installs. */
@Composable
internal fun AboutSiteSupport(
    installedVersion: String?,
    channel: EngineChannel,
    onChannelSelected: (EngineChannel) -> Unit,
    lastChecked: Long?,
) {
    var channelMenuOpen by remember { mutableStateOf(false) }
    SettingsSection(stringResource(R.string.settings_site_support)) {
        SettingsReadOnlyRow(
            label = stringResource(R.string.settings_version),
            value = installedVersion ?: stringResource(R.string.settings_site_support_bundled),
            leadingIcon = Icons.Filled.Build,
        )
        Box {
            SettingsValueRow(
                label = stringResource(R.string.settings_update_channel),
                value = channel.label,
                opens = ValueRowOpens.MENU,
                onClick = { channelMenuOpen = true },
                leadingIcon = Icons.Filled.ArrowDropDown,
            )
            DropdownMenu(expanded = channelMenuOpen, onDismissRequest = { channelMenuOpen = false }) {
                SELECTABLE_CHANNELS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onChannelSelected(option)
                            channelMenuOpen = false
                        },
                    )
                }
            }
        }
        lastChecked?.let {
            SettingsReadOnlyRow(
                label = stringResource(R.string.about_last_checked),
                value = DateFormat.getDateTimeInstance().format(Date(it)),
                leadingIcon = Icons.Filled.DateRange,
            )
        }
    }
}
