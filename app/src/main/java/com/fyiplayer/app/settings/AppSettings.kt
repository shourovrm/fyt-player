package com.fyiplayer.app.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.BuildConfig
import com.fyiplayer.app.update.UpdateCheck
import com.fyiplayer.app.update.UpdateInfo
import com.fyiplayer.app.update.downloadAndInstall
import kotlinx.coroutines.launch

/** Manual check-for-updates outcome, shown inline under the version row. */
private sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data object UpToDate : CheckState
    data object Failed : CheckState
    data class Found(val info: UpdateInfo) : CheckState
}

/** App identity + updates: the installed version (so a bug report always names it) and a manual
 *  check against GitHub releases, result shown inline. */
@Composable
fun AppSettings() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var check by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    SettingsSection("App") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "FYT Player ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                enabled = check != CheckState.Checking,
                onClick = {
                    check = CheckState.Checking
                    scope.launch {
                        check = runCatching { UpdateCheck.fetchNewer() }.fold(
                            onSuccess = { it?.let(CheckState::Found) ?: CheckState.UpToDate },
                            onFailure = { CheckState.Failed },
                        )
                    }
                },
            ) { Text("Check for updates") }
        }
        when (val c = check) {
            CheckState.Idle -> {}
            CheckState.Checking -> InlineStatus("Checking for updates…")
            CheckState.UpToDate -> InlineStatus("You're on the latest version")
            CheckState.Failed -> InlineStatus("Couldn't check — are you online?")
            is CheckState.Found -> Row(
                Modifier.padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Update available — v${c.info.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = {
                    downloadAndInstall(context, c.info.apkUrl, c.info.version)
                    Toast.makeText(context, "Downloading update…", Toast.LENGTH_SHORT).show()
                }) { Text("Get") }
            }
        }
    }
}

@Composable
private fun InlineStatus(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
