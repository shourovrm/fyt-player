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
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.data.backup.BackupDocument
import com.fyiplayer.app.data.backup.BackupIo
import com.fyiplayer.app.data.backup.BackupPlan
import com.fyiplayer.app.data.repo.HistoryRepository
import com.fyiplayer.app.data.repo.LikesRepository
import com.fyiplayer.app.data.repo.PlaylistRepository
import com.fyiplayer.app.ui.rememberFyiApp
import kotlinx.coroutines.launch

/**
 * Export/import the whole library as one HTML file via SAF (DESIGN.md §7). Both directions show
 * counts before anything is written; import never deletes or overwrites what's already saved.
 */
@Composable
fun BackupSettings() {
    val app = rememberFyiApp()
    val scope = rememberCoroutineScope()
    val likes = remember { LikesRepository(app.database.likeDao()) }
    val playlists = remember { PlaylistRepository(app.database.playlistDao(), app.database.playlistItemDao()) }
    val history = remember { HistoryRepository(app.database.watchHistoryDao()) }

    var pendingDoc by remember { mutableStateOf<BackupDocument?>(null) }
    var pendingPlan by remember { mutableStateOf<BackupPlan?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                BackupIo.export(app, uri, likes, playlists, history)
                status = "Library exported."; isError = false
            } catch (e: Exception) {
                status = e.message ?: "Export failed."; isError = true
            }
        }
    }
    // */* : a picker reports a saved .html as text/plain, so a narrower filter would hide the
    // user's own backup file from them (DESIGN.md §8).
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val (doc, plan) = BackupIo.preview(app, uri, likes, playlists, history)
                pendingDoc = doc; pendingPlan = plan
            } catch (e: Exception) {
                status = e.message ?: "Import failed."; isError = true
            }
        }
    }

    SettingsSection("Backup") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            TextButton(onClick = { exportLauncher.launch("fyi-player-backup.html") }) { Text("Export library") }
            TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import library") }
        }
        status?.let {
            Text(
                it,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

    val doc = pendingDoc
    val plan = pendingPlan
    if (doc != null && plan != null) {
        AlertDialog(
            onDismissRequest = { pendingDoc = null; pendingPlan = null },
            title = { Text("Import library") },
            text = {
                Text(
                    if (plan.isEmpty) {
                        "Nothing new to add -- everything in this file is already in your library."
                    } else {
                        "Adds ${plan.newPlaylists} playlists, ${plan.newPlaylistItems} playlist videos, " +
                            "${plan.newLiked} liked videos and ${plan.newHistory} history entries. " +
                            "Nothing already saved is changed or removed."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val applied = BackupIo.apply(doc, likes, playlists, history)
                            status = "Added ${applied.newPlaylists} playlists, ${applied.newPlaylistItems} playlist " +
                                "videos, ${applied.newLiked} liked videos, ${applied.newHistory} history entries."
                            isError = false
                        } catch (e: Exception) {
                            status = e.message ?: "Import failed."; isError = true
                        } finally {
                            pendingDoc = null; pendingPlan = null
                        }
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingDoc = null; pendingPlan = null }) { Text("Cancel") } },
        )
    }
}
