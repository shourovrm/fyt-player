package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.data.prefs.Prefs
import com.fyiplayer.app.player.CaptionSize
import com.fyiplayer.app.player.captionSizeLabel
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.ui.theme.scaledBy
import kotlinx.coroutines.launch

/** Caption text size for videos and for Shorts. Writes the same prefs the caption sheet writes,
 *  so a change here shows up in the sheet's chips and the other way round. */
@Composable
fun CaptionSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val videoSize by prefs.captionSizeVideo.collectAsStateWithLifecycle(initialValue = CaptionSize.DEFAULT)
    val shortsSize by prefs.captionSizeShorts.collectAsStateWithLifecycle(initialValue = CaptionSize.DEFAULT)

    SettingsSection(stringResource(R.string.settings_captions)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin)) {
            Text(stringResource(R.string.settings_caption_size), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.settings_caption_size_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CaptionSizeRow(
            label = stringResource(R.string.settings_caption_size_videos),
            selected = videoSize,
            onSelect = { size -> scope.launch { prefs.setCaptionSizeVideo(size) } },
        )
        CaptionSizeRow(
            label = stringResource(R.string.settings_caption_size_shorts),
            selected = shortsSize,
            onSelect = { size -> scope.launch { prefs.setCaptionSizeShorts(size) } },
        )
        CaptionSample(videoSize)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptionSizeRow(label: String, selected: CaptionSize, onSelect: (CaptionSize) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Spacing.s8))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CaptionSize.entries.forEachIndexed { index, size ->
                SegmentedButton(
                    selected = size == selected,
                    onClick = { onSelect(size) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = CaptionSize.entries.size),
                    label = { Text(captionSizeLabel(size)) },
                )
            }
        }
    }
}

/** Shows the videos size on a line of body text, so the change is visible before leaving the page. */
@Composable
private fun CaptionSample(size: CaptionSize) {
    val bodyLarge = MaterialTheme.typography.bodyLarge
    Text(
        stringResource(R.string.settings_caption_sample),
        style = bodyLarge.scaledBy(size.scale),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
    )
}
