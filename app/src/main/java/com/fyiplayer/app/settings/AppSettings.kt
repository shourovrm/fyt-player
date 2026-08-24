package com.fyiplayer.app.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.BuildConfig

/** App identity: just the version, so a bug report always names what's installed. */
@Composable
fun AppSettings() {
    SettingsSection("App") {
        Text(
            "FYT Player ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
