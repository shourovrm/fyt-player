package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.ui.theme.Spacing

/** One group of rows on a settings page. A null [title] draws no header: a page that holds a
 *  single concern already names it in the app bar, so a header would only repeat it. */
@Composable
internal fun SettingsSection(title: String?, content: @Composable () -> Unit) {
    if (title == null) {
        Column(Modifier.fillMaxWidth()) { content() }
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s16)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = 6.dp),
        )
        content()
    }
}
