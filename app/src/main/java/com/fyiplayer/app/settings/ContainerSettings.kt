package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

private val CONTAINERS = listOf("mp4", "webm", "mkv")

/** Preferred file container when a source offers a choice. Playback/download both filter to this
 *  first and fall back to whatever the source actually has (Contracts.kt: nothing is invented). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val current by prefs.preferredContainer.collectAsStateWithLifecycle(initialValue = "mp4")

    SettingsSection("Preferred container") {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CONTAINERS.forEach { container ->
                FilterChip(
                    selected = container == current,
                    onClick = { scope.launch { prefs.setPreferredContainer(container) } },
                    label = { Text(container) },
                )
            }
        }
    }
}
