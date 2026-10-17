package com.fyiplayer.app.settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.Topic
import com.fyiplayer.app.data.prefs.Prefs
import com.fyiplayer.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Which platforms this app browses and searches at all. Toggling one off here removes it from
 *  Home's tabs and search, but its saved URLs still resolve ([SourceRegistry.all] stays fixed). */
@Composable
fun SourcesSettings(prefs: Prefs) {
    val scope = rememberCoroutineScope()
    val enabled by prefs.enabledSources.collectAsStateWithLifecycle(initialValue = emptySet())
    val topics by prefs.exploreTopics.collectAsStateWithLifecycle(initialValue = emptySet())

    SettingsSection(stringResource(R.string.settings_sources)) {
        SourceRegistry.all.forEach { source ->
            SettingsSwitchRow(
                label = source.displayName,
                checked = source.id in enabled,
                onCheckedChange = { checked ->
                    val next = if (checked) enabled + source.id else enabled - source.id
                    scope.launch { prefs.setEnabledSources(next) }
                },
            )
        }
        SettingsLabel(
            stringResource(R.string.settings_home_topics),
            stringResource(R.string.settings_home_topics_hint),
            Modifier.padding(start = Spacing.pageMargin, end = Spacing.pageMargin, top = Spacing.s8),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.pageMargin, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
        ) {
            Topic.entries.forEach { topic ->
                val on = topic.name in topics
                FilterChip(
                    selected = on,
                    onClick = { scope.launch { prefs.setExploreTopics(if (on) topics - topic.name else topics + topic.name) } },
                    label = { Text(topic.label) },
                )
            }
        }
    }
}
