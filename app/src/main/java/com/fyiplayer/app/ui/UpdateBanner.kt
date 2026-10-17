package com.fyiplayer.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.update.UpdateCheck
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.update.downloadAndInstall
import kotlinx.coroutines.launch

/** One-line update card above the app content. Shows only while [UpdateCheck] holds
 *  a newer release the user hasn't dismissed; dismissal is per version, so the next release
 *  banners again. */
@Composable
fun UpdateBanner() {
    val notice = UpdateCheck.available ?: return
    val prefs = rememberFyiApp().prefs
    // Initial = this version: assume dismissed until DataStore answers, so a user who dismissed
    // never sees a one-frame banner flash on every launch.
    val dismissed by prefs.dismissedUpdateVersion.collectAsStateWithLifecycle(initialValue = notice.version)
    if (dismissed == notice.version) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadingMessage = stringResource(R.string.update_downloading)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = 6.dp),
    ) {
        Row(
            Modifier.padding(start = Spacing.s16, end = 4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.update_available, notice.version),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = {
                downloadAndInstall(context, notice.apkUrl, notice.version)
                Toast.makeText(context, downloadingMessage, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.update_install)) }
            IconButton(onClick = { scope.launch { prefs.setDismissedUpdateVersion(notice.version) } }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.update_dismiss))
            }
        }
    }
}
