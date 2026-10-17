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

/** One labelled group, shared by every settings section file so they read as one list, not six
 *  differently-styled ones. */
@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
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
