package com.fyiplayer.app.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.getSystemService
import com.fyiplayer.app.CrashLog
import com.fyiplayer.app.DiagLog
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.ConfirmDialog
import com.fyiplayer.app.ui.theme.Sizes

/** The About page dialog currently open. Null in the page state means none is open. */
internal enum class AboutDialog { REPORTS, CRASH, PLAYBACK, CLEAR_CRASH, CLEAR_PLAYBACK }

/** Routes the Reports picker, the two report viewers and their clear confirmations. Only one dialog
 *  is open at a time, so each step replaces the previous one. */
@Composable
internal fun AboutReportDialogs(
    open: AboutDialog?,
    crashReport: String?,
    playbackReport: String?,
    onOpen: (AboutDialog?) -> Unit,
    onCrashCleared: () -> Unit,
    onPlaybackCleared: () -> Unit,
) {
    val context = LocalContext.current
    val crashTitle = stringResource(R.string.settings_crash_report)
    val playbackTitle = stringResource(R.string.settings_playback_report)
    when (open) {
        null -> Unit
        AboutDialog.REPORTS -> ReportPickerDialog(
            hasCrash = crashReport != null,
            hasPlayback = playbackReport != null,
            onPick = onOpen,
            onDismiss = { onOpen(null) },
        )
        AboutDialog.CRASH -> ReportDialog(
            title = crashTitle,
            text = crashReport.orEmpty(),
            onCopy = {
                copyToClipboard(context, crashTitle, crashReport.orEmpty())
                onOpen(null)
            },
            onClear = { onOpen(AboutDialog.CLEAR_CRASH) },
            onDismiss = { onOpen(null) },
        )
        AboutDialog.PLAYBACK -> ReportDialog(
            title = playbackTitle,
            text = playbackReport.orEmpty(),
            onCopy = {
                copyToClipboard(context, playbackTitle, playbackReport.orEmpty())
                onOpen(null)
            },
            onClear = { onOpen(AboutDialog.CLEAR_PLAYBACK) },
            onDismiss = { onOpen(null) },
        )
        AboutDialog.CLEAR_CRASH -> ConfirmDialog(
            title = stringResource(R.string.about_clear_crash_title),
            body = stringResource(R.string.settings_clear_history_body),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = {
                CrashLog.clear(context)
                onCrashCleared()
                onOpen(null)
            },
            onDismiss = { onOpen(AboutDialog.CRASH) },
        )
        AboutDialog.CLEAR_PLAYBACK -> ConfirmDialog(
            title = stringResource(R.string.about_clear_playback_title),
            body = stringResource(R.string.settings_clear_history_body),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = {
                DiagLog.clear(context)
                onPlaybackCleared()
                onOpen(null)
            },
            onDismiss = { onOpen(AboutDialog.PLAYBACK) },
        )
    }
}

/** Lists only the reports that exist. Each row opens its viewer. */
@Composable
private fun ReportPickerDialog(
    hasCrash: Boolean,
    hasPlayback: Boolean,
    onPick: (AboutDialog) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reports)) },
        text = {
            Column {
                if (hasCrash) {
                    TextButton(onClick = { onPick(AboutDialog.CRASH) }) {
                        Text(stringResource(R.string.settings_crash_report))
                    }
                }
                if (hasPlayback) {
                    TextButton(onClick = { onPick(AboutDialog.PLAYBACK) }) {
                        Text(stringResource(R.string.settings_playback_report))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
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

private fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(label, text))
}
