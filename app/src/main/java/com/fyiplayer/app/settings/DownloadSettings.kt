package com.fyiplayer.app.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

private val CONTAINERS = listOf("mp4", "webm", "mkv")

/**
 * Where finished downloads get COPIED to, in addition to the app-private dir they're always
 * produced in (DESIGN.md; DownloadQueue never changes what it writes internally). Unset means
 * no extra copy is made -- the in-app Downloads screen always opens the private file either way.
 * Also owns the preferred container: playback/download both filter to this first and fall back
 * to whatever the source actually has (Contracts.kt: nothing is invented).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettings(prefs: Prefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val treeUri by prefs.downloadTreeUri.collectAsStateWithLifecycle(initialValue = null)
    val container by prefs.preferredContainer.collectAsStateWithLifecycle(initialValue = "mp4")

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Grant must be taken up front or it dies with this process; DownloadQueue copies happen
        // long after this composable is gone.
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        scope.launch { prefs.setDownloadTreeUri(uri.toString()) }
    }

    SettingsSection("Downloads") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Folder", style = MaterialTheme.typography.bodyLarge)
                Text(
                    prettyTreeUri(treeUri),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (treeUri != null) {
                TextButton(onClick = { scope.launch { prefs.setDownloadTreeUri(null) } }) { Text("Reset") }
            }
            TextButton(onClick = { pickLauncher.launch(null) }) { Text("Change") }
        }

        Text(
            "File format",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 0.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CONTAINERS.forEach { c ->
                FilterChip(
                    selected = c == container,
                    onClick = { scope.launch { prefs.setPreferredContainer(c) } },
                    label = { Text(c) },
                )
            }
        }
    }
}

private fun prettyTreeUri(uri: String?): String {
    if (uri == null) return "App storage (default)"
    return Uri.decode(Uri.parse(uri).lastPathSegment ?: uri)
}
