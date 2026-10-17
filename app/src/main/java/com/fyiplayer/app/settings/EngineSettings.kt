package com.fyiplayer.app.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.CrashLog
import com.fyiplayer.app.DiagLog
import com.fyiplayer.app.R
import com.fyiplayer.app.engine.EngineChannel
import com.fyiplayer.app.engine.EngineUpdater
import com.fyiplayer.app.engine.UpdateResult
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** Site support: version, update channel and the explicit "Update site support" action. Sites
 *  change their pages within weeks (CLAUDE.md), so this is the user's only recourse -- nothing
 *  here runs unless tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf<String?>(null) }
    var channel by remember { mutableStateOf(EngineChannel.STABLE) }
    var updating by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var crash by remember { mutableStateOf<String?>(null) }
    var showCrash by remember { mutableStateOf(false) }
    var playbackLog by remember { mutableStateOf<String?>(null) }
    var showPlaybackLog by remember { mutableStateOf(false) }
    val updatedMessage = stringResource(R.string.settings_site_support_updated)
    val alreadyCurrentMessage = stringResource(R.string.settings_site_support_current)
    // remember: lastChecked(context) builds a fresh Flow each call -- without this key,
    // collectAsStateWithLifecycle would see a new upstream and restart collection every recomposition.
    val lastCheckedFlow = remember(context) { EngineUpdater.lastChecked(context) }
    val lastChecked by lastCheckedFlow.collectAsStateWithLifecycle(initialValue = null)

    // version(context) only returns non-null after a successful update ever ran (verified API
    // shape) -- read once up front so a freshly-installed app shows "bundled", not "unknown".
    LaunchedEffect(Unit) {
        version = EngineUpdater.installedVersion(context)
        crash = CrashLog.read(context)
        playbackLog = DiagLog.read(context)
    }

    SettingsSection(stringResource(R.string.settings_site_support)) {
        SettingsInfoRow(
            label = stringResource(R.string.settings_version),
            hint = version ?: stringResource(R.string.settings_site_support_bundled),
        )
        Column(Modifier.padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8)) {
            Text(stringResource(R.string.settings_update_channel), style = MaterialTheme.typography.bodyMedium)
            Row(
                Modifier.padding(top = Spacing.s8),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
            ) {
                EngineChannel.entries.forEach { c ->
                    FilterChip(selected = c == channel, onClick = { channel = c }, label = { Text(c.label) })
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin),
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
                                result = updatedMessage
                            }
                            is UpdateResult.AlreadyCurrent -> result = alreadyCurrentMessage
                            is UpdateResult.Failed -> result = r.reason
                        }
                        updating = false
                    }
                },
            ) { Text(stringResource(R.string.settings_site_support_update)) }
            if (updating) {
                CircularProgressIndicator(modifier = Modifier.padding(start = Spacing.s8).size(Sizes.iconSmall))
            }
        }
        result?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = 4.dp),
            )
        }
        lastChecked?.let {
            Text(
                stringResource(R.string.settings_site_support_last_checked, DateFormat.getDateTimeInstance().format(Date(it))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = 4.dp),
            )
        }
        if (crash != null) {
            SettingsInfoRow(
                label = stringResource(R.string.settings_crash_report),
                hint = null,
                actionLabel = stringResource(R.string.settings_view),
                onAction = { showCrash = true },
            )
        }
        if (playbackLog != null) {
            SettingsInfoRow(
                label = stringResource(R.string.settings_playback_report),
                hint = stringResource(R.string.settings_playback_report_hint),
                actionLabel = stringResource(R.string.settings_view),
                onAction = { showPlaybackLog = true },
            )
        }
    }

    if (showPlaybackLog) {
        val text = playbackLog.orEmpty()
        val clipLabel = stringResource(R.string.settings_playback_report)
        ReportDialog(
            title = clipLabel,
            text = text,
            onCopy = {
                context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
                showPlaybackLog = false
            },
            onClear = {
                DiagLog.clear(context)
                playbackLog = null
                showPlaybackLog = false
            },
            onDismiss = { showPlaybackLog = false },
        )
    }

    if (showCrash) {
        val text = crash.orEmpty()
        val clipLabel = stringResource(R.string.settings_crash_report)
        ReportDialog(
            title = clipLabel,
            text = text,
            onCopy = {
                context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
                showCrash = false
            },
            onClear = {
                CrashLog.clear(context)
                crash = null
                showCrash = false
            },
            onDismiss = { showCrash = false },
        )
    }
}

/** One settings row with an optional hint and an optional trailing text action. */
@Composable
private fun SettingsInfoRow(label: String, hint: String?, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.listOneLine).padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLabel(label, hint, Modifier.weight(1f))
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** A scrollable plain-text report with Copy and Clear. */
@Composable
private fun ReportDialog(title: String, text: String, onCopy: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                // No height token fits a text pane; the sheet width token is the closest 320 dp.
                modifier = Modifier.heightIn(max = Sizes.sheetMaxWidth).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onCopy) { Text(stringResource(R.string.settings_copy)) } },
        dismissButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) } },
    )
}
