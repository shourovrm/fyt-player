package com.fyiplayer.app.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/**
 * Where finished downloads get COPIED to, in addition to the app-private dir they're always
 * produced in (DESIGN.md; DownloadQueue never changes what it writes internally). Unset means
 * no extra copy is made -- the in-app Downloads screen always opens the private file either way.
 */
@Composable
fun DownloadSettings(prefs: Prefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val treeUri by prefs.downloadTreeUri.collectAsStateWithLifecycle(initialValue = null)

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
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(prettyTreeUri(treeUri), style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            TextButton(onClick = { pickLauncher.launch(null) }) { Text("Choose folder") }
            if (treeUri != null) {
                TextButton(onClick = { scope.launch { prefs.setDownloadTreeUri(null) } }) { Text("Reset") }
            }
        }
    }
}

private fun prettyTreeUri(uri: String?): String {
    if (uri == null) return "App storage (default)"
    return Uri.decode(Uri.parse(uri).lastPathSegment ?: uri)
}
