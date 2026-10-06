package com.fyiplayer.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.core.canonicalChannelKey
import kotlinx.coroutines.launch

/** Per-channel SponsorBlock exception, on the video page where the channel is in view. Hidden
 *  while SponsorBlock is off, since there would be nothing to except the channel from. */
@Composable
internal fun SponsorChannelToggle(channelUrl: String?) {
    val prefs = rememberFyiApp().prefs
    val scope = rememberCoroutineScope()
    val enabled by prefs.sponsorBlock.collectAsStateWithLifecycle(initialValue = false)
    val whitelist by prefs.sponsorWhitelist.collectAsStateWithLifecycle(initialValue = emptySet())
    val key = channelUrl?.let(::canonicalChannelKey)
    if (channelUrl == null || key == null || !enabled) return

    val whitelisted = key in whitelist
    // Two words on screen; the full sentence goes to the screen reader.
    val spokenAction = if (whitelisted) "Skip segments on this channel again" else "Stop skipping segments on this channel"
    TextButton(
        onClick = { scope.launch { prefs.setSponsorChannelWhitelisted(channelUrl, !whitelisted) } },
        modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = spokenAction },
    ) {
        Text(
            if (whitelisted) "Skips off" else "Skips on",
            color = if (whitelisted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}
