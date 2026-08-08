package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.data.prefs.Prefs
import com.fyiplayer.app.data.repo.HistoryRepository
import com.fyiplayer.app.data.repo.SearchHistoryRepository
import com.fyiplayer.app.ui.rememberFyiApp
import kotlinx.coroutines.launch

/** Watch/search history toggles gate what gets *recorded* (Detail records watches, Home records
 *  searches); Clear only removes what's already stored -- it does not flip either toggle off. */
@Composable
fun HistorySettings(prefs: Prefs) {
    val app = rememberFyiApp()
    val scope = rememberCoroutineScope()
    val history = remember { HistoryRepository(app.database.watchHistoryDao()) }
    val searchHistory = remember { SearchHistoryRepository(app.database.searchHistoryDao()) }
    val recordWatch by prefs.recordWatchHistory.collectAsStateWithLifecycle(initialValue = true)
    val recordSearch by prefs.recordSearchHistory.collectAsStateWithLifecycle(initialValue = true)
    val savePosition by prefs.savePlayPosition.collectAsStateWithLifecycle(initialValue = true)

    SettingsSection("History") {
        SettingsSwitchRow(
            label = "Save watch history",
            checked = recordWatch,
            onCheckedChange = { scope.launch { prefs.setRecordWatchHistory(it) } },
        )
        SettingsSwitchRow(
            label = "Save search history",
            checked = recordSearch,
            onCheckedChange = { scope.launch { prefs.setRecordSearchHistory(it) } },
        )
        SettingsSwitchRow(
            label = "Remember playback position",
            checked = savePosition,
            onCheckedChange = { scope.launch { prefs.setSavePlayPosition(it) } },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            TextButton(onClick = { scope.launch { history.clear(); searchHistory.clear() } }) {
                Text("Clear history")
            }
        }
    }
}

@Composable
internal fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
