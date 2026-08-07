package com.fyiplayer.app.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/** Resolution ceiling picked per connection type -- a metered hotspot must not silently burn data
 *  at the wifi ceiling just because it reports as wifi (see FyiApp.currentMaxHeight). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val wifi by prefs.maxResolutionWifi.collectAsStateWithLifecycle(initialValue = 1080)
    val mobile by prefs.maxResolutionMobile.collectAsStateWithLifecycle(initialValue = 720)

    SettingsSection("Resolution") {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Wi-Fi", style = MaterialTheme.typography.bodyMedium)
            ResolutionRow(selected = wifi, onSelect = { scope.launch { prefs.setMaxResolutionWifi(it) } })
            Text("Mobile data", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            ResolutionRow(selected = mobile, onSelect = { scope.launch { prefs.setMaxResolutionMobile(it) } })
        }
    }
}

private val HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResolutionRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HEIGHTS.forEach { height ->
            FilterChip(selected = height == selected, onClick = { onSelect(height) }, label = { Text("${height}p") })
        }
    }
}
