package com.fyiplayer.app.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.data.backup.BackupDocument
import com.fyiplayer.app.data.backup.BackupIo
import com.fyiplayer.app.data.backup.BackupPlan
import com.fyiplayer.app.data.repo.LikesRepository
import com.fyiplayer.app.data.repo.PlaylistRepository
import com.fyiplayer.app.data.repo.SubscriptionRepository
import com.fyiplayer.app.ui.rememberFyiApp
import com.fyiplayer.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Export/import the whole library as one HTML file via SAF (DESIGN.md §7). Both directions show
 * counts before anything is written; import never deletes or overwrites what's already saved.
 */
@Composable
fun BackupSettings() {
    val app = rememberFyiApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val likes = remember { LikesRepository(app.database.likeDao()) }
    val playlists = remember { PlaylistRepository(app.database.playlistDao(), app.database.playlistItemDao()) }
    val subscriptions = remember { SubscriptionRepository(app.database.subscriptionDao()) }

    var pendingDoc by remember { mutableStateOf<BackupDocument?>(null) }
    var pendingPlan by remember { mutableStateOf<BackupPlan?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                BackupIo.export(app, uri, likes, playlists, subscriptions)
                status = context.getString(R.string.settings_backup_exported); isError = false
            } catch (e: Exception) {
                status = e.message ?: context.getString(R.string.settings_backup_export_failed); isError = true
            }
        }
    }
    // */* : a picker reports a saved .html as text/plain, so a narrower filter would hide the
    // user's own backup file from them (DESIGN.md §8).
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val (doc, plan) = BackupIo.preview(app, uri, likes, playlists, subscriptions)
                pendingDoc = doc; pendingPlan = plan
            } catch (e: Exception) {
                status = e.message ?: context.getString(R.string.settings_backup_import_failed); isError = true
            }
        }
    }

    SettingsSection(null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = 4.dp)) {
            TextButton(onClick = { exportLauncher.launch("fyt-player-backup.html") }) { Text(stringResource(R.string.settings_backup_export)) }
            TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.settings_backup_import)) }
        }
        status?.let {
            Text(
                it,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = 4.dp),
            )
        }
    }

    val doc = pendingDoc
    val plan = pendingPlan
    if (doc != null && plan != null) {
        AlertDialog(
            onDismissRequest = { pendingDoc = null; pendingPlan = null },
            title = { Text(stringResource(R.string.settings_backup_import)) },
            text = {
                val summary = if (plan.isEmpty) {
                    stringResource(R.string.settings_backup_nothing_new)
                } else {
                    stringResource(
                        R.string.settings_backup_adds,
                        plan.newPlaylists, plan.newPlaylistItems, plan.newLiked, plan.newChannels,
                    )
                }
                val skipped = if (plan.droppedEntries > 0) {
                    " " + pluralStringResource(R.plurals.settings_backup_skipped, plan.droppedEntries, plan.droppedEntries)
                } else {
                    ""
                }
                Text(summary + skipped)
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val applied = BackupIo.apply(doc, likes, playlists, subscriptions)
                            status = context.getString(
                                R.string.settings_backup_added,
                                applied.newPlaylists, applied.newPlaylistItems, applied.newLiked, applied.newChannels,
                            )
                            isError = false
                        } catch (e: Exception) {
                            status = e.message ?: context.getString(R.string.settings_backup_import_failed); isError = true
                        } finally {
                            pendingDoc = null; pendingPlan = null
                        }
                    }
                }) { Text(stringResource(R.string.settings_backup_import_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingDoc = null; pendingPlan = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
