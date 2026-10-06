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
import com.fyiplayer.app.core.SponsorCategory
import com.fyiplayer.app.core.SponsorMode
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/** The SponsorBlock master switch and one mode per category. Channel exceptions are set from the
 *  video page of the channel itself, not here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsorBlockSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val enabled by prefs.sponsorBlock.collectAsStateWithLifecycle(initialValue = false)
    val modes by prefs.sponsorModes.collectAsStateWithLifecycle(
        initialValue = SponsorCategory.entries.associateWith { it.defaultMode },
    )

    SettingsSection("SponsorBlock") {
        SettingsSwitchRow(
            label = "Use SponsorBlock — sends a 4-character hash prefix of each YouTube video id to sponsor.ajay.app",
            checked = enabled,
            onCheckedChange = { scope.launch { prefs.setSponsorBlock(it) } },
        )
        if (enabled) {
            SponsorCategory.entries.forEach { category ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(category.label, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SponsorMode.entries.forEach { mode ->
                            FilterChip(
                                selected = modes[category] == mode,
                                onClick = { scope.launch { prefs.setSponsorMode(category, mode) } },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
            }
        }
    }
}
