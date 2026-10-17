package com.fyiplayer.app.settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.Topic
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.launch

/** Which platforms this app browses and searches at all, and which topics Home shows. Toggling a
 *  source off removes it from Home's tabs and search, but its saved URLs still resolve
 *  ([SourceRegistry.all] stays fixed). */
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
    }
    // Switches, not chips: the design system keeps chips for content filters, never for settings.
    SettingsSection(stringResource(R.string.settings_home_topics)) {
        Topic.entries.forEach { topic ->
            val on = topic.name in topics
            SettingsSwitchRow(
                label = topic.label,
                checked = on,
                onCheckedChange = { scope.launch { prefs.setExploreTopics(if (on) topics - topic.name else topics + topic.name) } },
            )
        }
    }
}
