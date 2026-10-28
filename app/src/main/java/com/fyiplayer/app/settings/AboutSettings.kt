package com.fyiplayer.app.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.BuildConfig
import com.fyiplayer.app.CrashLog
import com.fyiplayer.app.DiagLog
import com.fyiplayer.app.R
import com.fyiplayer.app.engine.EngineChannel
import com.fyiplayer.app.engine.EngineUpdater
import com.fyiplayer.app.engine.UpdateResult
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.update.downloadAndInstall
import kotlinx.coroutines.launch

/** The About page: app version and update check, the action strip, site support values and reports.
 *  Nothing runs on open; every check and update starts from a tap. */
@Composable
fun AboutSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appCheck by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var siteSupportVersion by remember { mutableStateOf<String?>(null) }
    var channel by remember { mutableStateOf(EngineChannel.STABLE) }
    var siteSupportRunning by remember { mutableStateOf(false) }
    var siteSupportResult by remember { mutableStateOf<String?>(null) }
    var crashReport by remember { mutableStateOf<String?>(null) }
    var playbackReport by remember { mutableStateOf<String?>(null) }
    var openDialog by remember { mutableStateOf<AboutDialog?>(null) }
    val downloadingMessage = stringResource(R.string.settings_update_downloading)
    val updatedMessage = stringResource(R.string.settings_site_support_updated)
    val alreadyCurrentMessage = stringResource(R.string.settings_site_support_current)
    // remember: lastChecked(context) builds a fresh Flow each call -- without this key,
    // collectAsStateWithLifecycle would see a new upstream and restart collection every recomposition.
    val lastCheckedFlow = remember(context) { EngineUpdater.lastChecked(context) }
    val lastChecked by lastCheckedFlow.collectAsStateWithLifecycle(initialValue = null)

    // Read once up front so a freshly-installed app shows "bundled", not "unknown".
    LaunchedEffect(Unit) {
        siteSupportVersion = EngineUpdater.installedVersion(context)
        crashReport = CrashLog.read(context)
        playbackReport = DiagLog.read(context)
    }

    val startAppCheck: () -> Unit = {
        appCheck = CheckState.Checking
        scope.launch { appCheck = checkForAppUpdate() }
    }
    val startSiteSupportUpdate: () -> Unit = {
        siteSupportRunning = true
        siteSupportResult = null
        scope.launch {
            when (val outcome = EngineUpdater.update(context, channel)) {
                is UpdateResult.Updated -> {
                    siteSupportVersion = outcome.version ?: siteSupportVersion
                    siteSupportResult = updatedMessage
                }
                is UpdateResult.AlreadyCurrent -> siteSupportResult = alreadyCurrentMessage
                is UpdateResult.Failed -> siteSupportResult = outcome.reason
            }
            siteSupportRunning = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        AboutHero(
            versionName = BuildConfig.VERSION_NAME,
            check = appCheck,
            onRetryCheck = startAppCheck,
            onInstall = { found ->
                downloadAndInstall(context, found.apkUrl, found.version)
                Toast.makeText(context, downloadingMessage, Toast.LENGTH_SHORT).show()
            },
        )
        AboutActionStrip(
            appCheckRunning = appCheck == CheckState.Checking,
            onCheckApp = startAppCheck,
            siteSupportRunning = siteSupportRunning,
            onUpdateSiteSupport = startSiteSupportUpdate,
            reportsAvailable = crashReport != null || playbackReport != null,
            onReports = { openDialog = AboutDialog.REPORTS },
        )
        siteSupportResult?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = 4.dp),
            )
        }
        AboutSiteSupport(
            installedVersion = siteSupportVersion,
            channel = channel,
            onChannelSelected = { channel = it },
            lastChecked = lastChecked,
        )
        AboutReportDialogs(
            open = openDialog,
            crashReport = crashReport,
            playbackReport = playbackReport,
            onOpen = { openDialog = it },
            onCrashCleared = { crashReport = null },
            onPlaybackCleared = { playbackReport = null },
        )
    }
}
