package com.fyiplayer.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.data.prefs.Prefs
import com.fyiplayer.app.data.repo.HistoryRepository
import com.fyiplayer.app.data.repo.SearchHistoryRepository
import com.fyiplayer.app.ui.ConfirmDialog
import com.fyiplayer.app.ui.rememberFyiApp
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
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

    var confirmingClear by remember { mutableStateOf(false) }

    SettingsSection(stringResource(R.string.settings_history)) {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_save_watch_history),
            checked = recordWatch,
            onCheckedChange = { scope.launch { prefs.setRecordWatchHistory(it) } },
        )
        SettingsSwitchRow(
            label = stringResource(R.string.settings_save_search_history),
            checked = recordSearch,
            onCheckedChange = { scope.launch { prefs.setRecordSearchHistory(it) } },
        )
        SettingsSwitchRow(
            label = stringResource(R.string.settings_remember_position),
            checked = savePosition,
            onCheckedChange = { scope.launch { prefs.setSavePlayPosition(it) } },
        )
        SettingsDestructiveRow(
            label = stringResource(R.string.settings_clear_history),
            hint = stringResource(R.string.settings_clear_history_hint),
            onClick = { confirmingClear = true },
        )
    }

    if (confirmingClear) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_history_title),
            body = stringResource(R.string.settings_clear_history_body),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = {
                confirmingClear = false
                scope.launch { history.clear(); searchHistory.clear() }
            },
            onDismiss = { confirmingClear = false },
        )
    }
}

/** One settings row: label (bodyMedium), optional hint (bodySmall) beneath, switch trailing. */
@Composable
internal fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, hint: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.listOneLine).padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLabel(label, hint, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A row whose whole body is the action and whose label is in the error colour; callers confirm first. */
@Composable
internal fun SettingsDestructiveRow(label: String, hint: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = Sizes.listOneLine)
            .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLabel(label, hint, Modifier.weight(1f), labelColor = MaterialTheme.colorScheme.error)
    }
}

/** The label-over-hint block every settings row uses. */
@Composable
internal fun SettingsLabel(
    label: String,
    hint: String?,
    modifier: Modifier = Modifier,
    labelColor: Color = Color.Unspecified,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
