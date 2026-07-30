package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/** Which platforms this app browses and searches at all. Toggling one off here removes it from
 *  Home's tabs and search, but its saved URLs still resolve ([SourceRegistry.all] stays fixed). */
@Composable
fun SourcesSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val enabled by prefs.enabledSources.collectAsStateWithLifecycle(initialValue = emptySet())

    SettingsSection("Sources") {
        SourceRegistry.all.forEach { source ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(source.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = source.id in enabled,
                    onCheckedChange = { checked ->
                        val next = if (checked) enabled + source.id else enabled - source.id
                        scope.launch { prefs.setEnabledSources(next) }
                    },
                )
            }
        }
    }
}
