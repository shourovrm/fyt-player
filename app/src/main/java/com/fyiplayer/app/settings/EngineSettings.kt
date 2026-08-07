package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.engine.EngineChannel
import com.fyiplayer.app.engine.EngineUpdater
import com.fyiplayer.app.engine.UpdateResult
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** Extraction-engine version, update channel and the explicit "Update engine" action. Extractors
 *  rot within weeks (CLAUDE.md), so this is the user's only recourse -- nothing here runs unless
 *  tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf<String?>(null) }
    var channel by remember { mutableStateOf(EngineChannel.STABLE) }
    var updating by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    // remember: lastChecked(context) builds a fresh Flow each call -- without this key,
    // collectAsStateWithLifecycle would see a new upstream and restart collection every recomposition.
    val lastCheckedFlow = remember(context) { EngineUpdater.lastChecked(context) }
    val lastChecked by lastCheckedFlow.collectAsStateWithLifecycle(initialValue = null)

    // version(context) only returns non-null after a successful update ever ran (verified API
    // shape) -- read once up front so a freshly-installed app shows "bundled", not "unknown".
    LaunchedEffect(Unit) { version = EngineUpdater.installedVersion(context) }

    SettingsSection("Video engine") {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "Version: ${version ?: "bundled with the app"}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Update channel",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineChannel.entries.forEach { c ->
                    FilterChip(selected = c == channel, onClick = { channel = c }, label = { Text(c.label) })
                }
            }
            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !updating,
                    onClick = {
                        updating = true
                        result = null
                        scope.launch {
                            when (val r = EngineUpdater.update(context, channel)) {
                                is UpdateResult.Updated -> {
                                    version = r.version ?: version
                                    result = "Updated."
                                }
                                is UpdateResult.AlreadyCurrent -> result = "Already up to date."
                                is UpdateResult.Failed -> result = r.reason
                            }
                            updating = false
                        }
                    },
                ) { Text("Update now") }
                if (updating) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(16.dp))
                }
            }
            result?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            lastChecked?.let {
                Text(
                    "Last checked: ${DateFormat.getDateTimeInstance().format(Date(it))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
