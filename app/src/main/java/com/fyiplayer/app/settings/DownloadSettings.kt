package com.fyiplayer.app.settings
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.data.prefs.Prefs
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
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

    SettingsSection(stringResource(R.string.settings_downloads)) {
        val appStorageLabel = stringResource(R.string.settings_download_folder_default)
        Row(
            Modifier.fillMaxWidth().heightIn(min = Sizes.listOneLine).padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsLabel(stringResource(R.string.settings_download_folder), prettyTreeUri(treeUri, appStorageLabel), Modifier.weight(1f))
            if (treeUri != null) {
                TextButton(onClick = { scope.launch { prefs.setDownloadTreeUri(null) } }) { Text(stringResource(R.string.settings_reset)) }
            }
            TextButton(onClick = { pickLauncher.launch(null) }) { Text(stringResource(R.string.settings_change)) }
        }
    }
}

private fun prettyTreeUri(uri: String?, defaultLabel: String): String {
    if (uri == null) return defaultLabel
    return Uri.decode(Uri.parse(uri).lastPathSegment ?: uri)
}
